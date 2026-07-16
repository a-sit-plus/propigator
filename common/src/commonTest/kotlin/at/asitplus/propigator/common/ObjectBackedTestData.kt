package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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