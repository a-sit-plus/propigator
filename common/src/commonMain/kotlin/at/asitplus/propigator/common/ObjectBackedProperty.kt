// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class BackedProperty<O, K, V, T>(
    private val key: K?,
    private val serializer: KSerializer<T>,
) : ReadOnlyProperty<O, T> where O : ObjectBacked<K, V> {
    override fun getValue(thisRef: O, property: KProperty<*>): T {
        val actualKey = actualKey(property)
        val element = thisRef.getElement(actualKey)
            ?: return readNull(actualKey)
        if (thisRef.codec.isNull(element)) return readNull(actualKey)
        return thisRef.codec.decode(serializer, element)
    }

    @Suppress("UNCHECKED_CAST")
    private fun actualKey(property: KProperty<*>): K =
        key ?: (property.name as? K)
        ?: throw SerializationException("property ${property.name} is not identifiable by backing key type")

    @Suppress("UNCHECKED_CAST")
    private fun readNull(actualKey: K): T {
        if (serializer.descriptor.isNullable) return null as T
        throw SerializationException("Missing required backing property: $actualKey")
    }
}

inline fun <O, K, V, reified T> backedProperty(
    key: K? = null,
    serializer: KSerializer<T> = serializer(),
): ReadOnlyProperty<O, T> where O : ObjectBacked<K, V> =
    BackedProperty(key, serializer)
