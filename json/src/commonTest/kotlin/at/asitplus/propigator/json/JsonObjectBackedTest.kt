package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedTestData
import at.asitplus.propigator.common.ObjectBackedTestPerson
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = PersonJsonObject.Serializer::class)
private class PersonJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, json), ObjectBackedTestPerson {
    override val id: String by jsonProperty()
    override val name: String by jsonProperty()
    override val renamed: Boolean by jsonProperty("some_json_key")
    val readOnlyName: String by jsonProperty("name")
    override val foo: ObjectBackedTestPerson.Foo by jsonProperty("foo")

    override fun validate() {
        super<ObjectBackedTestPerson>.validate()
    }

    object Serializer : KSerializer<PersonJsonObject> by JsonObjectBackedSerializer(::PersonJsonObject)
}

private val PersonJsonObject.nickname: String? by jsonProperty("nick")

@Serializable
private data class NestedName(
    val name: String,
)

@Serializable(with = FormatJsonObject.Serializer::class)
private class FormatJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, json) {
    val nested: NestedName by jsonProperty()

    object Serializer : KSerializer<FormatJsonObject> by JsonObjectBackedSerializer(::FormatJsonObject)
}

private val ignoreUnknownJson = Json { ignoreUnknownKeys = true }

private object IgnoreUnknownFormatJsonObjectSerializer :
    KSerializer<FormatJsonObject> by JsonObjectBackedSerializer(::FormatJsonObject, ignoreUnknownJson)

private val encodeDefaultsJson = Json { encodeDefaults = true }

@Serializable(with = DefaultedJsonObject.Serializer::class)
private class DefaultedJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, json) {
    val id: String by jsonProperty()
    val status: String by jsonProperty(defaultValue = "active")
    val customScore: Int by jsonProperty("custom_score", defaultValue = 7, serializer = StringBackedIntSerializer)

    override fun validate() {
        id
        status
        customScore
    }

    object Serializer : KSerializer<DefaultedJsonObject> by JsonObjectBackedSerializer(::DefaultedJsonObject)
}

private object StringBackedIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("StringBackedInt", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Int =
        decoder.decodeString().toInt()

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(value.toString())
    }
}

private object EncodeDefaultsDefaultedJsonObjectSerializer :
    KSerializer<DefaultedJsonObject> by JsonObjectBackedSerializer(::DefaultedJsonObject, encodeDefaultsJson)

internal val JsonObjectBackedTest by matrixSuite {
    "JSON-backed objects" - {
        "round-trip known properties while preserving unknown fields" {
            val json = Json { prettyPrint = false }
            val decoded = json.decodeFromString(
                PersonJsonObject.serializer(),
                """{"id":"p-1",
                    "name":"Ada",
                    "some_json_key":true,
                    "unknown":42,
                    "foo": {
                        "bar": 2,
                        "baz": "eyz"
                    }
                    }""".trimIndent(),
            )

            decoded.id shouldBe ObjectBackedTestData.id
            decoded.name shouldBe ObjectBackedTestData.name
            decoded.renamed shouldBe ObjectBackedTestData.renamed
            decoded.foo shouldBe ObjectBackedTestData.foo
            decoded.backingObject["unknown"]!!.jsonPrimitive.content shouldBe "42"

            val encoded = json.encodeToString(PersonJsonObject.serializer(), decoded)
            val reparsed = json.decodeFromString(PersonJsonObject.serializer(), encoded)
            reparsed.name shouldBe ObjectBackedTestData.name
            reparsed.backingObject["unknown"]!!.jsonPrimitive.content shouldBe "42"
            reparsed.foo shouldBe ObjectBackedTestData.foo
        }

        "read member and extension val delegates from the backing object" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
                put("nick", "countess")
            })

            obj.readOnlyName shouldBe "Ada"
            obj.nickname shouldBe "countess"
        }

        "decode delegated properties with the serializer construction Json by default" {
            val payload = """{"nested":{"name":"Ada","unknown":true}}"""
            val decodedWithDefaultFormat = ignoreUnknownJson.decodeFromString(
                FormatJsonObject.serializer(),
                payload,
            )

            shouldThrow<SerializationException> {
                decodedWithDefaultFormat.nested
            }

            val decodedWithCustomFormat = Json.decodeFromString(
                IgnoreUnknownFormatJsonObjectSerializer,
                payload,
            )

            decodedWithCustomFormat.nested shouldBe NestedName("Ada")
        }

        "allow serialization with an equivalent Json configuration" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
            })

            val encoded = Json.encodeToString(PersonJsonObject.serializer(), obj)

            Json.parseToJsonElement(encoded).jsonObject["id"]!!.jsonPrimitive.content shouldBe "p-1"
        }

        "reject serialization with a mismatching Json configuration" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
            })

            shouldThrow<IllegalArgumentException> {
                Json { prettyPrint = true }.encodeToString(PersonJsonObject.serializer(), obj)
            }
        }

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<SerializationException> {
                Json.decodeFromString(PersonJsonObject.serializer(), """{"id":"p-1"}""")
            }
        }

        "read missing defaulted delegated properties from their defaults" {
            val decoded = Json.decodeFromString(
                DefaultedJsonObject.serializer(),
                """{"id":"p-1"}""",
            )

            decoded.status shouldBe "active"
            decoded.customScore shouldBe 7
        }

        "decode present defaulted delegated properties from the backing object" {
            val decoded = Json.decodeFromString(
                DefaultedJsonObject.serializer(),
                """{"id":"p-1","status":"inactive","custom_score":"9"}""",
            )

            decoded.status shouldBe "inactive"
            decoded.customScore shouldBe 9
        }

        "encode defaulted delegated properties from the raw backing object only" {
            val jsonWithoutDefaults = Json.Default
            val decodedWithoutDefaults = jsonWithoutDefaults.decodeFromString(
                DefaultedJsonObject.serializer(),
                """{"id":"p-1","unknown":42}""",
            )
            val encodedWithoutDefaults = jsonWithoutDefaults.encodeToString(
                DefaultedJsonObject.serializer(),
                decodedWithoutDefaults,
            )
            val reparsedWithoutDefaults = Json.parseToJsonElement(encodedWithoutDefaults).jsonObject

            decodedWithoutDefaults.status shouldBe "active"
            decodedWithoutDefaults.customScore shouldBe 7
            reparsedWithoutDefaults["status"] shouldBe null
            reparsedWithoutDefaults["custom_score"] shouldBe null
            reparsedWithoutDefaults["unknown"]!!.jsonPrimitive.content shouldBe "42"

            val decodedWithDefaults = encodeDefaultsJson.decodeFromString(
                EncodeDefaultsDefaultedJsonObjectSerializer,
                """{"id":"p-1","status":"active","custom_score":"7","unknown":42}""",
            )
            val encodedWithDefaults = encodeDefaultsJson.encodeToString(
                EncodeDefaultsDefaultedJsonObjectSerializer,
                decodedWithDefaults,
            )
            val reparsedWithDefaults = Json.parseToJsonElement(encodedWithDefaults).jsonObject

            reparsedWithDefaults["status"]!!.jsonPrimitive.content shouldBe "active"
            reparsedWithDefaults["custom_score"]!!.jsonPrimitive.content shouldBe "7"
            reparsedWithDefaults["unknown"]!!.jsonPrimitive.content shouldBe "42"
        }
    }
}
