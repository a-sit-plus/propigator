// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalUnsignedTypes::class)

package at.asitplus.propigator.cbor

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.ObjectBackedObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

interface CborBacked : ObjectBacked {
    val backingObject: CborMap
    override val serialFormat: Cbor

    fun <V> getElement(key: CborElement, serializer: KSerializer<V>): V? =
        backingObject[key]
            ?.takeUnless { it is CborNull }
            ?.let { serialFormat.decodeFromCborElement(serializer, it) }

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        getElement(CborString(key), serializer)

}

abstract class CborBackedObject(
    backingObject: CborMap,
    final override val serialFormat: Cbor = Cbor.Default,
) : ObjectBackedObject<CborElement>(), CborBacked {
    final override var backingObject: CborMap = backingObject
        private set

    protected inline fun <reified V> cborProperty(
        key: CborElement? = null,
        serializer: KSerializer<V> = serializer(),
    ): BackedProperty<V> = backedProperty(key, serializer)

    protected inline fun <reified V> cborProperty(
        key: CborElement? = null,
        serializer: KSerializer<V> = serializer(),
        defaultValue: V,
    ): BackedProperty<V> = backedProperty(key, serializer, defaultValue)

    protected inline fun <reified V> cborProperty(
        key: String,
        serializer: KSerializer<V> = serializer(),
        keyTags: ULongArray = ulongArrayOf(),
    ): BackedProperty<V> =
        backedProperty(cborStringKey(key, keyTags), serializer)

    protected inline fun <reified V> cborProperty(
        key: String,
        serializer: KSerializer<V> = serializer(),
        keyTags: ULongArray = ulongArrayOf(),
        defaultValue: V,
    ): BackedProperty<V> =
        backedProperty(cborStringKey(key, keyTags), serializer, defaultValue)

    protected inline fun <reified V> cborProperty(
        keyTags: ULongArray,
        serializer: KSerializer<V> = serializer(),
    ): BackedProperty<V> =
        backedProperty(serializer) { cborStringKey(it, keyTags) }

    protected inline fun <reified V> cborProperty(
        keyTags: ULongArray,
        serializer: KSerializer<V> = serializer(),
        defaultValue: V,
    ): BackedProperty<V> =
        backedProperty(serializer, defaultValue) { cborStringKey(it, keyTags) }

    protected final override fun keyFromPropertyName(name: String): CborElement = CborString(name)

    protected final override fun <V> readElement(key: CborElement, serializer: KSerializer<V>): V? =
        getElement(key, serializer)

    final override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        readElement(CborString(key), serializer)

    protected final override fun <V> writeElement(key: CborElement, serializer: KSerializer<V>, value: V) {
        backingObject = CborMap(
            backingObject + (key to serialFormat.encodeToCborElement(serializer, value)),
            backingObject.tags,
        )
    }
}

@PublishedApi
@OptIn(ExperimentalUnsignedTypes::class)
internal fun cborStringKey(name: String, tags: ULongArray): CborString =
    CborString(name, *tags)

@PublishedApi
internal fun <V> createCborBackedProperty(
    key: CborElement?,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
    keyFromPropertyName: (String) -> CborElement = { CborString(it) },
): ReadOnlyProperty<CborBacked, V> = ReadOnlyProperty { owner, property ->
    val actualKey = key ?: keyFromPropertyName(property.name)
    val element = owner.getElement(actualKey, serializer)
    if (element == null) {
        cborMissingValue(actualKey, serializer, defaultValue)
    } else {
        element
    }
}

private fun <V> cborMissingValue(
    key: CborElement,
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

/**
 * Optional fields are backed as nullable type.
 * Example
 * ```val foo: String? by cborProperty("foo")```
 *
 * Required fields are backed as strict types
 * ```val bar: Bar by cborProperty("bar_obj", CustomBarSerializer)```
 *
 * The canonical key type is [CborElement]. With no key, the Kotlin property name becomes an
 * untagged [CborString]. String and tagged-string overloads are conveniences for that common case.
 * Serializing tagged keys requires `Cbor { encodeKeyTags = true }`.
 */
inline fun <reified V> cborProperty(
    key: CborElement? = null,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(key, serializer, null)

inline fun <reified V> cborProperty(
    key: CborElement? = null,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(key, serializer, defaultValue = { defaultValue })

inline fun <reified V> cborProperty(
    key: String,
    serializer: KSerializer<V> = serializer(),
    keyTags: ULongArray = ulongArrayOf(),
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(cborStringKey(key, keyTags), serializer, null)

inline fun <reified V> cborProperty(
    key: String,
    serializer: KSerializer<V> = serializer(),
    keyTags: ULongArray = ulongArrayOf(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(
        cborStringKey(key, keyTags),
        serializer,
        defaultValue = { defaultValue },
    )

inline fun <reified V> cborProperty(
    keyTags: ULongArray,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(null, serializer, null) { cborStringKey(it, keyTags) }

inline fun <reified V> cborProperty(
    keyTags: ULongArray,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked, V> =
    createCborBackedProperty(null, serializer, { defaultValue }) {
        cborStringKey(it, keyTags)
    }

open class CborBackedSerializerTemplate<T : CborBacked>(
    private val create: (CborMap, Cbor) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = CborMap.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as? CborDecoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        val backingObject = decoder.decodeCborElement() as? CborMap
            ?: error("CborBackedSerializer only supports CBOR maps")
        return create(backingObject, decoder.cbor).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? CborEncoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        require(encoder.cbor.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching CborConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeCborElement(value.backingObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun Cbor.hasSameConfigurationAs(other: Cbor): Boolean {
    val left = configuration
    val right = other.configuration
    return left.encodeDefaults == right.encodeDefaults &&
            left.ignoreUnknownKeys == right.ignoreUnknownKeys &&
            left.encodeKeyTags == right.encodeKeyTags &&
            left.encodeValueTags == right.encodeValueTags &&
            left.encodeObjectTags == right.encodeObjectTags &&
            left.verifyKeyTags == right.verifyKeyTags &&
            left.verifyValueTags == right.verifyValueTags &&
            left.verifyObjectTags == right.verifyObjectTags &&
            left.useDefiniteLengthEncoding == right.useDefiniteLengthEncoding &&
            left.preferCborLabelsOverNames == right.preferCborLabelsOverNames &&
            left.alwaysUseByteString == right.alwaysUseByteString
}
