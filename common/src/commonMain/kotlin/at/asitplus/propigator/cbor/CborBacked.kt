// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.cbor

import at.asitplus.propigator.common.ObjectBacked
import at.asitplus.propigator.common.backedProperty
import at.asitplus.propigator.common.slice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

typealias CborBackedProperty<V> =
        ReadOnlyProperty<CborBacked, V>

interface CborBacked : ObjectBacked {
    val backingObject: CborMap
    override val serialFormat: Cbor

    override fun isFormatNull(element: Any?): Boolean = element is CborNull

    override fun <V> getElement(key: String, serializer: KSerializer<V>): V? =
        backingObject[key]?.let { serialFormat.decodeFromCborElement(serializer, it) }

    override fun <S> getSlice(serializer: KSerializer<S>) =
        serialFormat.decodeFromCborElement(serializer, backingObject)
}

/**
 * Optional fields are backed as nullable type.
 * Example
 * ```val foo: String? by cborProperty("foo")```
 *
 * Required fields are backed as strict types
 * ```val bar: Bar by cborProperty("bar_obj", CustomBarSerializer)```
 */
inline fun <reified V> cborProperty(
    key: String? = null,
    serializer: KSerializer<V> = serializer(),
): CborBackedProperty<V> =
    backedProperty<CborBacked, V>(key, serializer)

/**
 * Reads [defaultValue] when the backing object does not contain the property key.
 *
 * Serialization still emits the raw backing object unchanged. If defaults should be encoded,
 * construct or receive the backing object with those default fields already present.
 */
inline fun <reified V> cborProperty(
    key: String? = null,
    defaultValue: V,
    serializer: KSerializer<V> = serializer(),
): CborBackedProperty<V> = backedProperty(key, defaultValue, serializer)

inline fun <reified V> cborSlice(serializer: KSerializer<V> = serializer()): CborBackedProperty<V> =
    slice(serializer)

/**
 * Returns the combined content of two [CborMap]s.
 * If both inputs are zero returns the empty [CborMap]
 */
@OptIn(ExperimentalUnsignedTypes::class)
@Throws(IllegalArgumentException::class)
fun CborMap?.strictUnion(other: CborMap?): CborMap {
    if (this == null) return other ?: CborMap(emptyMap())
    if (other == null) return this

    val duplicates = this.keys intersect other.keys
    require(duplicates.isEmpty()) {
        "Duplicate keys: ${duplicates.joinToString()}"
    }

    return CborMap(this + other)
}
