// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.borson.semanticdispatch

import at.asitplus.propigator.cbor.CborBackedObject
import at.asitplus.propigator.cbor.CborBackedSerializerTemplate
import at.asitplus.propigator.common.validating
import at.asitplus.propigator.json.JsonBackedObject
import at.asitplus.propigator.json.JsonBackedSerializerTemplate
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = XoseHeaderSerializer::class)
private interface XoseHeader {
    val algorithm: String
    val type: String?
    val keyId: String?

    companion object {
        operator fun invoke(
            algorithm: String,
            type: String? = null,
            keyId: String? = null,
        ): XoseHeader = XoseHeaderSpec(algorithm, type, keyId)
    }
}

private data class XoseHeaderSpec(
    override val algorithm: String,
    override val type: String?,
    override val keyId: String?,
) : XoseHeader

@Serializable(with = GromitAuthenticationHeaderSerializer::class)
private interface GromitAuthenticationHeader : XoseHeader {
    val numberOfChickens: Int

    companion object {
        operator fun invoke(
            algorithm: String,
            numberOfChickens: Int,
            type: String? = null,
            keyId: String? = null,
        ): GromitAuthenticationHeader =
            GromitAuthenticationHeaderSpec(algorithm, numberOfChickens, type, keyId)
    }
}

private data class GromitAuthenticationHeaderSpec(
    override val algorithm: String,
    override val numberOfChickens: Int,
    override val type: String?,
    override val keyId: String?,
) : GromitAuthenticationHeader

private open class JoseHeader protected constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JsonBackedObject(backingObject, serialFormat), XoseHeader {

    final override var algorithm: String by jsonProperty("alg")
        private set

    final override var type: String? by jsonProperty("typ")
        private set

    final override var keyId: String? by jsonProperty("kid")
        private set

    object Serializer : KSerializer<JoseHeader> by
    JsonBackedSerializerTemplate(::JoseHeader)

    companion object {
        context(serialFormat: Json)
        fun from(value: XoseHeader): JoseHeader =
            value as? JoseHeader
                ?: JoseHeader(JsonObject(emptyMap()), serialFormat).validating {
                    algorithm = value.algorithm
                    type = value.type
                    keyId = value.keyId
                }
    }
}

private class GromitJoseHeader private constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JoseHeader(backingObject, serialFormat), GromitAuthenticationHeader {

    override var numberOfChickens: Int by jsonProperty("number_of_chickens")
        private set

    object Serializer : KSerializer<GromitJoseHeader> by
    JsonBackedSerializerTemplate(::GromitJoseHeader)

    companion object {
        context(serialFormat: Json)
        fun from(
            value: GromitAuthenticationHeader,
        ): GromitJoseHeader {
            if (value is GromitJoseHeader) return value
            val base = JoseHeader.from(value)
            return GromitJoseHeader(base.backingObject, serialFormat).validating {
                numberOfChickens = value.numberOfChickens
            }
        }
    }
}

private open class CoseHeader protected constructor(
    backingObject: CborMap,
    serialFormat: Cbor,
) : CborBackedObject(backingObject, serialFormat), XoseHeader {

    final override var algorithm: String by cborProperty(CborInteger(1L))
        private set

    final override var type: String? by cborProperty(CborInteger(-65_538L))
        private set

    final override var keyId: String? by cborProperty(CborInteger(4L))
        private set

    object Serializer : KSerializer<CoseHeader> by
    CborBackedSerializerTemplate(::CoseHeader)

    companion object {
        context(serialFormat: Cbor)
        fun from(value: XoseHeader): CoseHeader =
            if (value is CoseHeader) value
            else CoseHeader(CborMap(emptyMap()), serialFormat).validating {
                algorithm = value.algorithm
                type = value.type
                keyId = value.keyId
            }
    }
}

private class GromitCoseHeader private constructor(
    backingObject: CborMap,
    serialFormat: Cbor,
) : CoseHeader(backingObject, serialFormat), GromitAuthenticationHeader {

    override var numberOfChickens: Int by cborProperty(CborInteger(-65_537L))
        private set

    object Serializer : KSerializer<GromitCoseHeader> by
    CborBackedSerializerTemplate(::GromitCoseHeader)

    companion object {
        context(serialFormat: Cbor)
        fun from(
            value: GromitAuthenticationHeader,
        ): GromitCoseHeader {
            if (value is GromitCoseHeader) return value
            val base = CoseHeader.from(value)
            return GromitCoseHeader(base.backingObject, serialFormat).validating {
                numberOfChickens = value.numberOfChickens
            }
        }
    }
}

