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
import at.asitplus.propigator.multi.MultiFormatBackedSerializerTemplate
import at.asitplus.propigator.multi.MultiFormatSerializer
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.ObjectFormatSet
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
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

private val asn1ObjectSerializer =
    MapSerializer(Asn1ElementFallbackBase64Serializer, Asn1ElementFallbackBase64Serializer)

private object IntAsAsn1RealSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = Double.serializer().descriptor

    override fun deserialize(decoder: Decoder): Int =
        decoder.decodeDouble().toInt()

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeDouble(value.toDouble())
    }
}

private data object Asn1DerMapFormat : ObjectFormatAdapter {
    override val id: String = "asn1-der"
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

private val XoseFormats: ObjectFormatSet =
    JsonCborFormats + objectFormats(Asn1DerMapFormat)

@Serializable(with = XoseHeader.Serializer::class)
private open class XoseHeader protected constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : MultiFormatBackedObject(backingObject, serialFormat, XoseFormats) {

    final var algorithm: String by multiFormatProperty(
        JsonObjectFormat propertyKey "alg",
        CborMapFormat propertyKey CborInteger(1L),
        Asn1DerMapFormat propertyKey Asn1.Int(1),
    )
        private set

    final var type: String? by multiFormatProperty(
        JsonObjectFormat propertyKey "typ",
        CborMapFormat propertyKey CborInteger(-65_538L),
        Asn1DerMapFormat propertyKey Asn1.Int(-65_538),
    )
        private set

    final var keyId: String? by multiFormatProperty(
        JsonObjectFormat propertyKey "kid",
        CborMapFormat propertyKey CborInteger(4L),
        Asn1DerMapFormat propertyKey Asn1.Int(4),
    )
        private set

    object Serializer : MultiFormatSerializer<XoseHeader> by
        MultiFormatBackedSerializerTemplate(
            JsonObject.serializer().descriptor,
            XoseFormats,
            ::XoseHeader,
        )

    companion object {
        context(serialFormat: SerialFormat)
        operator fun invoke(
            algorithm: String,
            type: String? = null,
            keyId: String? = null,
        ): XoseHeader = XoseHeader(emptyMap<Any, Any>(), serialFormat).validating {
            this.algorithm = algorithm
            this.type = type
            this.keyId = keyId
        }
    }
}

@Serializable(with = GromitAuthenticationHeader.Serializer::class)
private class GromitAuthenticationHeader private constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : XoseHeader(backingObject, serialFormat) {

    var numberOfChickens: Int by multiFormatProperty(
        JsonObjectFormat propertyKey "number_of_chickens",
        CborMapFormat propertyKey CborInteger(-65_537L),
        Asn1DerMapFormat.property(
            key = Asn1.Int(-65_537),
            serializer = IntAsAsn1RealSerializer,
        ),
    )
        private set

    object Serializer : MultiFormatSerializer<GromitAuthenticationHeader> by
        MultiFormatBackedSerializerTemplate(
            JsonObject.serializer().descriptor,
            XoseFormats,
            ::GromitAuthenticationHeader,
        )

    companion object {
        context(serialFormat: SerialFormat)
        operator fun invoke(
            algorithm: String,
            numberOfChickens: Int,
            type: String? = null,
            keyId: String? = null,
        ): GromitAuthenticationHeader {
            val base = XoseHeader(algorithm, type, keyId)
            return GromitAuthenticationHeader(base.backingObject, serialFormat).validating {
                this.numberOfChickens = numberOfChickens
            }
        }
    }
}

internal val TripleFormatTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }
    val cbor = Cbor {
        ignoreUnknownKeys = true
        encodeObjectTags = true
    }
    val der = DER { explicitNulls = true }

    "the copied XOSE model supports three formats" {
        XoseFormats.size shouldBe 3
        GromitAuthenticationHeader.Serializer.descriptor shouldBe JsonObjectFormat.descriptor
        GromitAuthenticationHeader.Serializer.serializerFor(cbor).descriptor shouldBe CborMapFormat.descriptor
        GromitAuthenticationHeader.Serializer.serializerFor(der).descriptor shouldBe Asn1DerMapFormat.descriptor
    }

    "a format-specific serializer rejects another format" {
        val header = with(json) { GromitAuthenticationHeader("ES256", 23) }
        shouldThrow<IllegalArgumentException> {
            json.encodeToJsonElement(
                GromitAuthenticationHeader.Serializer.serializerFor(der),
                header,
            )
        }
    }

    "construct the specialized header as JSON" {
        val header = with(json) {
            GromitAuthenticationHeader("ES256", 23, "JWT", "moon-cheese-key")
        }
        val expected = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "kid" to JsonPrimitive("moon-cheese-key"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )

        header.backingObject shouldBe expected
        json.encodeToJsonElement(header) shouldBe expected
    }

    "construct the specialized header as CBOR" {
        val header = with(cbor) {
            GromitAuthenticationHeader("ES256", 23, "JWT", "moon-cheese-key")
        }
        val expected = CborMap(
            mapOf(
                CborInteger(1L) to CborString("ES256"),
                CborInteger(-65_538L) to CborString("JWT"),
                CborInteger(4L) to CborString("moon-cheese-key"),
                CborInteger(-65_537L) to CborInteger(23L),
            )
        )

        header.backingObject shouldBe expected
        cbor.encodeToCborElement(
            GromitAuthenticationHeader.Serializer.serializerFor(cbor),
            header,
        ) shouldBe expected
    }

    "construct and round-trip the specialized header as ASN.1 DER" {
        val header = with(der) {
            GromitAuthenticationHeader("ES256", 23, "JWT", "moon-cheese-key")
        }
        val expected = mapOf(
            Asn1.Int(1) to Asn1.Utf8String("ES256"),
            Asn1.Int(-65_538) to Asn1.Utf8String("JWT"),
            Asn1.Int(4) to Asn1.Utf8String("moon-cheese-key"),
            Asn1.Int(-65_537) to Asn1.Real(23.0),
        )

        header.backingObject shouldBe expected
        val serializer = GromitAuthenticationHeader.Serializer.serializerFor(der)
        val encoded = der.encodeToByteArray(serializer, header)
        val decoded = der.decodeFromByteArray(serializer, encoded)
        decoded.backingObject shouldBe expected
        decoded.numberOfChickens shouldBe 23
    }

    "all three formats reject a missing mandatory chicken count" {
        shouldThrow<NoSuchElementException> {
            json.decodeFromJsonElement<GromitAuthenticationHeader>(
                JsonObject(mapOf("alg" to JsonPrimitive("ES256")))
            )
        }
        shouldThrow<NoSuchElementException> {
            cbor.decodeFromCborElement<GromitAuthenticationHeader>(
                CborMap(mapOf(CborInteger(1L) to CborString("ES256")))
            )
        }
        shouldThrow<SerializationException> {
            der.decodeFromByteArray(
                GromitAuthenticationHeader.Serializer.serializerFor(der),
                der.encodeToByteArray(
                    asn1ObjectSerializer,
                    mapOf(Asn1.Int(1) to Asn1.Utf8String("ES256")),
                ),
            )
        }
    }
}
