// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
    ExperimentalSerializationApi::class,
    at.asitplus.propigator.multi.ExperimentalMultiFormatApi::class,
)

package at.asitplus.propigator.borson.tripleformat

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1ElementFallbackBase64Serializer
import at.asitplus.awesn1.Asn1Null
import at.asitplus.awesn1.encoding.Asn1
import at.asitplus.awesn1.serialization.DER
import at.asitplus.awesn1.serialization.Der
import at.asitplus.awesn1.serialization.DerDecoder
import at.asitplus.awesn1.serialization.DerEncoder
import at.asitplus.propigator.borson.CborMapFormat
import at.asitplus.propigator.borson.JsonCborFormats
import at.asitplus.propigator.borson.JsonObjectFormat
import at.asitplus.propigator.common.validating
import at.asitplus.propigator.multi.DecodedObjectBacking
import at.asitplus.propigator.multi.MultiFormatBackedObject
import at.asitplus.propigator.multi.MultiFormatBackedObjectSerializerTemplate as MultiFormatBackedSerializerTemplate
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.objectFormats
import at.asitplus.propigator.multi.property
import at.asitplus.propigator.multi.propertyKey
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

private val asn1ObjectSerializer =
    MapSerializer(Asn1ElementFallbackBase64Serializer, Asn1ElementFallbackBase64Serializer)

private object IntAsAsn1RealSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = Double.serializer().descriptor

    override fun deserialize(decoder: Decoder): Int = decoder.decodeDouble().toInt()

    override fun serialize(encoder: Encoder, value: Int) =
        encoder.encodeDouble(value.toDouble())
}

private data object Asn1DerMapFormat : ObjectFormatAdapter {
    override val descriptor: SerialDescriptor = asn1ObjectSerializer.descriptor

    override fun supports(serialFormat: SerialFormat): Boolean = serialFormat is Der
    override fun supports(decoder: Decoder): Boolean = decoder is DerDecoder
    override fun supports(encoder: Encoder): Boolean = encoder is DerEncoder
    override fun defaultKey(propertyName: String): Any = Asn1.Utf8String(propertyName)
    override fun isFormatNull(element: Any?): Boolean = element == Asn1Null

    override fun decodeObject(decoder: Decoder): DecodedObjectBacking {
        decoder as DerDecoder
        return DecodedObjectBacking(
            decoder.decodeSerializableValue(asn1ObjectSerializer),
            decoder.der,
        )
    }

    override fun encodeObject(
        encoder: Encoder,
        backingObject: Map<Any, Any>,
        serialFormat: SerialFormat,
        metadata: Any?,
    ) {
        encoder as DerEncoder
        require(serialFormat is Der && encoder.der.configuration == serialFormat.configuration)
        encoder.encodeSerializableValue(
            asn1ObjectSerializer,
            backingObject.map { (key, value) ->
                key as Asn1Element to value as Asn1Element
            }.toMap(),
        )
    }

    override fun <V> decodeElement(
        serialFormat: SerialFormat,
        element: Any,
        serializer: KSerializer<V>,
    ): V = (serialFormat as Der).decodeFromTlv(serializer, element as Asn1Element)

    override fun <V> encodeElement(
        serialFormat: SerialFormat,
        value: V,
        serializer: KSerializer<V>,
    ): Any = requireNotNull((serialFormat as Der).encodeToTlv(serializer, value))
}

private val formats = JsonCborFormats + objectFormats(Asn1DerMapFormat)

@Serializable(with = TripleFormatObject.Companion::class)
private class TripleFormatObject private constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : MultiFormatBackedObject(backingObject, serialFormat, formats) {

    val name: String by multiFormatProperty(
        JsonObjectFormat propertyKey "name",
        CborMapFormat propertyKey CborInteger(1L),
        Asn1DerMapFormat propertyKey Asn1.Int(1),
    )

    val count: Int by multiFormatProperty(
        JsonObjectFormat propertyKey "count",
        CborMapFormat propertyKey CborInteger(2L),
        Asn1DerMapFormat.property(Asn1.Int(2), IntAsAsn1RealSerializer),
    )

    companion object : MultiFormatBackedSerializerTemplate<TripleFormatObject>(
        JsonObject.serializer().descriptor,
        formats,
        ::TripleFormatObject,
    ) {
        context(serialFormat: SerialFormat)
        operator fun invoke(name: String, count: Int): TripleFormatObject =
            TripleFormatObject(emptyMap<Any, Any>(), serialFormat).validating {
                initBackedProperty(TripleFormatObject::name).with(name)
                initBackedProperty(TripleFormatObject::count).with(count)
            }
    }
}

internal val TripleFormatTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }
    val cbor = Cbor { ignoreUnknownKeys = true }
    val der = DER { explicitNulls = true }

    "selects each format descriptor" {
        formats.adapters.size shouldBe 3
        TripleFormatObject.descriptor shouldBe JsonObjectFormat.descriptor
        TripleFormatObject.serializerFor(cbor).descriptor shouldBe CborMapFormat.descriptor
        TripleFormatObject.serializerFor(der).descriptor shouldBe Asn1DerMapFormat.descriptor
    }

    "round-trips JSON, CBOR, and DER with a DER-specific property serializer" {
        val jsonExpected = JsonObject(
            mapOf("name" to JsonPrimitive("Gromit"), "count" to JsonPrimitive(23))
        )
        val jsonValue = with(json) { TripleFormatObject("Gromit", 23) }
        json.encodeToJsonElement(jsonValue) shouldBe jsonExpected

        val cborExpected = CborMap(
            mapOf(CborInteger(1L) to CborString("Gromit"), CborInteger(2L) to CborInteger(23L))
        )
        val cborValue = with(cbor) { TripleFormatObject("Gromit", 23) }
        cbor.encodeToCborElement(
            TripleFormatObject.serializerFor(cbor),
            cborValue,
        ) shouldBe cborExpected

        val derExpected = mapOf(
            Asn1.Int(1) to Asn1.Utf8String("Gromit"),
            Asn1.Int(2) to Asn1.Real(23.0),
        )
        val derSerializer = TripleFormatObject.serializerFor(der)
        val derValue = with(der) { TripleFormatObject("Gromit", 23) }
        derValue.backingObject shouldBe derExpected
        der.decodeFromByteArray(
            derSerializer,
            der.encodeToByteArray(derSerializer, derValue),
        ).count shouldBe 23
    }

    "rejects a mismatched descriptor and missing required values" {
        val jsonValue = with(json) { TripleFormatObject("Gromit", 23) }
        shouldThrow<IllegalArgumentException> {
            json.encodeToJsonElement(TripleFormatObject.serializerFor(der), jsonValue)
        }
        shouldThrow<NoSuchElementException> {
            json.decodeFromJsonElement<TripleFormatObject>(
                JsonObject(mapOf("name" to JsonPrimitive("Gromit")))
            )
        }
        shouldThrow<NoSuchElementException> {
            cbor.decodeFromCborElement<TripleFormatObject>(
                CborMap(mapOf(CborInteger(1L) to CborString("Gromit")))
            )
        }
        shouldThrow<SerializationException> {
            der.decodeFromByteArray(
                TripleFormatObject.serializerFor(der),
                der.encodeToByteArray(
                    asn1ObjectSerializer,
                    mapOf(Asn1.Int(1) to Asn1.Utf8String("Gromit")),
                ),
            )
        }
    }
}
