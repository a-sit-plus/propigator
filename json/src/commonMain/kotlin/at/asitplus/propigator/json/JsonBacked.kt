// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.Backed
import at.asitplus.propigator.common.backedProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

/**
 * A serializable value together with its native JSON representation.
 *
 * Values decoded from JSON retain that complete object, including properties unknown to [T].
 * Values created with [JsonBacked] obtain their object from the generated serializer for [T].
 */
@Serializable(with = JsonBackedSerializer::class)
open class JsonBacked<out T> protected constructor(
    override val value: T,
    val backingObject: JsonObject,
    override val serialFormat: Json,
) : Backed<T> {

    protected constructor(backed: JsonBacked<@UnsafeVariance T>) : this(
        backed.value,
        backed.backingObject,
        backed.serialFormat,
    )

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]
            ?.takeUnless { it is JsonNull }
            ?.let { serialFormat.decodeFromJsonElement(serializer, it) }

    companion object {
        @PublishedApi
        internal fun <T> create(
            value: T,
            backingObject: JsonObject,
            serialFormat: Json,
        ): JsonBacked<T> = JsonBacked(value, backingObject, serialFormat)
    }
}

/** Creates a JSON-backed envelope from an ordinary serializable value. */
inline fun <reified T> JsonBacked(
    value: T,
    serialFormat: Json = Json.Default,
): JsonBacked<T> =
    JsonBacked.create(
        value = value,
        backingObject = serialFormat.encodeToJsonElement(serializer<T>(), value).jsonObject,
        serialFormat = serialFormat,
    )

inline fun <reified T> Json.decodeFromJsonElementBacked(element: JsonElement): JsonBacked<T> =
    decodeFromJsonElement(element)

inline fun <reified T> Json.encodeToJsonElementBacked(value: JsonBacked<T>): JsonElement =
    encodeToJsonElement(value)

inline fun <reified T> Json.decodeFromStringBacked(string: String): JsonBacked<T> =
    decodeFromString(string)

inline fun <reified T> Json.encodeToStringBacked(value: JsonBacked<T>): String =
    encodeToString(value)

/**
 * Reads an additional property directly from the retained JSON object.
 *
 * ```kotlin
 * val JsonBacked<MyClaims>.applicationClaim: String? by jsonProperty()
 * ```
 */
inline fun <reified V> jsonProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<JsonBacked<*>, V> =
    backedProperty<JsonBacked<*>, V>(key, serializer)

inline fun <reified V> jsonProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<JsonBacked<*>, V> =
    backedProperty<JsonBacked<*>, V>(key, serializer, defaultValue)

/**
 * Reuses [JsonBacked] serialization for a concrete subclass created by [wrap].
 */
open class JsonBackedSerializerTemplate<T, B : JsonBacked<T>>(
    private val valueSerializer: KSerializer<T>,
    private val wrap: (JsonBacked<T>) -> B,
) : KSerializer<B> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): B {
        decoder as? JsonDecoder
            ?: error("JsonBackedSerializer only works with kotlinx.serialization JSON")

        val backingObject = decoder.decodeJsonElement().jsonObject
        val valueFormat = if (decoder.json.configuration.ignoreUnknownKeys) {
            decoder.json
        } else {
            Json(decoder.json) { ignoreUnknownKeys = true }
        }
        return wrap(
            JsonBacked.create(
                value = valueFormat.decodeFromJsonElement(valueSerializer, backingObject),
                backingObject = backingObject,
                serialFormat = decoder.json,
            )
        )
    }

    override fun serialize(encoder: Encoder, value: B) {
        encoder as? JsonEncoder
            ?: error("JsonBackedSerializer only works with kotlinx.serialization JSON")
        require(encoder.json.hasSameConfigurationAs(value.serialFormat)) {
            "Mismatching JsonConfiguration. By default, the object owns the serialization shape."
        }
        encoder.encodeJsonElement(value.backingObject)
    }
}

/** Generic serializer used automatically for [JsonBacked]. */
class JsonBackedSerializer<T>(
    valueSerializer: KSerializer<T>,
) : JsonBackedSerializerTemplate<T, JsonBacked<T>>(valueSerializer, { it })

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
