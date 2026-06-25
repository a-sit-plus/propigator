// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.mamoe.yamlkt.*
import kotlin.properties.ReadOnlyProperty

class YamlBackingCodec(
    val yaml: Yaml = Yaml.Default,
) : BackingCodec<YamlElement> {
    override fun <T> decode(serializer: KSerializer<T>, element: YamlElement): T =
        yaml.decodeFromString(serializer, element.toString())

    override fun isNull(element: YamlElement): Boolean = element is YamlPrimitive && element.content == null
}

open class YamlObjectBacked(
    initial: YamlMap,
    override val codec: YamlBackingCodec = YamlBackingCodec(),
) : ObjectBacked<String, YamlElement> {
    private val backing: YamlMap = initial

    val rawObject: YamlMap
        get() = backing

    override fun getElement(key: String): YamlElement? = backing[key]
}

inline fun <reified T> yamlProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<YamlObjectBacked, T> =
    backedProperty<YamlObjectBacked, String, YamlElement, T>(key, serializer)

inline fun <reified T> yamlSlice(serializer: KSerializer<T> = serializer()): ReadOnlyProperty<YamlObjectBacked, T> =
    ReadOnlyProperty { thisRef, _ -> thisRef.codec.decode(serializer, thisRef.rawObject) }
