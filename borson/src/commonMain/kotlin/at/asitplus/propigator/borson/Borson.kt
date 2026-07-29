// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
    ExperimentalUnsignedTypes::class,
    at.asitplus.propigator.multi.ExperimentalMultiFormatApi::class,
)

package at.asitplus.propigator.borson

import at.asitplus.propigator.cbor.hasSameConfigurationAs
import at.asitplus.propigator.json.hasSameConfigurationAs
import at.asitplus.propigator.multi.DecodedObjectBacking
import at.asitplus.propigator.multi.ExperimentalMultiFormatApi
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.ObjectFormatSet
import at.asitplus.propigator.multi.objectFormats
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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
}

@ExperimentalMultiFormatApi
val JsonCborFormats: ObjectFormatSet =
    objectFormats(JsonObjectFormat, CborMapFormat)
