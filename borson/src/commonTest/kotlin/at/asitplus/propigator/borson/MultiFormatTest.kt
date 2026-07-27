@file:OptIn(at.asitplus.propigator.multi.ExperimentalMultiFormatApi::class)

package at.asitplus.propigator.borson

import at.asitplus.propigator.common.validating
import at.asitplus.propigator.multi.MultiFormatBackedObject
import at.asitplus.propigator.multi.MultiFormatBackedSerializerTemplate
import at.asitplus.propigator.multi.MultiFormatSerializer
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.objectFormats
import at.asitplus.propigator.multi.propertyKey
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable(with = XoseHeader.Serializer::class)
open class XoseHeader protected constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : MultiFormatBackedObject(backingObject, serialFormat, JsonCborFormats) {

    final var algorithm: String by multiFormatProperty(
        JsonObjectFormat propertyKey "alg",
        CborMapFormat propertyKey CborInteger(1L),
    )
        private set

    final var type: String? by multiFormatProperty(
        JsonObjectFormat propertyKey "typ",
        CborMapFormat propertyKey CborInteger(-65_538L),
    )
        private set

    final var keyId: String? by multiFormatProperty(
        JsonObjectFormat propertyKey "kid",
        CborMapFormat propertyKey CborInteger(4L),
    )
        private set

    object Serializer : MultiFormatSerializer<XoseHeader> by
        MultiFormatBackedSerializerTemplate(
            JsonObject.serializer().descriptor,
            JsonCborFormats,
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
class GromitAuthenticationHeader private constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : XoseHeader(backingObject, serialFormat) {

    var numberOfChickens: Int by multiFormatProperty(
        JsonObjectFormat propertyKey "number_of_chickens",
        CborMapFormat propertyKey CborInteger(-65_537L),
    )
        private set

    object Serializer : MultiFormatSerializer<GromitAuthenticationHeader> by
        MultiFormatBackedSerializerTemplate(
            JsonObject.serializer().descriptor,
            JsonCborFormats,
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

internal val MultiFormatTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }
    val cbor = Cbor {
        ignoreUnknownKeys = true
        encodeObjectTags = true
    }

    "format sets compose flat through a shared format" {
        val jsonCbor = objectFormats(JsonObjectFormat, CborMapFormat)
        val thirdFormat = object : ObjectFormatAdapter by JsonObjectFormat {
            override val id: String = "third"
            override fun supports(serialFormat: SerialFormat): Boolean = false
            override fun supports(decoder: Decoder): Boolean = false
            override fun supports(encoder: Encoder): Boolean = false
        }
        val cborThird = objectFormats(CborMapFormat, thirdFormat)

        (jsonCbor + cborThird).size shouldBe 3

        val conflictingCbor = object : ObjectFormatAdapter by CborMapFormat {}
        shouldThrow<IllegalArgumentException> {
            jsonCbor + objectFormats(conflictingCbor)
        }
    }

    "construct the specialized header as flat JSON" {
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

    "construct the specialized header as a flat COSE map" {
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
        cbor.encodeToCborElement(header) shouldBe expected
    }

    "copy the base header into the downstream specialization in either format" - {
        "JSON" {
            val base = with(json) { XoseHeader("ES256", "JWT") }
            val copied = JsonObject(
                base.backingObject.map { (key, value) ->
                    key as String to value as JsonElement
                }.toMap() + ("number_of_chickens" to JsonPrimitive(42))
            )
            val gromit = json.decodeFromJsonElement<GromitAuthenticationHeader>(copied)

            gromit.numberOfChickens shouldBe 42
            json.encodeToJsonElement(gromit) shouldBe copied
        }

        "CBOR" {
            val base = with(cbor) { XoseHeader("ES256", "JWT") }
            val unknownComplexKey = CborMap(
                mapOf(CborString("wallace") to CborString("gromit"))
            )
            val copied = CborMap(
                base.backingObject.map { (key, value) ->
                    key as CborElement to value as CborElement
                }.toMap() + mapOf(
                    CborInteger(-65_537L) to CborInteger(42L),
                    unknownComplexKey to CborString("unknown but preserved"),
                ),
                listOf(100u),
            )
            val gromit = cbor.decodeFromCborElement<GromitAuthenticationHeader>(copied)

            gromit.numberOfChickens shouldBe 42
            cbor.encodeToCborElement(gromit) shouldBe copied
        }
    }

    "both formats reject a missing mandatory chicken count" {
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
