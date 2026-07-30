// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
    ExperimentalUnsignedTypes::class,
    at.asitplus.propigator.multi.ExperimentalMultiFormatApi::class,
)

package at.asitplus.propigator.borson

import at.asitplus.propigator.cbor.hasSameConfigurationAs
import at.asitplus.propigator.cbor.CborFlatteningSerializerTemplate
import at.asitplus.propigator.common.Flattened
import at.asitplus.propigator.json.JsonFlatteningSerializerTemplate
import at.asitplus.propigator.json.hasSameConfigurationAs
import at.asitplus.propigator.multi.DecodedObjectBacking
import at.asitplus.propigator.multi.ExperimentalMultiFormatApi
import at.asitplus.propigator.multi.MultiFormatBacked
import at.asitplus.propigator.multi.MultiFormatBackedSerializerTemplate
import at.asitplus.propigator.multi.MultiFormatSerializer
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.ObjectFormatSet
import at.asitplus.propigator.multi.objectFormats
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

@ExperimentalMultiFormatApi
data object JsonObjectFormat : ObjectFormatAdapter {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun supports(serialFormat: SerialFormat): Boolean = serialFormat is Json
    override fun supports(decoder: Decoder): Boolean = decoder is JsonDecoder
    override fun supports(encoder: Encoder): Boolean = encoder is JsonEncoder
    override fun defaultKey(propertyName: String): Any = propertyName
    override fun isFormatNull(element: Any?): Boolean = element is JsonNull

    override fun decodeObject(decoder: Decoder): DecodedObjectBacking {
        decoder as JsonDecoder
        return DecodedObjectBacking(decoder.decodeJsonElement().jsonObject, decoder.json)
    }

    override fun encodeObject(
        encoder: Encoder,
        backingObject: Map<Any, Any>,
        serialFormat: SerialFormat,
        metadata: Any?,
    ) {
        require(serialFormat is Json)
        encoder as JsonEncoder
        require(encoder.json.hasSameConfigurationAs(serialFormat)) {
            "Mismatching JsonConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeJsonElement(
            JsonObject(backingObject.map { (key, value) ->
                (key as String) to (value as JsonElement)
            }.toMap())
        )
    }

    override fun <V> decodeElement(
        serialFormat: SerialFormat,
        element: Any,
        serializer: KSerializer<V>,
    ): V = (serialFormat as Json).decodeFromJsonElement(serializer, element as JsonElement)

    override fun <V> encodeElement(
        serialFormat: SerialFormat,
        value: V,
        serializer: KSerializer<V>,
    ): Any = (serialFormat as Json).encodeToJsonElement(serializer, value)

    override fun <T> decodeValue(
        backing: DecodedObjectBacking,
        serializer: KSerializer<T>,
    ): T {
        val serialFormat = backing.serialFormat as Json
        val valueFormat = if (serialFormat.configuration.ignoreUnknownKeys) {
            serialFormat
        } else {
            Json(serialFormat) { ignoreUnknownKeys = true }
        }
        val backingObject = JsonObject(
            backing.backingObject.map { (key, value) ->
                key as String to value as JsonElement
            }.toMap()
        )
        return valueFormat.decodeFromJsonElement(serializer, backingObject)
    }

    override fun <T> encodeValue(
        serialFormat: SerialFormat,
        value: T,
        serializer: KSerializer<T>,
    ): DecodedObjectBacking {
        serialFormat as Json
        return DecodedObjectBacking(
            serialFormat.encodeToJsonElement(serializer, value).jsonObject,
            serialFormat,
        )
    }
}

@ExperimentalMultiFormatApi
data object CborMapFormat : ObjectFormatAdapter {
    override val descriptor: SerialDescriptor = CborMap.serializer().descriptor

