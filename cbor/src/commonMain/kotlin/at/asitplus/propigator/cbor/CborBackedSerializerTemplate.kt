// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.cbor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class CborBackedSerializerTemplate<T : CborBacked>(
    private val create: (CborMap, Cbor) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = CborMap.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as? CborDecoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        val backingObject = decoder.decodeCborElement() as? CborMap
            ?: error("CborBackedSerializer only supports CBOR maps")
        return create(backingObject, decoder.cbor).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? CborEncoder
            ?: error("CborBackedSerializer only works with kotlinx.serialization CBOR")
        require(encoder.cbor.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching CborConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeCborElement(value.backingObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun Cbor.hasSameConfigurationAs(other: Cbor): Boolean {
    val left = configuration
    val right = other.configuration
    return left.encodeDefaults == right.encodeDefaults &&
            left.ignoreUnknownKeys == right.ignoreUnknownKeys &&
            left.encodeKeyTags == right.encodeKeyTags &&
            left.encodeValueTags == right.encodeValueTags &&
            left.encodeObjectTags == right.encodeObjectTags &&
            left.verifyKeyTags == right.verifyKeyTags &&
            left.verifyValueTags == right.verifyValueTags &&
            left.verifyObjectTags == right.verifyObjectTags &&
            left.useDefiniteLengthEncoding == right.useDefiniteLengthEncoding &&
            left.preferCborLabelsOverNames == right.preferCborLabelsOverNames &&
            left.alwaysUseByteString == right.alwaysUseByteString
}
