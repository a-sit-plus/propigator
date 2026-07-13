// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap

class YamlBackedSerializerTemplate<T : YamlBacked>(
    private val yaml: Yaml = Yaml.Default,
    private val create: (YamlMap, Yaml) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = YamlMap.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        val map = YamlMap.serializer().deserialize(decoder)
        return create(map, yaml).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(yaml.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching Yaml configuration. By default, the object owns the serialization shape."
        }
        YamlMap.serializer().serialize(encoder, value.backingObject)
    }
}

/**
 * Creates an object-backed serializer bound to this [Yaml] instance.
 *
 * YAMLKt does not expose the originating [Yaml] through its decoder, so the returned serializer
 * should be used with this same instance for both decoding and encoding.
 */
fun <T : YamlBacked> Yaml.objectBackedSerializer(
    create: (YamlMap, Yaml) -> T,
): KSerializer<T> = YamlBackedSerializerTemplate(
    yaml = this,
    create = create,
)

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
