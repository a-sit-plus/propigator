package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.NullWriteMode
import at.asitplus.propigator.common.ObjectBackedTestData
import at.asitplus.propigator.common.ObjectBackedTestPerson
import at.asitplus.testballoon.invoke
import at.asitplus.testballoon.minus
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap
import net.mamoe.yamlkt.YamlNull
import net.mamoe.yamlkt.YamlPrimitive

@Serializable(with = PersonYamlObject.Serializer::class)
private class PersonYamlObject(
    raw: YamlMap,
    yaml: Yaml = Yaml.Default,
) : YamlObjectBacked(raw, YamlBackingCodec(yaml)), ObjectBackedTestPerson {
    override var id: String by yamlProperty()
    override var name: String by yamlProperty()
    override var renamed: Boolean by yamlProperty("some_yaml_key")
    val readOnlyName: String by yamlProperty("name")
    override val foo: ObjectBackedTestPerson.Foo by yamlProperty("foo")

    object Serializer : KSerializer<PersonYamlObject> by YamlObjectBackedSerializer(create = ::PersonYamlObject)
}

private var PersonYamlObject.nickname: String? by nullableYamlProperty("nick", NullWriteMode.REMOVE_KEY)
private var PersonYamlObject.middleName: String? by nullableYamlProperty("middle", NullWriteMode.STORE_NULL)
private val PersonYamlObject.readOnlyNickname: String? by nullableYamlProperty("nick")

internal val YamlObjectBackedTest by testSuite {
    "YAML-backed objects" - {
        "round-trip known properties while preserving unknown fields" {
            val decoded = Yaml.decodeFromString(
                PersonYamlObject.serializer(),
                """
                id: p-1
                name: Ada
                some_yaml_key: true
                unknown: 42
                foo:
                  bar: 2
                  baz: eyz
                """.trimIndent(),
            )

            decoded.id shouldBe ObjectBackedTestData.id
            decoded.name shouldBe ObjectBackedTestData.name
            decoded.renamed shouldBe ObjectBackedTestData.renamed
            decoded.foo shouldBe ObjectBackedTestData.foo
            decoded.rawObject["unknown"]!!.content shouldBe "42"

            decoded.name = "Grace"
            decoded.nickname = "amazing"

            val encoded = Yaml.encodeToString(PersonYamlObject.serializer(), decoded)
            val reparsed = Yaml.decodeFromString(PersonYamlObject.serializer(), encoded)
            reparsed.name shouldBe "Grace"
            reparsed.nickname shouldBe "amazing"
            reparsed.rawObject["unknown"]!!.content shouldBe "42"
            reparsed.foo shouldBe ObjectBackedTestData.foo
        }

        "read member and extension val delegates from the backing object" {
            val obj = PersonYamlObject(
                YamlMap(
                    mapOf(
                        YamlPrimitive("id") to YamlPrimitive("p-1"),
                        YamlPrimitive("name") to YamlPrimitive("Ada"),
                        YamlPrimitive("some_yaml_key") to YamlPrimitive("true"),
                        YamlPrimitive("nick") to YamlPrimitive("countess"),
                    ),
                ),
            )

            obj.readOnlyName shouldBe "Ada"
            obj.readOnlyNickname shouldBe "countess"
        }

        "resolve slice from the backing object" {
            val obj = object : YamlObjectBacked(
                YamlMap(
                    mapOf(
                        YamlPrimitive("bar") to YamlPrimitive("2"),
                        YamlPrimitive("baz") to YamlPrimitive("eyz"),
                    ),
                ),
            ) {
                val foo: ObjectBackedTestPerson.Foo by yamlSlice()
            }

            obj.foo shouldBe ObjectBackedTestData.foo
        }

        "remove nullable keys when configured with REMOVE_KEY mode" {
            val obj = PersonYamlObject(
                YamlMap(
                    mapOf(
                        YamlPrimitive("id") to YamlPrimitive("p-1"),
                        YamlPrimitive("name") to YamlPrimitive("Ada"),
                        YamlPrimitive("some_yaml_key") to YamlPrimitive("true"),
                    ),
                ),
            )
            obj.nickname = "x"
            obj.nickname shouldBe "x"
            obj.nickname = null
            obj.rawObject["nick"] shouldBe null
        }

        "store explicit null for one property while removing another on the same object" {
            val obj = PersonYamlObject(
                YamlMap(
                    mapOf(
                        YamlPrimitive("id") to YamlPrimitive("p-1"),
                        YamlPrimitive("name") to YamlPrimitive("Ada"),
                        YamlPrimitive("some_yaml_key") to YamlPrimitive("true"),
                        YamlPrimitive("middle") to YamlPrimitive("Byron"),
                        YamlPrimitive("nick") to YamlPrimitive("countess"),
                    ),
                ),
            )

            obj.middleName = null
            obj.nickname = null

            obj.rawObject["middle"] shouldBe YamlNull
            obj.rawObject["nick"] shouldBe null
        }

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<SerializationException> {
                Yaml.decodeFromString(PersonYamlObject.serializer(), "id: p-1")
            }
        }
    }
}
