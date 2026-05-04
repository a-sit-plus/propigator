// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.yaml

import at.asitplus.propigator.common.BackingCodec
import at.asitplus.propigator.common.NullWriteMode
import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import at.asitplus.propigator.common.nullableBackedProperty
import kotlinx.serialization.KSerializer
import net.mamoe.yamlkt.*
import kotlin.properties.ReadWriteProperty

class YamlBackingCodec(
    val yaml: Yaml = Yaml.Default,
) : BackingCodec<YamlElement> {
    override fun <T> decode(serializer: KSerializer<T>, element: YamlElement): T =
        yaml.decodeFromString(serializer, element.toString())

    override fun <T> encode(serializer: KSerializer<T>, value: T): YamlElement =
        yaml.decodeYamlFromString(yaml.encodeToString(serializer, value))

    override fun nullElement(): YamlElement = YamlNull
    override fun isNull(element: YamlElement): Boolean = element is YamlPrimitive && element.content == null
}

open class YamlObjectBacked(
    initial: YamlMap,
    override val codec: YamlBackingCodec = YamlBackingCodec(),
) : ObjectBacked<String, YamlElement> {
    private val backing: MutableMap<YamlElement, YamlElement> = initial.content.toMutableMap()

    val rawObject: YamlMap
        get() = YamlMap(backing.toMap())

    override fun getElement(key: String): YamlElement? = rawObject[key]

    override fun putElement(key: String, value: YamlElement) {
        val actualKey = findKey(key) ?: YamlPrimitive(key)
        backing[actualKey] = value
    }

    override fun removeElement(key: String) {
        findKey(key)?.let { backing.remove(it) }
    }

    private fun findKey(key: String): YamlElement? = backing.keys.firstOrNull { it.content == key }
}

inline fun <reified T> yamlProperty(
    key: String? = null,
): ReadWriteProperty<YamlObjectBacked, T> =
    backedProperty<YamlObjectBacked, String, YamlElement, T>(key)

inline fun <reified T> nullableYamlProperty(
    key: String? = null,
    nullWriteMode: NullWriteMode = NullWriteMode.STORE_NULL,
): ReadWriteProperty<YamlObjectBacked, T?> =
    nullableBackedProperty<YamlObjectBacked, String, YamlElement, T>(key, nullWriteMode)
