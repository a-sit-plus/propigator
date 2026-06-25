// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.BackingCodec
import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

class JsonBackingCodec(
    val json: Json = Json.Default,
) : BackingCodec<JsonElement> {
    override fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T =
        json.decodeFromJsonElement(serializer, element)

    override fun isNull(element: JsonElement): Boolean = element is JsonNull
}

open class JsonObjectBacked(
    initial: JsonObject,
    override val codec: JsonBackingCodec = JsonBackingCodec(),
) : ObjectBacked<String, JsonElement> {
    private val backing: JsonObject = initial

    val rawObject: JsonObject
        get() = backing

    override fun getElement(key: String): JsonElement? = backing[key]
}

/**
 * Optional fields are backed as nullable type.
 * Example
 * ```val foo: String? by jsonProperty("foo")```
 *
 * Required fields are backed as strict types
 * ```val bar: Bar by jsonProperty("bar_obj", CustomBarSerializer)```
 */
inline fun <reified T> jsonProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<JsonObjectBacked, T> =
    backedProperty<JsonObjectBacked, String, JsonElement, T>(key, serializer)

inline fun <reified T> jsonSlice(serializer: KSerializer<T> = serializer()): ReadOnlyProperty<JsonObjectBacked, T> =
    ReadOnlyProperty { thisRef, _ -> thisRef.codec.decode(serializer, thisRef.rawObject) }
