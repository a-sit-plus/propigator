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
    val id: Int by jsonProperty()
    val name: String by jsonProperty(defaultValue = "Bob")
    val isCool: Boolean? by jsonProperty("is_cool")
    val fancyString: String by jsonProperty("fancy", defaultValue = "Fancy", serializer = FancySerializer)
    val addressData: AddressData? by jsonSlice()

    constructor(id: Int, name: String? = "Bob", isCool: Boolean?, fancyString: String, serialFormat: Json) : this(
        JsonObject(
            mapOf(
                //All keys must be manually supplied
                "id" to serialFormat.encodeToJsonElement(id),
                "name" to serialFormat.encodeToJsonElement(name), //TODO missing default value logic
                "isCool" to serialFormat.encodeToJsonElement(isCool),
                "fancy" to serialFormat.encodeToJsonElement(
                    FancySerializer,
                    fancyString
                ) //TODO custom serializer must be manually added
                //TODO how do add [PersonObject.externalParameter]? Make externalParameters read-Only??
            )
        ), serialFormat
    )

    constructor(personData: PersonData, addressData: AddressData?, serialFormat: Json) : this(
        serialFormat.encodeToJsonElement(personData).jsonObject.strictUnion(
            serialFormat.encodeToJsonElement(addressData).jsonObject
        )
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
            person.name shouldBe "Bob"
            person.rawObject.keys.shouldNotContain("name")
        }

        "Default values are correctly encoded" {
            val person = PersonObject()
        }
    }
}
