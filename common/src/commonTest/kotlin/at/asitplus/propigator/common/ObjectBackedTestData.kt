@file:Suppress("LESS_VISIBLE_TYPE_IN_INLINE_ACCESSED_SIGNATURE_WARNING")

package at.asitplus.propigator.common

import at.asitplus.propigator.cbor.CborBacked
import at.asitplus.propigator.cbor.CborBackedSerializerTemplate
import at.asitplus.propigator.json.JsonBacked
import at.asitplus.propigator.json.JsonBackedSerializerTemplate
import at.asitplus.propigator.json.strictUnion
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.typeOf

@Serializable
data class AddressData(
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

@Serializable
data class PersonData(
    val id: Int,
    val name: String = "Bob",

    @SerialName("is_cool")
    val isCool: Boolean,

    @SerialName("fancy")
    @Serializable(with = FancySerializer::class)
    val fancyString: String = "Fancy",
)

abstract class Person : ObjectBacked {
    val personData: PersonData by slice()
    val addressData: AddressData? by slice()

    override fun validate() {
        personData
    }
}

@Serializable(with = PersonJson.Serializer::class)
class PersonJson private constructor(
    override val backingObject: JsonObject,
    override val serialFormat: Json = Json.Default,
) : Person(), JsonBacked {

    constructor(personData: PersonData, addressData: AddressData, json: Json) :
            this(
                backingObject = json.encodeToJsonElement(personData).jsonObject
                    .strictUnion(json.encodeToJsonElement(addressData).jsonObject),
                serialFormat = json
            )

    object Serializer : KSerializer<PersonJson> by JsonBackedSerializerTemplate(::PersonJson)
}

@Serializable(with = PersonCbor.Serializer::class)
class PersonCbor private constructor(
    override val backingObject: CborMap,
    override val serialFormat: Cbor = Cbor.Default,
) : Person(), CborBacked {
    object Serializer : KSerializer<PersonCbor> by CborBackedSerializerTemplate(::PersonCbor)
}

@Serializable
data class RegisterEntry<P : Person>(
    val person: P? = null, // Problem: Cannot define default Person parameters without specifying format
    val someField: String,
)

internal val expectedPersonData = PersonData(
    id = 42,
    name = "Ada",
    isCool = true,
    fancyString = "Fancy",
)

internal val expectedAddressData = AddressData(
    street = "Main Street 1",
    city = "Vienna",
    zipCode = 1010,
)

@Serializable
private class ExpectedPayload(
    val person: PersonData = expectedPersonData,
    val someField: String = "register-value",
)

private val payload = ExpectedPayload()

@Suppress("LESS_VISIBLE_TYPE_ACCESS_IN_INLINE_WARNING")
internal inline fun <reified T> getExpectedPayload(serialFormat: SerialFormat): T = when (serialFormat) {
    is Json if typeOf<T>() == typeOf<String>() -> serialFormat.encodeToString(payload) as T
    is Cbor if typeOf<T>() == typeOf<ByteArray>() -> serialFormat.encodeToByteArray(payload) as T

    is Json -> serialFormat.encodeToJsonElement(payload) as T
    is Cbor -> serialFormat.encodeToCborElement(payload) as T
    else -> throw Exception("Unsupported serialization format")
}