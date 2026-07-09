// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

@PublishedApi
internal fun <O, V, T> createBackedProperty(
    key: String?,
    serializer: KSerializer<T>,
): ReadOnlyProperty<O, T> where O : ObjectBacked<V> =
    ReadOnlyProperty { thisRef, property ->
        val actualKey = key ?: property.name
        val element = thisRef.getElement(actualKey)
            ?: return@ReadOnlyProperty missingValue(actualKey, serializer)
        if (thisRef.isNull(element)) return@ReadOnlyProperty missingValue(actualKey, serializer)
        thisRef.decode(serializer, element)
    }

@PublishedApi
internal fun <O, V, T> createDefaultProperty(
    key: String?,
    defaultValue: T,
    serializer: KSerializer<T>,
): ReadOnlyProperty<O,T> where O : ObjectBacked<V> = ReadOnlyProperty { thisRef, property ->
    val actualKey = key ?: property.name
    val element = thisRef.getElement(actualKey)
        ?: return@ReadOnlyProperty defaultValue
    if (thisRef.isNull(element)) return@ReadOnlyProperty missingValue(actualKey, serializer)
    thisRef.decode(serializer, element)
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

inline fun <O, V, reified T> backedProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<O, T> where O : ObjectBacked<V> =
    createBackedProperty(key, serializer)

inline fun <O, V, reified T> backedProperty(
    key: String? = null,
    defaultValue: T,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<O, T> where O : ObjectBacked<V> =
    createDefaultProperty(key, defaultValue, serializer)
