package at.asitplus.propigator.json

import at.asitplus.propigator.common.validating
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable(with = JoseHeader.Companion::class)
open class JoseHeader protected constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JsonBackedObject(backingObject, serialFormat) {

    val algorithm: String by jsonProperty("alg")

    open val type: String? by jsonProperty("typ")

    val keyId: String? by jsonProperty("kid")

    companion object : JsonBackedSerializerTemplate<JoseHeader>(::JoseHeader) {
        context(serialFormat: Json)
        operator fun invoke(
            algorithm: String,
            type: String? = null,
            keyId: String? = null,
        ): JoseHeader = JoseHeader(JsonObject(emptyMap()), serialFormat).validating {
            initBackedProperty(JoseHeader::algorithm, algorithm)
            initBackedProperty(JoseHeader::type, type)
            initBackedProperty(JoseHeader::keyId, keyId)
        }
    }
}

@Serializable(with = GromitAuthenticationHeader.Companion::class)
class GromitAuthenticationHeader private constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JoseHeader(backingObject, serialFormat) {

    override val type: String by jsonProperty("typ")

    val numberOfChickens: Int by jsonProperty("number_of_chickens")

    companion object :
        JsonBackedSerializerTemplate<GromitAuthenticationHeader>(::GromitAuthenticationHeader) {
        context(serialFormat: Json)
        operator fun invoke(
            algorithm: String,
            numberOfChickens: Int,
            type: String,
            keyId: String? = null,
        ): GromitAuthenticationHeader =
            GromitAuthenticationHeader(JsonObject(emptyMap()), serialFormat).validating {
                initBackedProperty(JoseHeader::algorithm, algorithm)
                initBackedProperty(GromitAuthenticationHeader::type, type)
                initBackedProperty(JoseHeader::keyId, keyId)
                initBackedProperty(
                    GromitAuthenticationHeader::numberOfChickens,
                    numberOfChickens,
                )
            }
    }
}

val JoseHeaderApiTest by matrixSuite {
    val json = Json { ignoreUnknownKeys = true }

    "construct a specialized flat JOSE header" {
        val header = with(json) {
            GromitAuthenticationHeader(
                algorithm = "ES256",
                numberOfChickens = 23,
                type = "JWT",
                keyId = "moon-cheese-key",
            )
        }

        header.backingObject shouldBe JsonObject(
            mapOf(
                "alg" to JsonPrimitive("ES256"),
                "typ" to JsonPrimitive("JWT"),
                "kid" to JsonPrimitive("moon-cheese-key"),
                "number_of_chickens" to JsonPrimitive(23),
            )
        )
    }

    "copy a basic header into the downstream specialization" {
        val basic = with(json) {
            JoseHeader(algorithm = "ES256", type = "JWT")
        }
        val copiedBackingObject = JsonObject(
            basic.backingObject + ("number_of_chickens" to JsonPrimitive(42))
        )
        val gromit = json.decodeFromJsonElement<GromitAuthenticationHeader>(copiedBackingObject)

        basic.backingObject shouldNotContainKey "number_of_chickens"
        gromit.algorithm shouldBe "ES256"
        gromit.numberOfChickens shouldBe 42
        json.encodeToJsonElement(gromit) shouldBe copiedBackingObject
    }

    "reject a specialized header that narrows an optional claim to mandatory" {
        val basic = with(json) {
            JoseHeader(algorithm = "ES256")
        }
        val specializedBackingObject = JsonObject(
            basic.backingObject + ("number_of_chickens" to JsonPrimitive(42))
        )

        shouldThrow<NoSuchElementException> {
            json.decodeFromJsonElement<GromitAuthenticationHeader>(specializedBackingObject)
        }
    }
}