    override fun supports(serialFormat: SerialFormat): Boolean = serialFormat is Cbor
    override fun supports(decoder: Decoder): Boolean = decoder is CborDecoder
    override fun supports(encoder: Encoder): Boolean = encoder is CborEncoder
    override fun defaultKey(propertyName: String): Any = CborString(propertyName)
    override fun isFormatNull(element: Any?): Boolean = element is CborNull

    override fun decodeObject(decoder: Decoder): DecodedObjectBacking {
        decoder as CborDecoder
        val backingObject = decoder.decodeCborElement() as? CborMap
            ?: error("CBOR object backing requires a CBOR map")
        return DecodedObjectBacking(backingObject, decoder.cbor)
    }

    override fun backingMetadata(backingObject: Map<*, *>): Any? =
        (backingObject as? CborMap)?.tags?.toList()

    override fun encodeObject(
        encoder: Encoder,
        backingObject: Map<Any, Any>,
        serialFormat: SerialFormat,
        metadata: Any?,
    ) {
        require(serialFormat is Cbor)
        encoder as CborEncoder
        require(encoder.cbor.hasSameConfigurationAs(serialFormat)) {
            "Mismatching CborConfiguration. By default, the object owns the serialization shape."
        }
        @Suppress("UNCHECKED_CAST")
        val tags = metadata as? List<ULong> ?: emptyList()
        encoder.encodeCborElement(
            CborMap(backingObject.map { (key, value) ->
                (key as CborElement) to (value as CborElement)
            }.toMap(), tags)
        )
    }

    override fun <V> decodeElement(
        serialFormat: SerialFormat,
        element: Any,
        serializer: KSerializer<V>,
    ): V = (serialFormat as Cbor).decodeFromCborElement(serializer, element as CborElement)

    override fun <V> encodeElement(
        serialFormat: SerialFormat,
        value: V,
        serializer: KSerializer<V>,
    ): Any = (serialFormat as Cbor).encodeToCborElement(serializer, value)

    override fun <T> decodeValue(
        backing: DecodedObjectBacking,
        serializer: KSerializer<T>,
    ): T {
        val serialFormat = backing.serialFormat as Cbor
        val valueFormat = if (serialFormat.configuration.ignoreUnknownKeys) {
            serialFormat
        } else {
            Cbor(serialFormat) { ignoreUnknownKeys = true }
        }
        val carrierObject = CborMap(
            backing.backingObject
                .map { (key, value) -> key as CborElement to value as CborElement }
                .toMap()
                .filterKeys { it is CborString || it is kotlinx.serialization.cbor.CborInteger },
            (backing.backingObject as? CborMap)?.tags.orEmpty(),
        )
        return valueFormat.decodeFromCborElement(serializer, carrierObject)
    }

    override fun <T> encodeValue(
        serialFormat: SerialFormat,
        value: T,
        serializer: KSerializer<T>,
    ): DecodedObjectBacking {
        serialFormat as Cbor
        val backingObject = serialFormat.encodeToCborElement(serializer, value) as? CborMap
            ?: error("CBOR object backing requires a CBOR map")
        return DecodedObjectBacking(backingObject, serialFormat)
    }
}

@ExperimentalMultiFormatApi
val JsonCborFormats: ObjectFormatSet =
    objectFormats(JsonObjectFormat, CborMapFormat)

@ExperimentalMultiFormatApi
@Serializable(with = BorsonBackedSerializer::class)
open class BorsonBacked<out T> protected constructor(
    backed: MultiFormatBacked<@UnsafeVariance T>,
) : MultiFormatBacked<T>(backed) {

    protected constructor(backed: BorsonBacked<@UnsafeVariance T>) : this(
        backed as MultiFormatBacked<T>
    )

    companion object {
        @PublishedApi
        internal fun <T> create(backed: MultiFormatBacked<T>): BorsonBacked<T> =
            BorsonBacked(backed)
    }
}

@ExperimentalMultiFormatApi
inline fun <reified T> SerialFormat.BorsonBacked(value: T): BorsonBacked<T> =
    BorsonBacked(value, serializer())

