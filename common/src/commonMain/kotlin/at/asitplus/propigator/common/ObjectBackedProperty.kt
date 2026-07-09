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
            ?: return@ReadOnlyProperty readNull(serializer, actualKey)
        if (thisRef.isNull(element)) return@ReadOnlyProperty readNull(serializer, actualKey)
        thisRef.decode(serializer, element)
    }

@Suppress("UNCHECKED_CAST")
private fun <T> readNull(serializer: KSerializer<T>, actualKey: String): T {
    if (serializer.descriptor.isNullable) return null as T
    throw SerializationException("Missing required backing property: $actualKey")
}

inline fun <O, V, reified T> backedProperty(
    key: String? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<O, T> where O : ObjectBacked<V> =
    createBackedProperty(key, serializer)
