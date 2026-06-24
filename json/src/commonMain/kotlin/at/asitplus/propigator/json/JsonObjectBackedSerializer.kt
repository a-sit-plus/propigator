// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedValidated
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

class JsonObjectBackedSerializer<T : JsonObjectBacked>(
    private val create: (JsonObject, Json) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as? JsonDecoder
            ?: throw SerializationException("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        val jsonObject = decoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("JsonObjectBackedSerializer can only deserialize JSON objects")
        return create(jsonObject, decoder.json).also {
            if (!it.codec.json.configuration.hasSameSettingsAs(decoder.json.configuration)) {
                throw SerializationException(
                    "Created object uses a Json configuration different from the decoder configuration"
                )
            }
            (it as? ObjectBackedValidated)?.validate()
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? JsonEncoder
            ?: throw SerializationException("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        if (!encoder.json.configuration.hasSameSettingsAs(value.codec.json.configuration)) {
            throw SerializationException(
                "Calling serializer uses different Json configuration from the one used to create this object"
            )
        }
        encoder.encodeJsonElement(value.rawObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun JsonConfiguration.hasSameSettingsAs(other: JsonConfiguration): Boolean =
    encodeDefaults == other.encodeDefaults &&
        ignoreUnknownKeys == other.ignoreUnknownKeys &&
        isLenient == other.isLenient &&
        allowStructuredMapKeys == other.allowStructuredMapKeys &&
        prettyPrint == other.prettyPrint &&
        explicitNulls == other.explicitNulls &&
        prettyPrintIndent == other.prettyPrintIndent &&
        coerceInputValues == other.coerceInputValues &&
        useArrayPolymorphism == other.useArrayPolymorphism &&
        classDiscriminator == other.classDiscriminator &&
        allowSpecialFloatingPointValues == other.allowSpecialFloatingPointValues &&
        useAlternativeNames == other.useAlternativeNames &&
        namingStrategy == other.namingStrategy &&
        decodeEnumsCaseInsensitive == other.decodeEnumsCaseInsensitive &&
        allowTrailingComma == other.allowTrailingComma &&
        allowComments == other.allowComments &&
        classDiscriminatorMode == other.classDiscriminatorMode &&
        exceptionsWithDebugInfo == other.exceptionsWithDebugInfo
