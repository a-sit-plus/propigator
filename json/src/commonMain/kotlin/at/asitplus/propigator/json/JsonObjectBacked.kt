// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import at.asitplus.propigator.common.slice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

typealias JsonBackedProperty<V> =
    ReadOnlyProperty<JsonObjectBacked, V>

open class JsonObjectBacked(
    override val backingObject: JsonObject,
    override val serialFormat: Json = Json.Default,
) : ObjectBacked() {
    override fun isFormatNull(element: Any?): Boolean = element is JsonNull

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]?.let { serialFormat.decodeFromJsonElement(serializer, it) }

    override fun <S> getSlice(serializer: KSerializer<S>) =
        serialFormat.decodeFromJsonElement(serializer, backingObject)
}

/**
 * Optional fields are backed as nullable type.
 * Example
 * ```val foo: String? by jsonProperty("foo")```
 *
 * Required fields are backed as strict types
 * ```val bar: Bar by jsonProperty("bar_obj", CustomBarSerializer)```
 */
inline fun <reified V> jsonProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
): JsonBackedProperty<V> =
    backedProperty<JsonObjectBacked, V>(key, serializer)

/**
 * Reads [defaultValue] when the backing object does not contain the property key.
 *
 * Serialization still emits the raw backing object unchanged. If defaults should be encoded,
 * construct or receive the backing object with those default fields already present.
 */

inline fun <reified V> jsonProperty(
    key: String? = null,
    defaultValue: V,
    serializer: KSerializer<V> = serializer(),
): JsonBackedProperty<V> = backedProperty(key, defaultValue, serializer)

inline fun <reified V> jsonSlice(serializer: KSerializer<V> = serializer()): JsonBackedProperty<V> =
    slice(serializer)

/**
 * Returns the combined content of two JsonObjects.
 * If both inputs are zero returns the empty JsonObject
 */
@Throws(IllegalArgumentException::class)
fun JsonObject?.strictUnion(other: JsonObject?): JsonObject {
    if (this == null) return other ?: JsonObject(emptyMap())
    if (other == null) return this

    val duplicates = this.keys intersect other.keys
    require(duplicates.isEmpty()) {
        "Duplicate keys: ${duplicates.joinToString()}"
    }

    return JsonObject(this + other)
}
