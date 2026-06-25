// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlElement
import net.mamoe.yamlkt.YamlMap
import net.mamoe.yamlkt.YamlPrimitive
import kotlin.properties.ReadOnlyProperty

open class YamlObjectBacked(
    val rawObject: YamlMap,
    private val yaml: Yaml = Yaml.Default,
) : ObjectBacked<YamlElement> {
    override fun <T> decode(serializer: KSerializer<T>, element: YamlElement): T =
        yaml.decodeFromString(serializer, element.toString())

    override fun isNull(element: YamlElement): Boolean = element is YamlPrimitive && element.content == null

    override fun getElement(key: String): YamlElement? = rawObject[key]
}

inline fun <reified T> yamlProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<YamlObjectBacked, T> =
    backedProperty<YamlObjectBacked, YamlElement, T>(key, serializer)

inline fun <reified T> yamlSlice(serializer: KSerializer<T> = serializer()): ReadOnlyProperty<YamlObjectBacked, T> =
    ReadOnlyProperty { thisRef, _ -> thisRef.decode(serializer, thisRef.rawObject) }
