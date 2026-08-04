// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.cbor

import at.asitplus.propigator.common.Flattened
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Flattens a serializable carrier's `base` property into its CBOR map. */
open class CborFlatteningSerializerTemplate<T : Flattened<*>>(
    private val generatedSerializer: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = generatedSerializer.descriptor

    private val baseIndex = descriptor.getElementIndex(BASE_PROPERTY).also {
        require(it >= 0) { "Generated serializer must contain a '$BASE_PROPERTY' property" }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? CborEncoder
            ?: error("CborFlatteningSerializerTemplate only works with kotlinx.serialization CBOR")
        val nested = encoder.cbor.encodeToCborElement(generatedSerializer, value) as? CborMap
            ?: throw SerializationException("Generated CBOR must be a map")
        val baseKey = CborString(descriptor.getElementName(baseIndex))
        val base = nested[baseKey] as? CborMap
            ?: throw SerializationException("Generated CBOR is missing '$baseKey'")
        val extension = nested - baseKey
        val collisions = base.keys.intersect(extension.keys)
        if (collisions.isNotEmpty()) {
            throw SerializationException(
                "Base and extending CBOR properties collide: ${collisions.joinToString()}"
            )
        }
        encoder.encodeCborElement(CborMap(base + extension, nested.tags))
    }

    override fun deserialize(decoder: Decoder): T {
        decoder as? CborDecoder
            ?: error("CborFlatteningSerializerTemplate only works with kotlinx.serialization CBOR")
        val flat = decoder.decodeCborElement() as? CborMap
            ?: throw SerializationException("Flattened CBOR must be a map")
        val carrierFlat = CborMap(
            flat.filterKeys { it is CborString || it is CborInteger },
            flat.tags,
        )
        val baseKey = CborString(descriptor.getElementName(baseIndex))
        val nested = CborMap(carrierFlat + (baseKey to carrierFlat), flat.tags)
        val valueFormat = if (decoder.cbor.configuration.ignoreUnknownKeys) {
            decoder.cbor
        } else {
            Cbor(decoder.cbor) { ignoreUnknownKeys = true }
        }
        return valueFormat.decodeFromCborElement(generatedSerializer, nested)
    }

    private companion object {
        const val BASE_PROPERTY = "base"
    }
}
