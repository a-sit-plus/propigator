package at.asitplus.propigator.json

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
private data class AddressData(
    val street: String,
    val city: String,
    val zipCode: Int,
)

object FancySerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor
        get() = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value.reversed())
    }

    override fun deserialize(decoder: Decoder): String {
        return decoder.decodeString().reversed()
    }
}

private val PersonObject.externalParameter: String by jsonProperty("didntseeitcoming")

@Serializable
private data class PersonData(
    val id: Int,
    val name: String = "Bob",

    @SerialName("is_cool")
    val isCool: Boolean,

    @SerialName("fancy")
    @Serializable(with = FancySerializer::class)
    val fancyString: String = "Fancy",
)

@Serializable(with = PersonObject.Serializer::class)
private class PersonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, json) {
    val personData: PersonData by jsonSlice()
    val addressData: AddressData? by jsonSlice()

    constructor(personData: PersonData, addressData: AddressData? = null, serialFormat: Json = Json.Default) : this(
        raw = serialFormat.encodeToJsonElement(personData).jsonObject.strictUnion(
            addressData?.let { serialFormat.encodeToJsonElement(it).jsonObject }
        ),
        json = serialFormat,
    )

    object Serializer : KSerializer<PersonObject> by JsonObjectBackedSerializer(::PersonObject)
}

private val encodeDefaultsJson = Json { encodeDefaults = true }
private val ignoreDefaultsJson = Json { encodeDefaults = false }

/** This is Bob */
val encodedWithoutDefaults = buildJsonObject {
    put("id", 1)
    put("is_cool", true)
}

/**
 * Test for compatability with kotlinx.serialization.
 *
 * [JsonConfiguration.encodeDefaults] only affects encoding but not decoding.
 * When decoding, irrespective of the serializer setting, default values need to be present in the class
 * but not in the raw object (if they were absent)
 */
internal val EncodeDefaultsTest by matrixSuite {
    listOf(encodeDefaultsJson, ignoreDefaultsJson).asData(
        nameFn = { "encodeDefaults = ${it.configuration.encodeDefaults}" }
    ) - { serializer ->
        "Default values are being honored during decoding" {
            val person = serializer.decodeFromJsonElement<PersonObject>(encodedWithoutDefaults)
            person.personData.name shouldBe "Bob"
            person.rawObject.keys.shouldNotContain("name")
        }

        "Default values are correctly encoded" {
            val person = PersonObject(
                personData = PersonData(
                    id = 5,
                    isCool = true
                ),
                serialFormat = serializer
            )
            val encoded = serializer.encodeToJsonElement(PersonObject.serializer(), person).jsonObject
            val expected = buildJsonObject {
                put("id", 5)
                put("is_cool", true)
                if (serializer.configuration.encodeDefaults) {
                    put("name", "Bob")
                    put("fancy", "ycnaF")
                }
            }

            encoded shouldBe expected
            person.personData shouldBe PersonData(
                id = 5,
                isCool = true,
            )
            person.rawObject.contains("name") shouldBe serializer.configuration.encodeDefaults
            (person.rawObject["fancy"] == JsonPrimitive("ycnaF")) shouldBe serializer.configuration.encodeDefaults
        }
    }
}
