package at.asitplus.propigator.json

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

interface JoseHeader {
    val algorithm: String
    val type: String?
    val keyId: String?
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

@Serializable
data class GromitAuthenticationHeader(
    @SerialName("alg")
    override val algorithm: String,
    @SerialName("typ")
    override val type: String,
    @SerialName("kid")
    override val keyId: String? = null,
    @SerialName("number_of_chickens")
    val numberOfChickens: Int,
) : JoseHeader

val JsonBacked<JoseHeader>.applicationClaim: String? by jsonProperty("application_claim")

val JoseHeaderApiTest by matrixSuite {
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
    }

    "construct a specialized flat JOSE header" {
        val header = JsonBacked(
            GromitAuthenticationHeader(
                algorithm = "ES256",
                numberOfChickens = 23,
                type = "JWT",
                keyId = "moon-cheese-key",
            ),
            json,
        )

        header.value.type shouldBe "JWT"
        header.backingObject shouldBe JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "kid" to JsonPrimitive("moon-cheese-key"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )
    }

    "retain application-specific claims unknown to the base carrier" {
        val input = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "application_claim" to JsonPrimitive("untouched"),
            )
        )

        val header = json.decodeFromJsonElement<JsonBacked<StandardJoseHeader>>(input)

        header.value.algorithm shouldBe "ES256"
        header.applicationClaim shouldBe "untouched"
        json.encodeToJsonElement(header) shouldBe input
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

        val gromit =
            json.decodeFromJsonElement<JsonBacked<GromitAuthenticationHeader>>(input)

        gromit.value.algorithm shouldBe "ES256"
        gromit.value.type shouldBe "JWT"
        gromit.value.numberOfChickens shouldBe 42
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
            json.decodeFromJsonElement<JsonBacked<GromitAuthenticationHeader>>(input)
        }
    }

    "base construction does not acquire downstream claims" {
        val basic = JsonBacked(StandardJoseHeader(algorithm = "ES256"), json)
        basic.backingObject shouldNotContainKey "number_of_chickens"
    }
}
