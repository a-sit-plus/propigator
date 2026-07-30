// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalMultiFormatApi::class)

package at.asitplus.propigator.multi

import at.asitplus.propigator.common.NativeBacked
import at.asitplus.propigator.common.ObjectBackedObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

@ExperimentalMultiFormatApi
data class DecodedObjectBacking(
    val backingObject: Map<*, *>,
    val serialFormat: SerialFormat,
)

@ExperimentalMultiFormatApi
interface ObjectFormatAdapter {
    val descriptor: SerialDescriptor

    fun supports(serialFormat: SerialFormat): Boolean
    fun supports(decoder: Decoder): Boolean
    fun supports(encoder: Encoder): Boolean
    fun defaultKey(propertyName: String): Any
    fun isFormatNull(element: Any?): Boolean
    fun decodeObject(decoder: Decoder): DecodedObjectBacking
    fun encodeObject(
        encoder: Encoder,
        backingObject: Map<Any, Any>,
        serialFormat: SerialFormat,
        metadata: Any?,
    )

    fun backingMetadata(backingObject: Map<*, *>): Any? = null

    fun <T> decodeValue(
        backing: DecodedObjectBacking,
        serializer: KSerializer<T>,
    ): T = error("$this does not support serializable carriers")

    fun <T> encodeValue(
        serialFormat: SerialFormat,
        value: T,
        serializer: KSerializer<T>,
    ): DecodedObjectBacking = error("$this does not support serializable carriers")

    fun <V> decodeElement(
        serialFormat: SerialFormat,
        element: Any,
        serializer: KSerializer<V>,
    ): V

    fun <V> encodeElement(
        serialFormat: SerialFormat,
        value: V,
        serializer: KSerializer<V>,
    ): Any
}

@ExperimentalMultiFormatApi
class ObjectFormatSet internal constructor(
    adapters: List<ObjectFormatAdapter>,
) {
    init {
        require(adapters.isNotEmpty()) { "At least one object format is required" }
    }

    val adapters: List<ObjectFormatAdapter> = adapters.distinct()

    operator fun plus(other: ObjectFormatSet): ObjectFormatSet =
        ObjectFormatSet(adapters + other.adapters)

    internal fun adapterFor(serialFormat: SerialFormat): ObjectFormatAdapter =
        adapters.singleOrNull { it.supports(serialFormat) }
            ?: error("Expected exactly one adapter for $serialFormat")

    internal fun adapterFor(decoder: Decoder): ObjectFormatAdapter =
        adapters.singleOrNull { it.supports(decoder) }
            ?: error("Expected exactly one adapter for ${decoder::class}")

    internal fun adapterFor(encoder: Encoder): ObjectFormatAdapter =
        adapters.singleOrNull { it.supports(encoder) }
            ?: error("Expected exactly one adapter for ${encoder::class}")

}

@ExperimentalMultiFormatApi
fun objectFormats(vararg adapters: ObjectFormatAdapter): ObjectFormatSet =
    ObjectFormatSet(adapters.toList())

@ExperimentalMultiFormatApi
interface ObjectFormatPropertyBinding<out V> {
    val format: ObjectFormatAdapter
    val key: Any
}

@ExperimentalMultiFormatApi
data class ObjectFormatPropertyKey(
    override val format: ObjectFormatAdapter,
    override val key: Any,
) : ObjectFormatPropertyBinding<Nothing>

@ExperimentalMultiFormatApi
data class ObjectFormatProperty<V>(
    override val format: ObjectFormatAdapter,
    override val key: Any,
    val serializer: KSerializer<V>,
) : ObjectFormatPropertyBinding<V>

@ExperimentalMultiFormatApi
infix fun ObjectFormatAdapter.propertyKey(key: Any): ObjectFormatPropertyKey =
    ObjectFormatPropertyKey(this, key)

@ExperimentalMultiFormatApi
fun <V> ObjectFormatAdapter.property(
    key: Any,
    serializer: KSerializer<V>,
): ObjectFormatProperty<V> =
    ObjectFormatProperty(this, key, serializer)

