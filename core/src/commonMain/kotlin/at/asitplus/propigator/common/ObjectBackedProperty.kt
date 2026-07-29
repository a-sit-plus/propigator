// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

@PublishedApi
internal fun <O : ObjectBacked, V> createBackedProperty(
    key: String?,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
): ReadOnlyProperty<O, V> =
    ReadOnlyProperty { thisRef, property ->
        val actualKey = key ?: property.name
        val element: V = thisRef.getElement(actualKey, serializer)
            ?: return@ReadOnlyProperty missingValue(actualKey, serializer, defaultValue)
        if (thisRef.isFormatNull(element)) {
            return@ReadOnlyProperty missingValue(actualKey, serializer, defaultValue)
        }
        element
    }

private fun <V> missingValue(
    key: String,
    serializer: KSerializer<V>,
    defaultValue: (() -> V)?,
): V {
    if (defaultValue != null) return defaultValue()
    if (serializer.descriptor.isNullable) {
        @Suppress("UNCHECKED_CAST")
        return null as V
    }
    throw NoSuchElementException("Missing property: $key")
}

fun <T : ObjectBackedObject<*>> T.validating(block: T.() -> Unit): T = apply {
    block()
    validate()
}

inline fun <O : ObjectBacked, reified V> backedProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<O, V> =
    createBackedProperty(key, serializer, null)

inline fun <O : ObjectBacked, reified V> backedProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
    defaultValue: V,
): ReadOnlyProperty<O, V> =
    createBackedProperty(key, serializer) { defaultValue }
