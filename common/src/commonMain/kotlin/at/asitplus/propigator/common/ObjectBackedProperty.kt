// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

@PublishedApi
internal fun <O : ObjectBacked, V> createBackedProperty(
    key: String?,
    serializer: KSerializer<V>
): ReadOnlyProperty<O, V> =
    ReadOnlyProperty { thisRef, property ->
        val actualKey = key ?: property.name
        val element: V = thisRef.getElement(actualKey, serializer)
            ?: return@ReadOnlyProperty missingValue(actualKey, serializer)
        if (thisRef.isFormatNull(element)) return@ReadOnlyProperty missingValue(actualKey, serializer)
        element
    }

@PublishedApi
internal fun <O : ObjectBacked, V> createDefaultProperty(
    key: String?,
    defaultValue: V,
    serializer: KSerializer<V>,
): ReadOnlyProperty<O, V> =
    ReadOnlyProperty { thisRef, property ->
        val actualKey = key ?: property.name
        val element: V = thisRef.getElement(actualKey, serializer)
            ?: return@ReadOnlyProperty defaultValue
        if (thisRef.isFormatNull(element)) return@ReadOnlyProperty missingValue(actualKey, serializer)
        element
    }

private fun <V> missingValue(
    key: String,
    serializer: KSerializer<V>
): V {
    if (serializer.descriptor.isNullable) {
        @Suppress("UNCHECKED_CAST")
        return null as V
    }
    throw NoSuchElementException("Missing property: $key")
}

inline fun <O : ObjectBacked, reified V> backedProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<O, V> =
    createBackedProperty(key, serializer)

inline fun <O : ObjectBacked, reified V> backedProperty(
    key: String? = null,
    defaultValue: V,
    serializer: KSerializer<V> = serializer(),
): ReadOnlyProperty<O, V> =
    createDefaultProperty(key, defaultValue, serializer)

inline fun <O : ObjectBacked, reified S> slice(
    serializer: KSerializer<S> = serializer()
): ReadOnlyProperty<O, S> = ReadOnlyProperty { thisRef, _ ->
    thisRef.getSlice(serializer)
}