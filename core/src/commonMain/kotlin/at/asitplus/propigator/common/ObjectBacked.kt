// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

interface ObjectBacked {
    val serialFormat: SerialFormat

    fun <V> getElement(key: String, serializer: KSerializer<V>): V?

    /** Validates required delegated properties. Override and call `super` for semantic checks. */
    fun validate(): Unit = Unit
}

/** A typed value paired with a lossless native backing object. */
interface Backed<out T> : ObjectBacked {
    val value: T
}

/** A typed value paired with its complete native representation and owning format. */
interface NativeBacked<out T, out O, out F : SerialFormat> : Backed<T> {
    val backingObject: O
    override val serialFormat: F
}

/** A serializable carrier that reuses another carrier as its flattened base. */
interface Flattened<out B> {
    val base: B
}

abstract class ObjectBackedObject<K> : ObjectBacked {
    private val requiredPropertyChecks = mutableListOf<() -> Unit>()
    private val propertyInitializers = mutableMapOf<String, (Any?) -> Unit>()

    protected abstract fun keyFromPropertyName(name: String): K
    protected abstract fun <V> readElement(key: K, serializer: KSerializer<V>): V?
    protected abstract fun <V> writeElement(key: K, serializer: KSerializer<V>, value: V)

    /** Returns a type-safe initializer that may be consumed exactly once. */
    protected fun <V> initBackedProperty(
        property: KProperty1<*, V>,
    ): BackedPropertyInitializer<V> = BackedPropertyInitializer {
        val initializer = propertyInitializers.remove(property.name)
            ?: error("Backed property '${property.name}' is not available for initialization")
        initializer(it)
    }

    protected class BackedPropertyInitializer<V> internal constructor(
        private val initializer: (V) -> Unit,
    ) {
        fun with(value: V) = initializer(value)
    }

    protected inline fun <reified V> backedProperty(
        key: K? = null,
        serializer: KSerializer<V> = serializer(),
    ): BackedProperty<V> = createMutableBackedProperty(key, serializer)

    protected inline fun <reified V> backedProperty(
        key: K? = null,
        serializer: KSerializer<V> = serializer(),
        defaultValue: V,
    ): BackedProperty<V> =
        createMutableBackedProperty(key, serializer, defaultValue = { defaultValue })

    protected inline fun <reified V> backedProperty(
        serializer: KSerializer<V> = serializer(),
        noinline keyFromPropertyName: (String) -> K,
    ): BackedProperty<V> =
        createMutableBackedProperty(null, serializer, null, keyFromPropertyName)

    protected inline fun <reified V> backedProperty(
        serializer: KSerializer<V> = serializer(),
        defaultValue: V,
        noinline keyFromPropertyName: (String) -> K,
    ): BackedProperty<V> =
        createMutableBackedProperty(
            null,
            serializer,
            { defaultValue },
            keyFromPropertyName,
        )

    protected fun <V> createMutableBackedProperty(
        key: K?,
        serializer: KSerializer<V>,
        defaultValue: (() -> V)? = null,
        defaultKey: (String) -> K = ::keyFromPropertyName,
    ): BackedProperty<V> = BackedProperty(key, serializer, defaultKey, defaultValue)

    protected inner class BackedProperty<V>(
        private val key: K?,
        private val serializer: KSerializer<V>,
        private val defaultKey: (String) -> K,
        private val defaultValue: (() -> V)?,
    ) : ReadWriteProperty<Any?, V> {
        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): BackedProperty<V> {
            val actualKey = key ?: defaultKey(property.name)
            propertyInitializers[property.name] = { value ->
                @Suppress("UNCHECKED_CAST")
                writeElement(actualKey, serializer, value as V)
            }
            if (!serializer.descriptor.isNullable) {
                requiredPropertyChecks += { readValue(actualKey) }
            }
            return this
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): V {
            val actualKey = key ?: defaultKey(property.name)
            return readValue(actualKey)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            writeElement(key ?: defaultKey(property.name), serializer, value)
        }

        private fun readValue(actualKey: K): V {
            return readElement(actualKey, serializer) ?: defaultOrMissing(actualKey)
        }

        private fun defaultOrMissing(actualKey: K): V =
            if (defaultValue != null) defaultValue()
            else missingValue(actualKey.toString(), serializer)
    }

    override fun validate() {
        requiredPropertyChecks.forEach { it() }
    }

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        readElement(keyFromPropertyName(key), serializer)

    private fun <V> missingValue(key: String, serializer: KSerializer<V>): V {
        if (serializer.descriptor.isNullable) {
            @Suppress("UNCHECKED_CAST")
            return null as V
        }
        throw NoSuchElementException("Missing property: $key")
    }
}