@ExperimentalMultiFormatApi
data class MultiFormatKey(
    val propertyName: String,
    val aliases: Map<ObjectFormatAdapter, Any>,
    val serializers: Map<ObjectFormatAdapter, KSerializer<*>> = emptyMap(),
) {
    fun keyFor(adapter: ObjectFormatAdapter): Any =
        aliases[adapter] ?: adapter.defaultKey(propertyName)

    @Suppress("UNCHECKED_CAST")
    fun <V> serializerFor(
        adapter: ObjectFormatAdapter,
        default: KSerializer<V>,
    ): KSerializer<V> =
        serializers[adapter] as? KSerializer<V> ?: default
}

@ExperimentalMultiFormatApi
open class MultiFormatBacked<out T> protected constructor(
    override val value: T,
    override val backingObject: Map<Any, Any>,
    override val serialFormat: SerialFormat,
    internal val objectFormats: ObjectFormatSet,
    internal val backingMetadata: Any?,
) : NativeBacked<T, Map<Any, Any>, SerialFormat> {
    internal val objectFormat: ObjectFormatAdapter = objectFormats.adapterFor(serialFormat)

    protected constructor(backed: MultiFormatBacked<@UnsafeVariance T>) : this(
        backed.value,
        backed.backingObject,
        backed.serialFormat,
        backed.objectFormats,
        backed.backingMetadata,
    )

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[objectFormat.defaultKey(key)]
            ?.takeUnless { objectFormat.isFormatNull(it) }
            ?.let { objectFormat.decodeElement(serialFormat, it, serializer) }

    companion object {
        @PublishedApi
        internal fun <T> create(
            value: T,
            backingObject: Map<*, *>,
            serialFormat: SerialFormat,
            objectFormats: ObjectFormatSet,
            backingMetadata: Any?,
        ): MultiFormatBacked<T> = MultiFormatBacked(
            value,
            backingObject.entries.associate { (key, element) ->
                requireNotNull(key) to requireNotNull(element)
            },
            serialFormat,
            objectFormats,
            backingMetadata,
        )
    }
}

@ExperimentalMultiFormatApi
inline fun <reified T> MultiFormatBacked(
    value: T,
    serialFormat: SerialFormat,
    objectFormats: ObjectFormatSet,
): MultiFormatBacked<T> =
    MultiFormatBacked(value, serializer(), serialFormat, objectFormats)

@ExperimentalMultiFormatApi
fun <T> MultiFormatBacked(
    value: T,
    serializer: KSerializer<T>,
    serialFormat: SerialFormat,
    objectFormats: ObjectFormatSet,
): MultiFormatBacked<T> {
    val adapter = objectFormats.adapterFor(serialFormat)
    val backing = adapter.encodeValue(serialFormat, value, serializer.forFormat(serialFormat))
    return MultiFormatBacked.create(
        value,
        backing.backingObject,
        serialFormat,
        objectFormats,
        adapter.backingMetadata(backing.backingObject),
    )
}

@PublishedApi
internal fun <V> createMultiFormatProperty(
    bindings: Array<out ObjectFormatPropertyBinding<V>>,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
): ReadOnlyProperty<MultiFormatBacked<*>, V> {
    require(bindings.map { it.format }.distinct().size == bindings.size) {
        "A property may define only one binding per format"
    }
    return ReadOnlyProperty { owner, property ->
        val binding = bindings.singleOrNull {
            it.format === owner.objectFormat || it.format == owner.objectFormat
        }
        val key = binding?.key ?: owner.objectFormat.defaultKey(property.name)
        @Suppress("UNCHECKED_CAST")
        val actualSerializer =
            (binding as? ObjectFormatProperty<V>)?.serializer ?: serializer
        owner.backingObject[key]
            ?.takeUnless { owner.objectFormat.isFormatNull(it) }
            ?.let { owner.objectFormat.decodeElement(owner.serialFormat, it, actualSerializer) }
            ?: multiFormatMissingValue(key, actualSerializer, defaultValue)
    }
}

