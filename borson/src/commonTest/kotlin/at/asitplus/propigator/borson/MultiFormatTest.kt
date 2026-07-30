@file:OptIn(
    at.asitplus.propigator.multi.ExperimentalMultiFormatApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package at.asitplus.propigator.borson

import at.asitplus.propigator.common.Flattened
import at.asitplus.propigator.multi.MultiFormatBacked
import at.asitplus.propigator.multi.ObjectFormatAdapter
import at.asitplus.propigator.multi.multiFormatProperty
import at.asitplus.propigator.multi.objectFormats
import at.asitplus.propigator.multi.propertyKey
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborLabel
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

interface XoseHeader {
    val algorithm: String
    val type: String?
    val keyId: String?
    val defaultIssuer: String
}

@Serializable
data class StandardXoseHeader(
    @SerialName("alg")
    @CborLabel(1)
    override val algorithm: String,
    @SerialName("typ")
    @CborLabel(-65_538)
    override val type: String? = null,
    @SerialName("kid")
    @CborLabel(4)
    override val keyId: String? = null,
    @SerialName("iss")
    @CborLabel(-65_539)
    override val defaultIssuer: String = "Wallace",
) : XoseHeader

interface GromitHeader : XoseHeader {
    val numberOfChickens: Int
}

@KeepGeneratedSerializer
@Serializable(with = GromitAuthenticationHeader.Serializer::class)
data class GromitAuthenticationHeader(
    override val base: StandardXoseHeader,
    @SerialName("number_of_chickens")
    @CborLabel(-65_537)
    override val numberOfChickens: Int,
) : GromitHeader, XoseHeader by base, Flattened<StandardXoseHeader> {
    object Serializer :
        BorsonFlatteningSerializerTemplate<GromitAuthenticationHeader>(
            generatedSerializer()
        )
}

@Serializable(with = BorsonBackedGromitHeader.Serializer::class)
class BorsonBackedGromitHeader private constructor(
    backed: BorsonBacked<GromitAuthenticationHeader>,
) : BorsonBacked<GromitAuthenticationHeader>(backed), GromitHeader by backed.value {

    constructor(
        value: GromitAuthenticationHeader,
        serialFormat: SerialFormat,
    ) : this(BorsonBacked(value, serialFormat))

    object Serializer :
        BorsonBackedSerializerTemplate<GromitAuthenticationHeader, BorsonBackedGromitHeader>(
            GromitAuthenticationHeader.serializer(),
            ::BorsonBackedGromitHeader,
        )
}

val MultiFormatBacked<XoseHeader>.applicationClaim: String? by multiFormatProperty<String?>(
    JsonObjectFormat propertyKey "application_claim",
    CborMapFormat propertyKey CborInteger(-70_000L),
)

internal val MultiFormatTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = false }
    val cbor = Cbor {
        ignoreUnknownKeys = false
        encodeObjectTags = true
        preferCborLabelsOverNames = true
    }

    "format sets compose flat through a shared format" {
        val jsonCbor = objectFormats(JsonObjectFormat, CborMapFormat)
        val thirdFormat = object : ObjectFormatAdapter by JsonObjectFormat {
            override fun supports(serialFormat: SerialFormat): Boolean = false
            override fun supports(decoder: Decoder): Boolean = false
            override fun supports(encoder: Encoder): Boolean = false
        }
        val cborThird = objectFormats(CborMapFormat, thirdFormat)

        (jsonCbor + cborThird).adapters.size shouldBe 3
        (jsonCbor + objectFormats(CborMapFormat)).adapters.size shouldBe 2
    }

    "construct the specialized header as flat JSON" {
        val header = BorsonBackedGromitHeader(
            GromitAuthenticationHeader(
                StandardXoseHeader("ES256", "JWT", "moon-cheese-key"),
                23,
            ),
            json,
        )
        val expected = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "kid" to JsonPrimitive("moon-cheese-key"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )

        header.algorithm shouldBe "ES256"
        header.numberOfChickens shouldBe 23
        json.encodeToJsonElement(header) shouldBe expected
    }

    "construct the specialized header as a flat COSE map" {
        val header = BorsonBackedGromitHeader(
            GromitAuthenticationHeader(
                StandardXoseHeader("ES256", "JWT", "moon-cheese-key"),
                23,
            ),
            cbor,
        )
        val expected = CborMap(
            mapOf(
                CborInteger(1L) to CborString("ES256"),
                CborInteger(-65_538L) to CborString("JWT"),
                CborInteger(4L) to CborString("moon-cheese-key"),
                CborInteger(-65_537L) to CborInteger(23L),
            )
        )

        header.algorithm shouldBe "ES256"
        header.numberOfChickens shouldBe 23
        cbor.encodeToCborElement(header) shouldBe expected
    }

    "preserve unknown JSON claims through a concrete backed carrier" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "number_of_chickens" to JsonPrimitive(42),
                "application_claim" to JsonPrimitive("direct"),
                "future" to JsonPrimitive(true),
            )
        )

        val header = json.decodeFromJsonElement<BorsonBackedGromitHeader>(input)

        header.algorithm shouldBe "ES256"
        header.numberOfChickens shouldBe 42
        header.applicationClaim shouldBe "direct"
        json.encodeToJsonElement(header) shouldBe input
    }

    "preserve unknown native CBOR keys and tags" {
        val complexKey = CborMap(mapOf(CborString("kind") to CborString("future")))
        val input = CborMap(
            mapOf(
                CborInteger(1L) to CborString("ES256"),
                CborInteger(-65_537L) to CborInteger(42L),
                CborInteger(-70_000L) to CborString("direct"),
                complexKey to CborString("preserved"),
            ),
            listOf(100u),
        )

        val header = cbor.decodeFromCborElement<BorsonBackedGromitHeader>(input)

        header.algorithm shouldBe "ES256"
        header.numberOfChickens shouldBe 42
        header.applicationClaim shouldBe "direct"
        cbor.encodeToCborElement(header) shouldBe input
    }

    "use generic borson string and byte-array shortcuts" {
        val jsonInput = """{"alg":"ES256","number_of_chickens":42,"future":true}"""
        val jsonBacked =
            json.decodeFromStringBorson<GromitAuthenticationHeader>(jsonInput)

        json.encodeToStringBorson(jsonBacked) shouldBe jsonInput

        val cborInput = CborMap(
            mapOf(
                CborInteger(1L) to CborString("ES256"),
                CborInteger(-65_537L) to CborInteger(42L),
            )
        )
        val cborBacked =
            cbor.decodeFromCborElementBorson<GromitAuthenticationHeader>(cborInput)

        cbor.encodeToCborElementBorson(cborBacked) shouldBe cborInput
        cbor.decodeFromByteArrayBorson<GromitAuthenticationHeader>(
            cbor.encodeToByteArrayBorson(cborBacked)
        ).value shouldBe cborBacked.value
    }

    "reject a specialized carrier without its required claim in either format" {
        shouldThrow<SerializationException> {
            json.decodeFromJsonElementBorson<GromitAuthenticationHeader>(
                JsonObject(mapOf("alg" to JsonPrimitive("ES256")))
            )
        }
        shouldThrow<SerializationException> {
            cbor.decodeFromCborElementBorson<GromitAuthenticationHeader>(
                CborMap(mapOf(CborInteger(1L) to CborString("ES256")))
            )
        }
    }
}
