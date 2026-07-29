package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.validating
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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

@Serializable(with = CoreObj.Companion::class)
open class CoreObj protected constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JsonBackedObject(backingObject, serialFormat), Core {

    final override val aString: String by jsonProperty()

    final override val anInt: Int by jsonProperty()

    val defaultString: String by jsonProperty(
        defaultValue = "default",
    )

    val nullableDefault: String? by jsonProperty(
        defaultValue = null,
    )

    companion object : JsonBackedSerializerTemplate<CoreObj>(::CoreObj) {
        context(serialFormat: Json)
        operator fun invoke(aString: String, anInt: Int): CoreObj =
            CoreObj(JsonObject(emptyMap()), serialFormat).validating {
                initBackedProperty(CoreObj::aString, aString)
                initBackedProperty(CoreObj::anInt, anInt)
            }

        context(serialFormat: Json)
        fun initializeAStringTwice() {
            CoreObj(JsonObject(emptyMap()), serialFormat).apply {
                initBackedProperty(CoreObj::aString, "first")
                initBackedProperty(CoreObj::aString, "second")
            }
        }
    }
}

val CoreObj.aFloat: Float by jsonProperty("aFloat")
val CoreObj.defaultFloat: Float by jsonProperty(
    defaultValue = 13.37f,
)

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
        created.defaultString shouldBe "default"
        created.nullableDefault shouldBe null
        created.defaultFloat shouldBe 13.37f
        serialFormat.encodeToJsonElement(created) shouldBe expected
    }

    "backed properties can only be initialized once" {
        shouldThrow<IllegalStateException> {
            with(serialFormat) { CoreObj.initializeAStringTwice() }
        }
    }

    "With Additional props" - {
        val floating = JsonObject(
            mapOf(
                "aString" to JsonPrimitive("some string"),
                "anInt" to JsonPrimitive(1337),
                "aFloat" to JsonPrimitive(13.37f),
                "defaultString" to JsonPrimitive("from wire"),
            )
        )

        "RTT" {
            val deserialized = serialFormat.decodeFromJsonElement<CoreObj>(floating)
            deserialized.backingObject shouldBe floating
            deserialized.aFloat shouldBe floating["aFloat"]!!.jsonPrimitive.float
            deserialized.defaultString shouldBe "from wire"
            serialFormat.encodeToJsonElement(deserialized) shouldBe floating
        }
    }
}
