// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedValidated
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class JsonObjectBackedSerializer<T : JsonObjectBacked>(
    private val create: (JsonObject, Json) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        return create(input.decodeJsonElement().jsonObject, input.json).also {
            (it as? ObjectBackedValidated)?.validate()
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as? JsonEncoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        output.encodeJsonElement(value.rawObject)
    }
}
