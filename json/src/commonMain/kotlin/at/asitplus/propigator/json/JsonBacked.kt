// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.ObjectBackedObject
import at.asitplus.propigator.common.backedProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

typealias JsonProperty<V> =
    ReadOnlyProperty<JsonBacked, V>

interface JsonBacked : ObjectBacked {
    val backingObject: JsonObject
    override val serialFormat: Json

    override fun isFormatNull(element: Any?): Boolean = element is JsonNull

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]?.let { serialFormat.decodeFromJsonElement(serializer, it) }

}

/**
 * A [JsonBacked] object whose member delegates may update the backing object.
 *
 * Concrete properties control whether callers may write them with normal Kotlin setter visibility:
 * ```kotlin
 * var id: String by jsonProperty()
 *     private set
 * ```
 */
abstract class JsonBackedObject(
    backingObject: JsonObject,
    final override val serialFormat: Json = Json.Default,
) : ObjectBackedObject<String>(), JsonBacked {
    final override var backingObject: JsonObject = backingObject
        private set

    protected inline fun <reified V> jsonProperty(
        key: String? = null,
        serializer: KSerializer<V> = serializer(),
    ): BackedProperty<V> = backedProperty(key, serializer)

    protected final override fun keyFromPropertyName(name: String): String = name

    protected final override fun <V> readElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]?.let { serialFormat.decodeFromJsonElement(serializer, it) }

    final override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        readElement(key, serializer)

    protected final override fun <V> writeElement(key: String, serializer: KSerializer<V>, value: V) {
        backingObject = JsonObject(
            backingObject + (key to serialFormat.encodeToJsonElement(serializer, value))
        )
    }
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
): JsonProperty<V> =
    backedProperty<JsonBacked, V>(key, serializer)

class JsonBackedSerializerTemplate<T : JsonBacked>(
    private val create: (JsonObject, Json) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as? JsonDecoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        return create(decoder.decodeJsonElement().jsonObject, decoder.json).also { it.validate() }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? JsonEncoder
            ?: error("JsonObjectBackedSerializer only works with kotlinx.serialization JSON")
        require(encoder.json.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching JsonConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeJsonElement(value.backingObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun Json.hasSameConfigurationAs(other: Json): Boolean {
    val left = configuration
    val right = other.configuration
    return left.encodeDefaults == right.encodeDefaults &&
            left.ignoreUnknownKeys == right.ignoreUnknownKeys &&
            left.isLenient == right.isLenient &&
            left.allowStructuredMapKeys == right.allowStructuredMapKeys &&
            left.prettyPrint == right.prettyPrint &&
            left.explicitNulls == right.explicitNulls &&
            left.prettyPrintIndent == right.prettyPrintIndent &&
            left.coerceInputValues == right.coerceInputValues &&
            left.useArrayPolymorphism == right.useArrayPolymorphism &&
            left.classDiscriminator == right.classDiscriminator &&
            left.allowSpecialFloatingPointValues == right.allowSpecialFloatingPointValues &&
            left.useAlternativeNames == right.useAlternativeNames &&
            left.namingStrategy == right.namingStrategy &&
            left.decodeEnumsCaseInsensitive == right.decodeEnumsCaseInsensitive &&
            left.allowTrailingComma == right.allowTrailingComma &&
            left.allowComments == right.allowComments &&
            left.classDiscriminatorMode == right.classDiscriminatorMode &&
            left.exceptionsWithDebugInfo == right.exceptionsWithDebugInfo
}
