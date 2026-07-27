package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.validating
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive

interface Core : ObjectBacked {
    val aString: String
    val anInt: Int
}

@Serializable(with = CoreObj.Serializer::class)
open class CoreObj protected constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JsonBackedObject(backingObject, serialFormat), Core {

    final override var aString: String by jsonProperty()
        private set

    final override var anInt: Int by jsonProperty()
        private set

    object Serializer : KSerializer<CoreObj> by JsonBackedSerializerTemplate(::CoreObj)

    companion object {
        context(serialFormat: Json)
        operator fun invoke(aString: String, anInt: Int): CoreObj =
            CoreObj(JsonObject(emptyMap()), serialFormat).validating {
                this.aString = aString
                this.anInt = anInt
            }
    }
}

val CoreObj.aFloat: Float by jsonProperty("aFloat")

val CoreTest by matrixSuite {
    val serialFormat = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    "RTT" {
        val created = with(serialFormat) {
            CoreObj("some string", 1337)
        }
        val expected = JsonObject(
            mapOf(
                "aString" to JsonPrimitive("some string"),
                "anInt" to JsonPrimitive(1337),
            )
        )

        created.backingObject shouldBe expected
        serialFormat.encodeToJsonElement(created) shouldBe expected
    }

    "With Additional props" - {
        val floating = JsonObject(
            mapOf(
                "aString" to JsonPrimitive("some string"),
                "anInt" to JsonPrimitive(1337),
                "aFloat" to JsonPrimitive(13.37f),
            )
        )

        "RTT" {
            val deserialized = serialFormat.decodeFromJsonElement<CoreObj>(floating)
            deserialized.backingObject shouldBe floating
            deserialized.aFloat shouldBe floating["aFloat"]!!.jsonPrimitive.float
            serialFormat.encodeToJsonElement(deserialized) shouldBe floating
        }
    }
}
