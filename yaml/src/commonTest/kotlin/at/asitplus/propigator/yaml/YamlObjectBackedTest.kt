package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.ObjectBackedTestData
import at.asitplus.propigator.common.ObjectBackedTestPerson
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap
import net.mamoe.yamlkt.YamlPrimitive

@Serializable(with = PersonYamlObject.Serializer::class)
private class PersonYamlObject(
    raw: YamlMap,
    yaml: Yaml = Yaml.Default,
) : YamlObjectBacked(raw, YamlBackingCodec(yaml)), ObjectBackedTestPerson {
    override val id: String by yamlProperty()
    override val name: String by yamlProperty()
    override val renamed: Boolean by yamlProperty("some_yaml_key")
    val readOnlyName: String by yamlProperty("name")
    override val foo: ObjectBackedTestPerson.Foo by yamlProperty("foo")

    object Serializer : KSerializer<PersonYamlObject> by YamlObjectBackedSerializer(create = ::PersonYamlObject)
}

private val PersonYamlObject.nickname: String? by yamlProperty("nick")

internal val YamlObjectBackedTest by matrixSuite {
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

            val encoded = Yaml.encodeToString(PersonYamlObject.serializer(), decoded)
            val reparsed = Yaml.decodeFromString(PersonYamlObject.serializer(), encoded)
            reparsed.name shouldBe ObjectBackedTestData.name
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
            obj.nickname shouldBe "countess"
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

        "reject payloads missing mandatory delegated properties" {
            shouldThrow<SerializationException> {
                Yaml.decodeFromString(PersonYamlObject.serializer(), "id: p-1")
            }
        }
    }
}
