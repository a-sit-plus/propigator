package at.asitplus.propigator.json

import at.asitplus.propigator.common.NullWriteMode
import at.asitplus.propigator.common.ObjectBackedValidated
import at.asitplus.testballoon.invoke
import at.asitplus.testballoon.minus
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

@Serializable
private class Foo(
    val bar: Int,
    val baz: String
)

@Serializable(with = PersonJsonObject.Serializer::class)
private class PersonJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, JsonBackingCodec(json)), ObjectBackedValidated {
    var id: String by jsonProperty()
    var name: String by jsonProperty()
    var renamed: Boolean by jsonProperty("some_json_key")
    val readOnlyName: String by jsonProperty("name")
    val foo: Foo by jsonProperty("foo")

    override fun validate() {
        id
        name
        foo
    }

    object Serializer : KSerializer<PersonJsonObject> by JsonObjectBackedSerializer(::PersonJsonObject)
}

private var PersonJsonObject.nickname: String? by nullableJsonProperty("nick", NullWriteMode.REMOVE_KEY)
private var PersonJsonObject.middleName: String? by nullableJsonProperty("middle", NullWriteMode.STORE_NULL)
private val PersonJsonObject.readOnlyNickname: String? by nullableJsonProperty("nick")
//private val PersonJsonObject.foo: Foo by jsonProperty("foo")

internal val JsonObjectBackedTest by testSuite {
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

            decoded.id shouldBe "p-1"
            decoded.name shouldBe "Ada"
            decoded.renamed shouldBe true
            decoded.foo.shouldBeInstanceOf<Foo>()
            decoded.foo.bar shouldBe 2
            decoded.foo.baz shouldBe "eyz"
            decoded.rawObject["unknown"]!!.jsonPrimitive.content shouldBe "42"

            decoded.name = "Grace"
            decoded.nickname = "amazing"

            val encoded = json.encodeToString(PersonJsonObject.serializer(), decoded)
            val reparsed = json.decodeFromString(PersonJsonObject.serializer(), encoded)
            reparsed.name shouldBe "Grace"
            reparsed.nickname shouldBe "amazing"
            reparsed.rawObject["unknown"]!!.jsonPrimitive.content shouldBe "42"
            reparsed.foo.shouldBeInstanceOf<Foo>()
        }

        "read member and extension val delegates from the backing object" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
                put("nick", "countess")
            })

            obj.readOnlyName shouldBe "Ada"
            obj.readOnlyNickname shouldBe "countess"
        }

        "remove nullable keys when configured with REMOVE_KEY mode" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
            })
            obj.nickname = "x"
            obj.nickname shouldBe "x"
            obj.nickname = null
            obj.rawObject["nick"] shouldBe null
        }

        "store explicit null for one property while removing another on the same object" {
            val obj = PersonJsonObject(buildJsonObject {
                put("id", "p-1")
                put("name", "Ada")
                put("some_json_key", true)
                put("middle", "Byron")
                put("nick", "countess")
            })

            obj.middleName = null
            obj.nickname = null

            obj.rawObject["middle"] shouldBe JsonNull
            obj.rawObject["nick"] shouldBe null
        }

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<SerializationException> {
                Json.decodeFromString(PersonJsonObject.serializer(), """{"id":"p-1"}""")
            }
        }
    }
}
