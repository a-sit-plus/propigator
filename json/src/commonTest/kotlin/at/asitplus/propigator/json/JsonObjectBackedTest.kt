package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedTestData
import at.asitplus.propigator.common.ObjectBackedTestPerson
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = PersonJson.Serializer::class)
private class PersonJson(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonBacked(raw, json), ObjectBackedTestPerson {
    override val id: String by jsonProperty()
    override val name: String by jsonProperty()
    override val renamed: Boolean by jsonProperty("some_json_key")
    val readOnlyName: String by jsonProperty("name")
    override val foo: ObjectBackedTestPerson.Foo by jsonProperty("foo")

    override fun validate() {
        super<ObjectBackedTestPerson>.validate()
    }

    object Serializer : KSerializer<PersonJson> by JsonBackedSerializerTemplate(::PersonJson)
}

private val PersonJson.nickname: String? by jsonProperty("nick")

private val encodeDefaultsJson = Json { encodeDefaults = true }

@Serializable(with = DefaultedJson.Serializer::class)
private class DefaultedJson(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonBacked(raw, json) {
    val id: String by jsonProperty()
    val status: String by jsonProperty(defaultValue = "active")
    val customScore: Int by jsonProperty("custom_score", defaultValue = 7, serializer = StringBackedIntSerializer)

    override fun validate() {
        id
        status
        customScore
    }

    object Serializer : KSerializer<DefaultedJson> by JsonBackedSerializerTemplate(::DefaultedJson)
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
    KSerializer<DefaultedJson> by JsonBackedSerializerTemplate(::DefaultedJson)

internal val JsonObjectBackedTest by matrixSuite {
    "JSON-backed objects" - {
        "round-trip known properties while preserving unknown fields" {
            val json = Json { prettyPrint = false }
            val decoded = json.decodeFromString(
                PersonJson.serializer(),
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

            val encoded = json.encodeToString(PersonJson.serializer(), decoded)
            val reparsed = json.decodeFromString(PersonJson.serializer(), encoded)
            reparsed.name shouldBe ObjectBackedTestData.name
            reparsed.backingObject["unknown"]!!.jsonPrimitive.content shouldBe "42"
            reparsed.foo shouldBe ObjectBackedTestData.foo
        }

        "read member and extension val delegates from the backing object" {
            val obj = PersonJson(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
                put("nick", "countess")
            })

            obj.readOnlyName shouldBe "Ada"
            obj.nickname shouldBe "countess"
        }

        "allow serialization with an equivalent Json configuration" {
            val obj = PersonJson(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
            })

            val encoded = Json.encodeToString(PersonJson.serializer(), obj)

            Json.parseToJsonElement(encoded).jsonObject["id"]!!.jsonPrimitive.content shouldBe "p-1"
        }

        "reject serialization with a mismatching Json configuration" {
            val obj = PersonJson(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
            })

            shouldThrow<IllegalArgumentException> {
                Json { prettyPrint = true }.encodeToString(PersonJson.serializer(), obj)
            }
        }

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<NoSuchElementException> {
                Json.decodeFromString(PersonJson.serializer(), """{"id":"p-1"}""")
            }
        }

        "read missing defaulted delegated properties from their defaults" {
            val decoded = Json.decodeFromString(
                DefaultedJson.serializer(),
                """{"id":"p-1"}""",
            )

            decoded.status shouldBe "active"
            decoded.customScore shouldBe 7
        }

        "decode present defaulted delegated properties from the backing object" {
            val decoded = Json.decodeFromString(
                DefaultedJson.serializer(),
                """{"id":"p-1","status":"inactive","custom_score":"9"}""",
            )

            decoded.status shouldBe "inactive"
            decoded.customScore shouldBe 9
        }

        "encode defaulted delegated properties from the raw backing object only" {
            val jsonWithoutDefaults = Json.Default
            val decodedWithoutDefaults = jsonWithoutDefaults.decodeFromString(
                DefaultedJson.serializer(),
                """{"id":"p-1","unknown":42}""",
            )
            val encodedWithoutDefaults = jsonWithoutDefaults.encodeToString(
                DefaultedJson.serializer(),
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
