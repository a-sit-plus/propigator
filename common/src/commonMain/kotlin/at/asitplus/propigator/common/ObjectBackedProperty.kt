// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class NullWriteMode {
    /** Store an explicit format-native null value. */
    STORE_NULL,

    /** Remove the key from the backing object. */
    REMOVE_KEY,
}

class BackedProperty<O, K, V, T>(
    private val key: K?,
    private val serializer: KSerializer<T>,
    private val nullWriteMode: NullWriteMode,
) : ReadWriteProperty<O, T> where O : ObjectBacked<K, V> {
    override fun getValue(thisRef: O, property: KProperty<*>): T {
        val actualKey = actualKey(property)
        val element = thisRef.getElement(actualKey)
            ?: return readNull(actualKey)
        if (thisRef.codec.isNull(element)) return readNull(actualKey)
        return thisRef.codec.decode(serializer, element)
    }

    override fun setValue(thisRef: O, property: KProperty<*>, value: T) {
        val actualKey = actualKey(property)
        if (value == null) {
            if (!serializer.descriptor.isNullable) {
                throw SerializationException("Required backing property cannot be set to null: $actualKey")
            }
            when (nullWriteMode) {
                NullWriteMode.STORE_NULL ->
                    thisRef.putElement(actualKey, thisRef.codec.nullElement())

                NullWriteMode.REMOVE_KEY ->
                    thisRef.removeElement(actualKey)
            }
            return
        }

        thisRef.putElement(actualKey, thisRef.codec.encode(serializer, value))
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
    nullWriteMode: NullWriteMode,
): ReadWriteProperty<O, T> where O : ObjectBacked<K, V> =
    BackedProperty(key, serializer, nullWriteMode)
