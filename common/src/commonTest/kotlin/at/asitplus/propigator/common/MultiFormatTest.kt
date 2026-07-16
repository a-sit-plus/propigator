package at.asitplus.propigator.common

import at.asitplus.propigator.cbor.CborBacked
import at.asitplus.propigator.cbor.CborBackedSerializerTemplate
import at.asitplus.propigator.json.JsonBacked
import at.asitplus.propigator.json.JsonBackedSerializerTemplate
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

abstract class Person : ObjectBacked {
    val personData: PersonData by slice()
    val addressData: AddressData by slice()

    override fun validate() {
        personData
        addressData
    }
}

@Serializable(with = PersonJson.Serializer::class)
private class PersonJson(
    override val backingObject: JsonObject,
    override val serialFormat: Json = Json.Default,
) : Person(), JsonBacked {
    object Serializer : KSerializer<PersonJson> by JsonBackedSerializerTemplate(::PersonJson)
}

@Serializable(with = PersonCbor.Serializer::class)
private class PersonCbor(
    override val backingObject: CborMap,
    override val serialFormat: Cbor = Cbor.Default,
) : Person(), CborBacked {
    object Serializer : KSerializer<PersonCbor> by CborBackedSerializerTemplate(::PersonCbor)
}

@Serializable
data class RegisterEntry<P : Person>(
    val person: P,
    val someField: String,
)

@Serializable
private data class PersonPayload(
    val id: Int,
    val name: String,
    @SerialName("is_cool")
    val isCool: Boolean,
    @SerialName("fancy")
    @Serializable(with = FancySerializer::class)
    val fancyString: String,
    val street: String,
    val city: String,
    val zipCode: Int,
)

@Serializable
private data class RegisterEntryPayload(
    val person: PersonPayload,
    val someField: String,
)

private val expectedPersonData = PersonData(
    id = 42,
    name = "Ada",
    isCool = true,
    fancyString = "Fancy",
)

private val expectedAddressData = AddressData(
    street = "Main Street 1",
    city = "Vienna",
    zipCode = 1010,
)

private val payload = RegisterEntryPayload(
    person = PersonPayload(
        id = expectedPersonData.id,
        name = expectedPersonData.name,
        isCool = expectedPersonData.isCool,
        fancyString = expectedPersonData.fancyString,
        street = expectedAddressData.street,
        city = expectedAddressData.city,
        zipCode = expectedAddressData.zipCode,
    ),
    someField = "register-value",
)

private fun RegisterEntry<out Person>.shouldMatchPayload() {
    person.personData shouldBe expectedPersonData
    person.addressData shouldBe expectedAddressData
    someField shouldBe payload.someField
}

internal val MultiFormatTest by matrixSuite {
    "object-backed values nested in serializable data classes" - {
        "round-trip a JSON-backed person as JSON" {
            val json = Json { ignoreUnknownKeys = true }
            val serializer = RegisterEntry.serializer(PersonJson.serializer())
            val source = json.encodeToString(RegisterEntryPayload.serializer(), payload)

            val decoded = json.decodeFromString(serializer, source)
            decoded.shouldMatchPayload()

            val encoded = json.encodeToString(serializer, decoded)
            json.decodeFromString(serializer, encoded).shouldMatchPayload()
        }

        "round-trip a CBOR-backed person as CBOR" {
            val cbor = Cbor { ignoreUnknownKeys = true }
            val serializer = RegisterEntry.serializer(PersonCbor.serializer())
            val source = cbor.encodeToByteArray(RegisterEntryPayload.serializer(), payload)

            val decoded = cbor.decodeFromByteArray(serializer, source)
            decoded.shouldMatchPayload()

            val encoded = cbor.encodeToByteArray(serializer, decoded)
            cbor.decodeFromByteArray(serializer, encoded).shouldMatchPayload()
        }
    }
}
