// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.borson.interfaceonly

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
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

private interface XoseHeader {
    val algorithm: String
    val type: String?
    val keyId: String?
}

private interface GromitAuthenticationHeader : XoseHeader {
    val numberOfChickens: Int
}

@Serializable(with = JoseHeader.Serializer::class)
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
        operator fun invoke(
            algorithm: String,
            type: String? = null,
            keyId: String? = null,
        ): JoseHeader = JoseHeader(JsonObject(emptyMap()), serialFormat).validating {
            this.algorithm = algorithm
            this.type = type
            this.keyId = keyId
        }
    }
}

@Serializable(with = GromitJoseHeader.Serializer::class)
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
        operator fun invoke(
            algorithm: String,
            numberOfChickens: Int,
            type: String? = null,
            keyId: String? = null,
        ): GromitJoseHeader {
            val base = JoseHeader(algorithm, type, keyId)
            return GromitJoseHeader(base.backingObject, serialFormat).validating {
                this.numberOfChickens = numberOfChickens
            }
        }
    }
}

@Serializable(with = CoseHeader.Serializer::class)
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
        operator fun invoke(
            algorithm: String,
            type: String? = null,
            keyId: String? = null,
        ): CoseHeader = CoseHeader(CborMap(emptyMap()), serialFormat).validating {
            this.algorithm = algorithm
            this.type = type
            this.keyId = keyId
        }
    }
}

@Serializable(with = GromitCoseHeader.Serializer::class)
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
        operator fun invoke(
            algorithm: String,
            numberOfChickens: Int,
            type: String? = null,
            keyId: String? = null,
        ): GromitCoseHeader {
            val base = CoseHeader(algorithm, type, keyId)
            return GromitCoseHeader(base.backingObject, serialFormat).validating {
                this.numberOfChickens = numberOfChickens
            }
        }
    }
}

private fun GromitAuthenticationHeader.semanticView(): String =
    "$algorithm:$numberOfChickens:${type ?: "-"}:${keyId ?: "-"}"

internal val InterfaceOnlyTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }
    val cbor = Cbor {
        ignoreUnknownKeys = true
        encodeObjectTags = true
    }

    "JSON and CBOR implementations share the semantic interfaces" {
        val jose: GromitAuthenticationHeader = with(json) {
            GromitJoseHeader("ES256", 23, "JWT", "moon-cheese-key")
        }
        val cose: GromitAuthenticationHeader = with(cbor) {
            GromitCoseHeader("ES256", 23, "JWT", "moon-cheese-key")
        }

        jose.semanticView() shouldBe cose.semanticView()
    }

    "construct and encode the JSON implementation" {
        val header = with(json) {
            GromitJoseHeader("ES256", 23, "JWT", "moon-cheese-key")
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

    "construct and encode the CBOR implementation" {
        val header = with(cbor) {
            GromitCoseHeader("ES256", 23, "JWT", "moon-cheese-key")
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
        cbor.encodeToCborElement(header) shouldBe expected
    }

    "copy the base implementation into the downstream specialization" - {
        "JSON" {
            val base = with(json) { JoseHeader("ES256", "JWT") }
            val copied = JsonObject(
                base.backingObject + ("number_of_chickens" to JsonPrimitive(42))
            )
            val gromit = json.decodeFromJsonElement<GromitJoseHeader>(copied)

            gromit.numberOfChickens shouldBe 42
            json.encodeToJsonElement(gromit) shouldBe copied
        }

        "CBOR" {
            val base = with(cbor) { CoseHeader("ES256", "JWT") }
            val unknownComplexKey = CborMap(
                mapOf(CborString("wallace") to CborString("gromit"))
            )
            val copied = CborMap(
                base.backingObject + mapOf(
                    CborInteger(-65_537L) to CborInteger(42L),
                    unknownComplexKey to CborString("unknown but preserved"),
                ),
            )
            val gromit = cbor.decodeFromCborElement<GromitCoseHeader>(copied)

            gromit.numberOfChickens shouldBe 42
            cbor.encodeToCborElement(gromit) shouldBe copied
        }
    }

    "each implementation rejects a missing mandatory chicken count" {
        shouldThrow<NoSuchElementException> {
            json.decodeFromJsonElement<GromitJoseHeader>(
                JsonObject(mapOf("alg" to JsonPrimitive("ES256")))
            )
        }
        shouldThrow<NoSuchElementException> {
            cbor.decodeFromCborElement<GromitCoseHeader>(
                CborMap(mapOf(CborInteger(1L) to CborString("ES256")))
            )
        }
    }
}
