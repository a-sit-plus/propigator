package at.asitplus.propigator.json

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class CoreValue(
    val aString: String,
    val anInt: Int,
    val defaultString: String = "default",
    val nullableDefault: String? = null,
)

val JsonBacked<CoreValue>.aFloat: Float by jsonProperty()
val JsonBacked<CoreValue>.defaultFloat: Float by jsonProperty(defaultValue = 13.37f)

val CoreTest by matrixSuite {
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
    }

    "construct with an ordinary serializable value" {
        val created = JsonBacked(CoreValue("some string", 1337), json)
        val expected = JsonObject(
            mapOf(
                "aString" to JsonPrimitive("some string"),
                "anInt" to JsonPrimitive(1337),
            )
        )

        created.value shouldBe CoreValue("some string", 1337)
        created.backingObject shouldBe expected
        created.defaultFloat shouldBe 13.37f
        json.encodeToJsonElement(created) shouldBe expected
    }

    "retain properties unknown to the carrier" {
        val input = JsonObject(
            mapOf(
                "aString" to JsonPrimitive("some string"),
                "anInt" to JsonPrimitive(1337),
                "aFloat" to JsonPrimitive(13.37f),
                "defaultString" to JsonPrimitive("from wire"),
                "future" to JsonObject(mapOf("untouched" to JsonPrimitive(true))),
            )
        )

        val decoded = json.decodeFromJsonElementBacked<CoreValue>(input)

        decoded.value shouldBe CoreValue("some string", 1337, defaultString = "from wire")
        decoded.aFloat shouldBe 13.37f
        decoded.backingObject shouldBe input
        json.encodeToJsonElementBacked(decoded) shouldBe input
    }

    "use backed string shortcuts" {
        val input = """{"aString":"some string","anInt":1337,"future":true}"""

        val decoded = json.decodeFromStringBacked<CoreValue>(input)

        json.encodeToStringBacked(decoded) shouldBe input
    }
}