private fun <V> multiFormatMissingValue(
    key: Any,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
): V {
    if (defaultValue != null) return defaultValue()
    if (serializer.descriptor.isNullable) {
        @Suppress("UNCHECKED_CAST")
        return null as V
    }
    throw NoSuchElementException("Missing property: $key")
}

@ExperimentalMultiFormatApi
inline fun <reified V> multiFormatProperty(
    vararg bindings: ObjectFormatPropertyBinding<V>,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<MultiFormatBacked<*>, V> =
    createMultiFormatProperty(bindings, serializer, null)

@ExperimentalMultiFormatApi
inline fun <reified V> multiFormatProperty(
    vararg bindings: ObjectFormatPropertyBinding<V>,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<MultiFormatBacked<*>, V> =
    createMultiFormatProperty(bindings, serializer) { defaultValue }

@ExperimentalMultiFormatApi
abstract class MultiFormatBackedObject(
    backingObject: Map<*, *>,
    final override val serialFormat: SerialFormat,
    internal val objectFormats: ObjectFormatSet,
) : ObjectBackedObject<MultiFormatKey>() {
    internal val objectFormat: ObjectFormatAdapter = objectFormats.adapterFor(serialFormat)
    internal val backingMetadata: Any? = objectFormat.backingMetadata(backingObject)

    final var backingObject: Map<Any, Any> =
        backingObject.entries.associate { (key, value) ->
            requireNotNull(key) to requireNotNull(value)
        }
        private set

    protected inline fun <reified V> multiFormatProperty(
        vararg bindings: ObjectFormatPropertyBinding<V>,
        serializer: KSerializer<V> = serializer(),
    ): BackedProperty<V> {
        require(bindings.map { it.format }.distinct().size == bindings.size) {
            "A property may define only one binding per format"
        }
        val aliases = bindings.associate { it.format to it.key }
        val serializers = bindings.mapNotNull { binding ->
            (binding as? ObjectFormatProperty<*>)?.let { it.format to it.serializer }
        }.toMap()
        return backedProperty(serializer) {
            MultiFormatKey(it, aliases, serializers)
        }
    }

    protected inline fun <reified V> multiFormatProperty(
        vararg bindings: ObjectFormatPropertyBinding<V>,
        serializer: KSerializer<V> = serializer(),
        defaultValue: V,
    ): BackedProperty<V> {
        require(bindings.map { it.format }.distinct().size == bindings.size) {
            "A property may define only one binding per format"
        }
        val aliases = bindings.associate { it.format to it.key }
        val serializers = bindings.mapNotNull { binding ->
            (binding as? ObjectFormatProperty<*>)?.let { it.format to it.serializer }
        }.toMap()
        return backedProperty(serializer, defaultValue) {
            MultiFormatKey(it, aliases, serializers)
        }
    }

    protected final override fun keyFromPropertyName(name: String): MultiFormatKey =
        MultiFormatKey(name, emptyMap())

    protected final override fun <V> readElement(
        key: MultiFormatKey,
        serializer: KSerializer<V>,
    ): V? = backingObject[key.keyFor(objectFormat)]
        ?.takeUnless { objectFormat.isFormatNull(it) }
        ?.let {
        objectFormat.decodeElement(
            serialFormat,
            it,
            key.serializerFor(objectFormat, serializer),
        )
    }

    protected final override fun <V> writeElement(
        key: MultiFormatKey,
        serializer: KSerializer<V>,
        value: V,
    ) {
        backingObject = backingObject +
                (key.keyFor(objectFormat) to objectFormat.encodeElement(
                    serialFormat,
                    value,
                    key.serializerFor(objectFormat, serializer),
                ))
    }
}

@ExperimentalMultiFormatApi
interface MultiFormatSerializer<T> : KSerializer<T> {
    fun serializerFor(serialFormat: SerialFormat): KSerializer<T>
}

@ExperimentalMultiFormatApi
open class MultiFormatBackedSerializerTemplate<T, B : MultiFormatBacked<T>>(
    private val valueSerializer: KSerializer<T>,
    private val objectFormats: ObjectFormatSet,
    private val wrap: (MultiFormatBacked<T>) -> B,
) : MultiFormatSerializer<B> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun serializerFor(serialFormat: SerialFormat): KSerializer<B> {
        val expectedAdapter = objectFormats.adapterFor(serialFormat)
        return object : KSerializer<B> {
            override val descriptor: SerialDescriptor = expectedAdapter.descriptor

            override fun deserialize(decoder: Decoder): B {
                requireMatchingAdapter(objectFormats.adapterFor(decoder), expectedAdapter)
                return this@MultiFormatBackedSerializerTemplate.deserialize(decoder)
            }

            override fun serialize(encoder: Encoder, value: B) {
                requireMatchingAdapter(objectFormats.adapterFor(encoder), expectedAdapter)
                this@MultiFormatBackedSerializerTemplate.serialize(encoder, value)
            }
        }
    }

    override fun deserialize(decoder: Decoder): B {
        val adapter = objectFormats.adapterFor(decoder)
        val decoded = adapter.decodeObject(decoder)
        return wrap(
            MultiFormatBacked.create(
                value = adapter.decodeValue(
                    decoded,
                    valueSerializer.forFormat(decoded.serialFormat),
                ),
                backingObject = decoded.backingObject,
                serialFormat = decoded.serialFormat,
                objectFormats = objectFormats,
                backingMetadata = adapter.backingMetadata(decoded.backingObject),
            )
        )
    }

    override fun serialize(encoder: Encoder, value: B) {
        val adapter = objectFormats.adapterFor(encoder)
        require(adapter === value.objectFormat || adapter == value.objectFormat) {
            "A ${value.objectFormat}-backed value cannot be encoded as $adapter"
        }
        adapter.encodeObject(
            encoder,
            value.backingObject,
            value.serialFormat,
            value.backingMetadata,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> KSerializer<T>.forFormat(serialFormat: SerialFormat): KSerializer<T> =
    (this as? MultiFormatSerializer<T>)?.serializerFor(serialFormat) ?: this

@ExperimentalMultiFormatApi
open class MultiFormatBackedObjectSerializerTemplate<T : MultiFormatBackedObject>(
    override val descriptor: SerialDescriptor,
    private val objectFormats: ObjectFormatSet,
    private val create: (Map<*, *>, SerialFormat) -> T,
) : MultiFormatSerializer<T> {
    override fun serializerFor(serialFormat: SerialFormat): KSerializer<T> {
        val expectedAdapter = objectFormats.adapterFor(serialFormat)
        return object : KSerializer<T> {
            override val descriptor: SerialDescriptor = expectedAdapter.descriptor

            override fun deserialize(decoder: Decoder): T {
                requireMatchingAdapter(objectFormats.adapterFor(decoder), expectedAdapter)
                return this@MultiFormatBackedObjectSerializerTemplate.deserialize(decoder)
            }

            override fun serialize(encoder: Encoder, value: T) {
                requireMatchingAdapter(objectFormats.adapterFor(encoder), expectedAdapter)
                this@MultiFormatBackedObjectSerializerTemplate.serialize(encoder, value)
            }
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val decoded = objectFormats.adapterFor(decoder).decodeObject(decoder)
        return create(decoded.backingObject, decoded.serialFormat).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        val adapter = objectFormats.adapterFor(encoder)
        require(adapter === value.objectFormat || adapter == value.objectFormat) {
            "A ${value.objectFormat}-backed value cannot be encoded as $adapter"
        }
        adapter.encodeObject(
            encoder,
            value.backingObject,
            value.serialFormat,
            value.backingMetadata,
        )
    }
}

private fun requireMatchingAdapter(
    actual: ObjectFormatAdapter,
    expected: ObjectFormatAdapter,
) {
    require(actual === expected || actual == expected) {
        "This serializer is bound to $expected, but was used with $actual"
    }
}
