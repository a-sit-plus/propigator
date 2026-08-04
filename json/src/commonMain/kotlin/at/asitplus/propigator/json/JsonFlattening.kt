// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.Flattened
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Marks a serializable carrier whose [base] object is flattened into the carrier's JSON object.
 */
typealias JsonFlattened<B> = Flattened<B>

/**
 * Flattens the `base` property produced by a retained generated serializer.
 *
 * Use with `@KeepGeneratedSerializer` so ordinary kotlinx.serialization continues to construct
 * and validate both the base and extending carrier.
 */
@OptIn(ExperimentalSerializationApi::class)
open class JsonFlatteningSerializerTemplate<T : Flattened<*>>(
    private val generatedSerializer: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = generatedSerializer.descriptor

    private val baseIndex = descriptor.getElementIndex(BASE_PROPERTY).also {
        require(it >= 0) { "Generated serializer must contain a '$BASE_PROPERTY' property" }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? JsonEncoder
            ?: error("JsonFlatteningSerializerTemplate only works with kotlinx.serialization JSON")

        val nested = encoder.json
            .encodeToJsonElement(generatedSerializer, value)
            .jsonObject
        val baseKey = baseKey(encoder.json)
        val base = nested[baseKey]?.jsonObject
            ?: throw SerializationException("Generated JSON is missing '$baseKey'")
        val extension = nested - baseKey
        val collisions = base.keys.intersect(extension.keys)
        if (collisions.isNotEmpty()) {
            throw SerializationException(
                "Base and extending JSON properties collide: ${collisions.joinToString()}"
            )
        }

        encoder.encodeJsonElement(JsonObject(base + extension))
    }

    override fun deserialize(decoder: Decoder): T {
        decoder as? JsonDecoder
            ?: error("JsonFlatteningSerializerTemplate only works with kotlinx.serialization JSON")

        val flat = decoder.decodeJsonElement().jsonObject
        val nested = JsonObject(flat + (baseKey(decoder.json) to flat))
        val valueFormat = if (decoder.json.configuration.ignoreUnknownKeys) {
            decoder.json
        } else {
            Json(decoder.json) { ignoreUnknownKeys = true }
        }
        return valueFormat.decodeFromJsonElement(generatedSerializer, nested)
    }

    private fun baseKey(json: Json): String {
        val serialName = descriptor.getElementName(baseIndex)
        return json.configuration.namingStrategy
            ?.serialNameForJson(descriptor, baseIndex, serialName)
            ?: serialName
    }

    private companion object {
        const val BASE_PROPERTY = "base"
    }
}