private object XoseHeaderSerializer : KSerializer<XoseHeader> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): XoseHeader = when (decoder) {
        is JsonDecoder -> JoseHeader.Serializer.deserialize(decoder)
        is CborDecoder -> CoseHeader.Serializer.deserialize(decoder)
        else -> error("XOSE headers support only JSON and CBOR")
    }

    override fun serialize(encoder: Encoder, value: XoseHeader) {
        when (encoder) {
            is JsonEncoder ->
                with(encoder.json) {
                    JoseHeader.Serializer.serialize(encoder, JoseHeader.from(value))
                }

            is CborEncoder ->
                with(encoder.cbor) {
                    CoseHeader.Serializer.serialize(encoder, CoseHeader.from(value))
                }

            else -> error("XOSE headers support only JSON and CBOR")
        }
    }
}

private object GromitAuthenticationHeaderSerializer :
    KSerializer<GromitAuthenticationHeader> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): GromitAuthenticationHeader = when (decoder) {
        is JsonDecoder -> GromitJoseHeader.Serializer.deserialize(decoder)
        is CborDecoder -> GromitCoseHeader.Serializer.deserialize(decoder)
        else -> error("Gromit authentication headers support only JSON and CBOR")
    }

    override fun serialize(encoder: Encoder, value: GromitAuthenticationHeader) {
        when (encoder) {
            is JsonEncoder -> with(encoder.json) {
                GromitJoseHeader.Serializer.serialize(
                    encoder,
                    GromitJoseHeader.from(value)
                )
            }

            is CborEncoder -> with(encoder.cbor) {
                GromitCoseHeader.Serializer.serialize(
                    encoder,
                    GromitCoseHeader.from(value),
                )
            }

            else -> error("Gromit authentication headers support only JSON and CBOR")
        }
    }
}

internal val SemanticDispatchTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }
    val cbor = Cbor {
        ignoreUnknownKeys = true
        encodeObjectTags = true
    }

    val expectedJson = JsonObject(
        mapOf(
            "alg" to JsonPrimitive("ES256"),
            "typ" to JsonPrimitive("JWT"),
            "kid" to JsonPrimitive("moon-cheese-key"),
            "number_of_chickens" to JsonPrimitive(23),
        )
    )
    val expectedCbor = CborMap(
        mapOf(
            CborInteger(1L) to CborString("ES256"),
            CborInteger(-65_538L) to CborString("JWT"),
            CborInteger(4L) to CborString("moon-cheese-key"),
            CborInteger(-65_537L) to CborInteger(23L),
        )
    )

    "construct once without a format and serialize to both" {
        val header = GromitAuthenticationHeader(
            algorithm = "ES256",
            numberOfChickens = 23,
            type = "JWT",
            keyId = "moon-cheese-key",
        )

        json.encodeToJsonElement(header) shouldBe expectedJson
        cbor.encodeToCborElement(header) shouldBe expectedCbor
    }

    "deserialize from either format and serialize to the other" {
        val fromJson = json.decodeFromJsonElement<GromitAuthenticationHeader>(expectedJson)
        val fromCbor = cbor.decodeFromCborElement<GromitAuthenticationHeader>(expectedCbor)

        cbor.encodeToCborElement(fromJson) shouldBe expectedCbor
        json.encodeToJsonElement(fromCbor) shouldBe expectedJson
    }

    "the base semantic type uses the same dispatch pattern" {
        val header = XoseHeader("ES256", "JWT", "moon-cheese-key")

        json.encodeToJsonElement(header) shouldBe JsonObject(
            expectedJson - "number_of_chickens"
        )
        cbor.encodeToCborElement(header) shouldBe CborMap(
            expectedCbor - CborInteger(-65_537L)
        )
    }

    "both formats validate the downstream mandatory claim" {
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
    }
}