@ExperimentalMultiFormatApi
fun <T> SerialFormat.BorsonBacked(
    value: T,
    serializer: KSerializer<T>,
): BorsonBacked<T> =
    BorsonBacked.create(MultiFormatBacked(value, serializer, this, JsonCborFormats))

@ExperimentalMultiFormatApi
@JvmName("BorsonBackedFromContext")
context(serialFormat: SerialFormat)
inline fun <reified T> BorsonBacked(value: T): BorsonBacked<T> =
    serialFormat.BorsonBacked(value)

@ExperimentalMultiFormatApi
open class BorsonBackedSerializerTemplate<T, B : BorsonBacked<T>>(
    valueSerializer: KSerializer<T>,
    wrap: (BorsonBacked<T>) -> B,
) : MultiFormatBackedSerializerTemplate<T, B>(
    valueSerializer,
    JsonCborFormats,
    { wrap(BorsonBacked.create(it)) },
)

@ExperimentalMultiFormatApi
class BorsonBackedSerializer<T>(
    valueSerializer: KSerializer<T>,
) : BorsonBackedSerializerTemplate<T, BorsonBacked<T>>(valueSerializer, { it })

@ExperimentalMultiFormatApi
inline fun <reified T> Json.decodeFromJsonElementBorson(element: JsonElement): BorsonBacked<T> =
    decodeFromJsonElement(element)

@ExperimentalMultiFormatApi
inline fun <reified T> Json.encodeToJsonElementBorson(value: BorsonBacked<T>): JsonElement =
    encodeToJsonElement(value)

@ExperimentalMultiFormatApi
inline fun <reified T> Json.decodeFromStringBorson(string: String): BorsonBacked<T> =
    decodeFromString(string)

@ExperimentalMultiFormatApi
inline fun <reified T> Json.encodeToStringBorson(value: BorsonBacked<T>): String =
    encodeToString(value)

@ExperimentalMultiFormatApi
inline fun <reified T> Cbor.decodeFromCborElementBorson(element: CborElement): BorsonBacked<T> =
    decodeFromCborElement(element)

@ExperimentalMultiFormatApi
inline fun <reified T> Cbor.encodeToCborElementBorson(value: BorsonBacked<T>): CborElement =
    encodeToCborElement(value)

@ExperimentalMultiFormatApi
inline fun <reified T> Cbor.decodeFromByteArrayBorson(bytes: ByteArray): BorsonBacked<T> =
    decodeFromByteArray(bytes)

@ExperimentalMultiFormatApi
inline fun <reified T> Cbor.encodeToByteArrayBorson(value: BorsonBacked<T>): ByteArray =
    encodeToByteArray(value)

@ExperimentalMultiFormatApi
open class BorsonFlatteningSerializerTemplate<T : Flattened<*>>(
    generatedSerializer: KSerializer<T>,
) : MultiFormatSerializer<T> {
    private val jsonSerializer = JsonFlatteningSerializerTemplate(generatedSerializer)
    private val cborSerializer = CborFlatteningSerializerTemplate(generatedSerializer)

    override val descriptor: SerialDescriptor = generatedSerializer.descriptor

    override fun serializerFor(serialFormat: SerialFormat): KSerializer<T> = when (serialFormat) {
        is Json -> jsonSerializer
        is Cbor -> cborSerializer
        else -> error("Unsupported serial format: $serialFormat")
    }

    override fun deserialize(decoder: Decoder): T = when (decoder) {
        is JsonDecoder -> jsonSerializer.deserialize(decoder)
        is CborDecoder -> cborSerializer.deserialize(decoder)
        else -> error("Unsupported decoder: ${decoder::class}")
    }

    override fun serialize(encoder: Encoder, value: T) = when (encoder) {
        is JsonEncoder -> jsonSerializer.serialize(encoder, value)
        is CborEncoder -> cborSerializer.serialize(encoder, value)
        else -> error("Unsupported encoder: ${encoder::class}")
    }
}
