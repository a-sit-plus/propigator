// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.json

import at.asitplus.propigator.common.Backed
import at.asitplus.propigator.common.backedProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
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
class JsonBacked<out T> @PublishedApi internal constructor(
    override val value: T,
    val backingObject: JsonObject,
    override val serialFormat: Json,
) : Backed<T> {

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]
            ?.takeUnless { it is JsonNull }
            ?.let { serialFormat.decodeFromJsonElement(serializer, it) }
}

/** Creates a JSON-backed envelope from an ordinary serializable value. */
inline fun <reified T> JsonBacked(
    value: T,
    serialFormat: Json = Json.Default,
): JsonBacked<T> =
    JsonBacked(
        value = value,
        backingObject = serialFormat.encodeToJsonElement(serializer<T>(), value).jsonObject,
        serialFormat = serialFormat,
    )

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
 * Generic serializer used automatically for every [JsonBacked] carrier type.
 */
class JsonBackedSerializer<T>(
    private val valueSerializer: KSerializer<T>,
) : KSerializer<JsonBacked<T>> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): JsonBacked<T> {
        decoder as? JsonDecoder
            ?: error("JsonBackedSerializer only works with kotlinx.serialization JSON")

        val backingObject = decoder.decodeJsonElement().jsonObject
        val valueFormat = if (decoder.json.configuration.ignoreUnknownKeys) {
            decoder.json
        } else {
            Json(decoder.json) { ignoreUnknownKeys = true }
        }
        return JsonBacked(
            value = valueFormat.decodeFromJsonElement(valueSerializer, backingObject),
            backingObject = backingObject,
            serialFormat = decoder.json,
        )
    }

    override fun serialize(encoder: Encoder, value: JsonBacked<T>) {
        encoder as? JsonEncoder
            ?: error("JsonBackedSerializer only works with kotlinx.serialization JSON")
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
