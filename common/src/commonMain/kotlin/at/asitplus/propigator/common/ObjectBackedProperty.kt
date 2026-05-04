// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

//TODO: this can go, as the underlyign format will do null handling, right!?
public enum class NullWriteMode {
    /** Store an explicit format-native null value. */
    STORE_NULL,

    /** Remove the key from the backing object. */
    REMOVE_KEY,
}

public class RequiredBackedProperty<O, K, V, T>(
    private val key: K?,
    private val serializer: KSerializer<T>,
) : ReadWriteProperty<O, T> where O : ObjectBacked<K, V> {
    override fun getValue(thisRef: O, property: KProperty<*>): T {
        val actualKey = (key ?: (property.name as? K))
            ?: throw SerializationException("property ${property.name} is not identifiable by String")
        val element = thisRef.getElement(actualKey)
            ?: throw SerializationException("Missing required backing property: $actualKey")
        return thisRef.codec.decode(serializer, element)
    }

    override fun setValue(thisRef: O, property: KProperty<*>, value: T) {
        val actualKey = (key ?: (property.name as? K))
            ?: throw SerializationException("property ${property.name} is not identifiable by String")
        thisRef.putElement(actualKey, thisRef.codec.encode(serializer, value))
    }
}

public class NullableBackedProperty<O, K, V, T>(
    private val key: K?,
    private val serializer: KSerializer<T>,
    private val nullWriteMode: NullWriteMode = NullWriteMode.STORE_NULL,
) : ReadWriteProperty<O, T?> where O : ObjectBacked<K, V> {
    override fun getValue(thisRef: O, property: KProperty<*>): T? {
        val actualKey = (key ?: (property.name as? K))
            ?: throw SerializationException("property ${property.name} is not identifiable by String")
        val element = thisRef.getElement(actualKey) ?: return null
        if (thisRef.codec.isNull(element)) return null
        return thisRef.codec.decode(serializer, element)
    }

    override fun setValue(thisRef: O, property: KProperty<*>, value: T?) {
        val actualKey = (key ?: (property.name as? K))
            ?: throw SerializationException("property ${property.name} is not identifiable by String")
        if (value == null && nullWriteMode == NullWriteMode.REMOVE_KEY) {
            thisRef.removeElement(actualKey)
        } else {
            thisRef.putElement(
                actualKey,
                if (value == null) thisRef.codec.nullElement() else thisRef.codec.encode(serializer, value),
            )
        }
    }
}

public inline fun <O, K, V, reified T> backedProperty(
    key: K? = null,
): ReadWriteProperty<O, T> where O : ObjectBacked<K, V> =
    RequiredBackedProperty(key, serializer())

public inline fun <O, K, V, reified T> nullableBackedProperty(
    key: K? = null,
    nullWriteMode: NullWriteMode = NullWriteMode.STORE_NULL,
): ReadWriteProperty<O, T?> where O : ObjectBacked<K, V> =
    NullableBackedProperty(key, serializer(), nullWriteMode)
