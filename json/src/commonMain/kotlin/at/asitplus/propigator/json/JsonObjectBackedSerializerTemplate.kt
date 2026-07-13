// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

class JsonObjectBackedSerializerTemplate<T : JsonObjectBacked>(
    private val create: (JsonObject, Json) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as? JsonDecoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        return create(decoder.decodeJsonElement().jsonObject, decoder.json).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? JsonEncoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        require(encoder.json.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching JsonConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeJsonElement(value.backingObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun Json.hasSameConfigurationAs(other: Json): Boolean {
    val left = configuration
    val right = other.configuration
    return left.encodeDefaults == right.encodeDefaults &&
            left.ignoreUnknownKeys == right.ignoreUnknownKeys &&
            left.isLenient == right.isLenient &&
            left.allowStructuredMapKeys == right.allowStructuredMapKeys &&
            left.prettyPrint == right.prettyPrint &&
            left.explicitNulls == right.explicitNulls &&
            left.prettyPrintIndent == right.prettyPrintIndent &&
            left.coerceInputValues == right.coerceInputValues &&
            left.useArrayPolymorphism == right.useArrayPolymorphism &&
            left.classDiscriminator == right.classDiscriminator &&
            left.allowSpecialFloatingPointValues == right.allowSpecialFloatingPointValues &&
            left.useAlternativeNames == right.useAlternativeNames &&
            left.namingStrategy == right.namingStrategy &&
            left.decodeEnumsCaseInsensitive == right.decodeEnumsCaseInsensitive &&
            left.allowTrailingComma == right.allowTrailingComma &&
            left.allowComments == right.allowComments &&
            left.classDiscriminatorMode == right.classDiscriminatorMode &&
            left.exceptionsWithDebugInfo == right.exceptionsWithDebugInfo
}
