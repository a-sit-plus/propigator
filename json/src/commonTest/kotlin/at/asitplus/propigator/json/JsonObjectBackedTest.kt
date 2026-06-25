package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedTestData
import at.asitplus.propigator.common.ObjectBackedTestPerson
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

    object Serializer : KSerializer<PersonJsonObject> by JsonObjectBackedSerializer(::PersonJsonObject)
}

private val PersonJsonObject.nickname: String? by jsonProperty("nick")

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
            decoded.rawObject["unknown"]!!.jsonPrimitive.content shouldBe "42"

            val encoded = json.encodeToString(PersonJsonObject.serializer(), decoded)
            val reparsed = json.decodeFromString(PersonJsonObject.serializer(), encoded)
            reparsed.name shouldBe ObjectBackedTestData.name
            reparsed.rawObject["unknown"]!!.jsonPrimitive.content shouldBe "42"
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

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<SerializationException> {
                Json.decodeFromString(PersonJsonObject.serializer(), """{"id":"p-1"}""")
            }
        }
    }
}
