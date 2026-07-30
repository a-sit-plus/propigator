// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalUnsignedTypes::class)

package at.asitplus.propigator.cbor

import at.asitplus.propigator.common.NativeBacked
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty

/** A serializable value together with its complete native CBOR map. */
@Serializable(with = CborBackedSerializer::class)
open class CborBacked<out T> protected constructor(
    override val value: T,
    override val backingObject: CborMap,
    override val serialFormat: Cbor,
) : NativeBacked<T, CborMap, Cbor> {

    protected constructor(backed: CborBacked<@UnsafeVariance T>) : this(
        backed.value,
        backed.backingObject,
        backed.serialFormat,
    )

    fun <V> getElement(key: CborElement, serializer: KSerializer<V>): V? =
        backingObject[key]
            ?.takeUnless { it is CborNull }
            ?.let { serialFormat.decodeFromCborElement(serializer, it) }

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        getElement(CborString(key), serializer)

    companion object {
        @PublishedApi
        internal fun <T> create(
            value: T,
            backingObject: CborMap,
            serialFormat: Cbor,
        ): CborBacked<T> = CborBacked(value, backingObject, serialFormat)
    }
}

/** Creates a CBOR-backed envelope owned by this format. */
inline fun <reified T> Cbor.CborBacked(value: T): CborBacked<T> =
    CborBacked(value, serializer())

/** Creates a CBOR-backed envelope using an explicitly selected serializer. */
fun <T> Cbor.CborBacked(value: T, serializer: KSerializer<T>): CborBacked<T> =
    CborBacked.create(
        value = value,
        backingObject = encodeToCborElement(serializer, value) as? CborMap
            ?: error("CborBacked only supports values encoded as CBOR maps"),
        serialFormat = this,
    )

/** Creates a CBOR-backed envelope using the contextual format. */
@JvmName("CborBackedFromContext")
context(serialFormat: Cbor)
inline fun <reified T> CborBacked(value: T): CborBacked<T> =
    serialFormat.CborBacked(value)

inline fun <reified T> Cbor.decodeFromCborElementBacked(element: CborElement): CborBacked<T> =
    decodeFromCborElement(element)

inline fun <reified T> Cbor.encodeToCborElementBacked(value: CborBacked<T>): CborElement =
    encodeToCborElement(value)

inline fun <reified T> Cbor.decodeFromByteArrayBacked(bytes: ByteArray): CborBacked<T> =
    decodeFromByteArray(bytes)

inline fun <reified T> Cbor.encodeToByteArrayBacked(value: CborBacked<T>): ByteArray =
    encodeToByteArray(value)

@PublishedApi
internal fun <V> createCborBackedProperty(
    key: CborElement?,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
    keyFromPropertyName: (String) -> CborElement = { CborString(it) },
): ReadOnlyProperty<CborBacked<*>, V> = ReadOnlyProperty { owner, property ->
    val actualKey = key ?: keyFromPropertyName(property.name)
    owner.getElement(actualKey, serializer)
        ?: cborMissingValue(actualKey, serializer, defaultValue)
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
 * Reads an additional property directly from the retained CBOR map.
 *
 * The canonical key type is [CborElement]. String and tagged-string overloads cover the common
 * cases. Serializing tagged keys requires `Cbor { encodeKeyTags = true }`.
 */
inline fun <reified V> cborProperty(
    key: CborElement? = null,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(key, serializer, null)

inline fun <reified V> cborProperty(
    key: CborElement? = null,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(key, serializer, { defaultValue })

inline fun <reified V> cborProperty(
    key: String,
    serializer: KSerializer<V> = serializer(),
    keyTags: ULongArray = ulongArrayOf(),
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(CborString(key, *keyTags), serializer, null)

inline fun <reified V> cborProperty(
    key: String,
    serializer: KSerializer<V> = serializer(),
    keyTags: ULongArray = ulongArrayOf(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(CborString(key, *keyTags), serializer, { defaultValue })

inline fun <reified V> cborProperty(
    keyTags: ULongArray,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(null, serializer, null) { CborString(it, *keyTags) }

inline fun <reified V> cborProperty(
    keyTags: ULongArray,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<CborBacked<*>, V> =
    createCborBackedProperty(null, serializer, { defaultValue }) {
        CborString(it, *keyTags)
    }

/** Reuses [CborBacked] serialization for a concrete subclass created by [wrap]. */
open class CborBackedSerializerTemplate<T, B : CborBacked<T>>(
    private val valueSerializer: KSerializer<T>,
    private val wrap: (CborBacked<T>) -> B,
) : KSerializer<B> {
    override val descriptor: SerialDescriptor = CborMap.serializer().descriptor

    context(serialFormat: Cbor)
    open operator fun invoke(value: T): B =
        wrap(serialFormat.CborBacked(value, valueSerializer))

    override fun deserialize(decoder: Decoder): B {
        decoder as? CborDecoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        val backingObject = decoder.decodeCborElement() as? CborMap
            ?: error("CborBackedSerializer only supports CBOR maps")
        val valueFormat = if (decoder.cbor.configuration.ignoreUnknownKeys) {
            decoder.cbor
        } else {
            Cbor(decoder.cbor) { ignoreUnknownKeys = true }
        }
        val carrierObject = CborMap(
            backingObject.filterKeys { it is CborString || it is CborInteger },
            backingObject.tags,
        )
        return wrap(
            CborBacked.create(
                value = valueFormat.decodeFromCborElement(valueSerializer, carrierObject),
                backingObject = backingObject,
                serialFormat = decoder.cbor,
            )
        )
    }

    override fun serialize(encoder: Encoder, value: B) {
        encoder as? CborEncoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        require(encoder.cbor.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching CborConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeCborElement(value.backingObject)
    }
}

/** Generic serializer used automatically for [CborBacked]. */
class CborBackedSerializer<T>(
    valueSerializer: KSerializer<T>,
) : CborBackedSerializerTemplate<T, CborBacked<T>>(valueSerializer, { it })

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
