// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.ObjectBackedValidated
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap

class YamlObjectBackedSerializer<T : YamlObjectBacked>(
    private val yaml: Yaml = Yaml.Default,
    private val create: (YamlMap, Yaml) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = YamlMap.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        val map = YamlMap.serializer().deserialize(decoder)
        return create(map, yaml).also { (it as? ObjectBackedValidated)?.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(yaml.hasSameConfigurationAs(value.yaml)) {
            "Mismatching Yaml configuration. By default, the object owns the serialization shape."
        }
        YamlMap.serializer().serialize(encoder, value.rawObject)
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun Yaml.hasSameConfigurationAs(other: Yaml): Boolean {
    val left = configuration
    val right = other.configuration
    return left.nonStrictNullability == right.nonStrictNullability &&
        left.nonStrictNumber == right.nonStrictNumber &&
        left.encodeDefaultValues == right.encodeDefaultValues &&
        left.stringSerialization == right.stringSerialization &&
        left.nullSerialization == right.nullSerialization &&
        left.mapSerialization == right.mapSerialization &&
        left.classSerialization == right.classSerialization &&
        left.listSerialization == right.listSerialization
}
