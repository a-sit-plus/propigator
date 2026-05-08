// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty

class JsonBackingCodec(
    val json: Json = Json.Default,
) : BackingCodec<JsonElement> {
    override fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T =
        json.decodeFromJsonElement(serializer, element)

    override fun <T> encode(serializer: KSerializer<T>, value: T): JsonElement =
        json.encodeToJsonElement(serializer, value)

    override fun nullElement(): JsonElement = JsonNull
    override fun isNull(element: JsonElement): Boolean = element is JsonNull
}

open class JsonObjectBacked(
    initial: JsonObject,
    override val codec: JsonBackingCodec = JsonBackingCodec(),
) : ObjectBacked<String, JsonElement> {
    private val backing: MutableMap<String, JsonElement> = initial.toMutableMap()

    val rawObject: JsonObject
        get() = JsonObject(backing)

    override fun getElement(key: String): JsonElement? = backing[key]
    override fun putElement(key: String, value: JsonElement) {
        backing[key] = value
    }

    override fun removeElement(key: String) {
        backing.remove(key)
    }
}

inline fun <reified T> jsonProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadWriteProperty<JsonObjectBacked, T> =
    backedProperty<JsonObjectBacked, String, JsonElement, T>(key, serializer)

inline fun <reified T> jsonSlice(serializer: KSerializer<T> = serializer()): ReadOnlyProperty<JsonObjectBacked, T> =
    ReadOnlyProperty { thisRef, _ -> thisRef.codec.decode(serializer, thisRef.rawObject) }

inline fun <reified T> nullableJsonProperty(
    key: String? = null,
    nullWriteMode: NullWriteMode = NullWriteMode.STORE_NULL,
): ReadWriteProperty<JsonObjectBacked, T?> =
    nullableBackedProperty<JsonObjectBacked, String, JsonElement, T>(key, nullWriteMode)
