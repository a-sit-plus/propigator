@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package at.asitplus.propigator.json

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

interface JoseHeader {
    val algorithm: String
    val type: String?
    val keyId: String?
}

interface GromitHeader : JoseHeader {
    val numberOfChickens: Int
}

@Serializable
data class StandardJoseHeader(
    @SerialName("alg")
    override val algorithm: String,
    @SerialName("typ")
    override val type: String? = null,
    @SerialName("kid")
    override val keyId: String? = null,
) : JoseHeader

@KeepGeneratedSerializer
@Serializable(with = GromitAuthenticationHeader.Serializer::class)
data class GromitAuthenticationHeader(
    override val base: StandardJoseHeader,
    @SerialName("number_of_chickens")
    override val numberOfChickens: Int,
) : GromitHeader, JoseHeader by base, JsonFlattened<StandardJoseHeader> {

    @Transient
    override val type: String =
        base.type ?: throw SerializationException("Gromit authentication headers require 'typ'")

    object Serializer :
        JsonFlatteningSerializerTemplate<GromitAuthenticationHeader>(
            GromitAuthenticationHeader.generatedSerializer()
        )
}

@Serializable(with = JsonBackedGromitHeader.Companion::class)
class JsonBackedGromitHeader private constructor(
    backed: JsonBacked<GromitAuthenticationHeader>,
) : JsonBacked<GromitAuthenticationHeader>(backed), GromitHeader by backed.value {

    companion object :
        JsonBackedSerializerTemplate<GromitAuthenticationHeader, JsonBackedGromitHeader>(
            GromitAuthenticationHeader.serializer(),
            ::JsonBackedGromitHeader,
        )
}

@KeepGeneratedSerializer
@Serializable(with = CollidingJsonFlattened.Serializer::class)
private data class CollidingJsonFlattened(
    override val base: StandardJoseHeader,
    @SerialName("alg")
    val extensionAlgorithm: String,
) : JsonFlattened<StandardJoseHeader> {
    object Serializer :
        JsonFlatteningSerializerTemplate<CollidingJsonFlattened>(
            CollidingJsonFlattened.generatedSerializer()
        )
}

val JsonBacked<JoseHeader>.applicationClaim: String? by jsonProperty("application_claim")

val JoseHeaderApiTest by matrixSuite {
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
    }

    "construct a specialized flat JOSE header" {
        val value = GromitAuthenticationHeader(
            base = StandardJoseHeader(
                algorithm = "ES256",
                type = "JWT",
                keyId = "moon-cheese-key",
            ),
            numberOfChickens = 23,
        )
        val header = with(json) { JsonBackedGromitHeader(value) }

        header.value.type shouldBe "JWT"
        header.backingObject shouldNotContainKey "type"
        header.backingObject shouldBe JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "kid" to JsonPrimitive("moon-cheese-key"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )
    }

    "flatten delegation without a JsonBacked envelope" {
        val expected = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )
        val value = GromitAuthenticationHeader(
            base = StandardJoseHeader(algorithm = "ES256", type = "JWT"),
            numberOfChickens = 23,
        )

        json.encodeToJsonElement(value) shouldBe expected
        json.decodeFromJsonElement<GromitAuthenticationHeader>(expected) shouldBe value
    }

    "honor the active JSON naming strategy while flattening" {
        val prefixedJson = Json {
            ignoreUnknownKeys = false
            namingStrategy = JsonNamingStrategy { _, _, serialName -> "x_$serialName" }
        }
        val value = GromitAuthenticationHeader(
            base = StandardJoseHeader(algorithm = "ES256", type = "JWT"),
            numberOfChickens = 23,
        )
        val expected = JsonObject(
            mapOf(
                "x_alg" to JsonPrimitive("ES256"),
                "x_typ" to JsonPrimitive("JWT"),
                "x_number_of_chickens" to JsonPrimitive(23),
            )
        )

        prefixedJson.encodeToJsonElement(value) shouldBe expected
        prefixedJson.decodeFromJsonElement<GromitAuthenticationHeader>(expected) shouldBe value
    }

    "reject colliding base and extension properties" {
        val value = CollidingJsonFlattened(
            base = StandardJoseHeader(algorithm = "ES256"),
            extensionAlgorithm = "ES384",
        )

        shouldThrow<SerializationException> {
            json.encodeToJsonElement(value)
        }
    }

    "retain application-specific claims unknown to the base carrier" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "application_claim" to JsonPrimitive("untouched"),
            )
        )

        val header = json.decodeFromJsonElementBacked<StandardJoseHeader>(input)

        header.value.algorithm shouldBe "ES256"
        header.applicationClaim shouldBe "untouched"
        json.encodeToJsonElementBacked(header) shouldBe input
    }

    "decode a downstream carrier and preserve newer claims" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "number_of_chickens" to JsonPrimitive(42),
                "future_gromit_claim" to JsonPrimitive("still here"),
            )
        )

        val gromit = json.decodeFromJsonElementBacked<GromitAuthenticationHeader>(input)

        gromit.value.algorithm shouldBe "ES256"
        gromit.value.type shouldBe "JWT"
        gromit.value.numberOfChickens shouldBe 42
        json.encodeToJsonElementBacked(gromit) shouldBe input
    }

    "expose a backed carrier's semantic interface directly" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "number_of_chickens" to JsonPrimitive(42),
                "future_gromit_claim" to JsonPrimitive("still here"),
                "application_claim" to JsonPrimitive("also direct"),
            )
        )

        val gromit = json.decodeFromJsonElement<JsonBackedGromitHeader>(input)

        gromit.algorithm shouldBe "ES256"
        gromit.type shouldBe "JWT"
        gromit.numberOfChickens shouldBe 42
        gromit.applicationClaim shouldBe "also direct"
        json.encodeToJsonElement(gromit) shouldBe input
    }

    "reject a specialized carrier without its required narrowed claim" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "number_of_chickens" to JsonPrimitive(42),
            )
        )

        shouldThrow<SerializationException> {
            json.decodeFromJsonElementBacked<GromitAuthenticationHeader>(input)
        }
    }

    "base construction does not acquire downstream claims" {
        val basic = with(json) {
            JsonBacked(StandardJoseHeader(algorithm = "ES256"))
        }
        basic.backingObject shouldNotContainKey "number_of_chickens"
    }
}
