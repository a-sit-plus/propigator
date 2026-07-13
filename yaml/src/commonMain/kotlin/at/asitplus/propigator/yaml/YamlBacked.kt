// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import at.asitplus.propigator.common.slice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap
import net.mamoe.yamlkt.YamlPrimitive
import kotlin.properties.ReadOnlyProperty

typealias YamlProperty<V> =
        ReadOnlyProperty<YamlBacked, V>

open class YamlBacked(
    override val backingObject: YamlMap,
    override val serialFormat: Yaml = Yaml.Default,
) : ObjectBacked() {
    override fun isFormatNull(element: Any?): Boolean = element is YamlPrimitive && element.content == null
    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]?.let {
            //????????... needs `Yaml.decodeFromYamlElement()` functionality in base package?
            val yamlToString = serialFormat.encodeToString(it)
            serialFormat.decodeFromString(serializer, yamlToString)
        }

    override fun <S> getSlice(serializer: KSerializer<S>): S {
        //????????... needs `Yaml.decodeFromYamlElement()` functionality in base package?
        val yamlToString = serialFormat.encodeToString(backingObject)
        return serialFormat.decodeFromString(serializer, yamlToString)
    }
}

inline fun <reified T> yamlProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): YamlProperty<T> =
    backedProperty<YamlBacked, T>(key, serializer)

inline fun <reified T> yamlSlice(serializer: KSerializer<T> = serializer()): YamlProperty<T> =
    slice(serializer)
