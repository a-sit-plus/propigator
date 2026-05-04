// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

/**
 * Minimal mutable key/value view needed by the delegates.
 *
 * Implementations are expected to preserve unknown fields in their raw snapshot.
 */
interface ObjectBacked<K, V> {
    val codec: BackingCodec<V>
    fun getElement(key: K): V?
    fun putElement(key: K, value: V)
    fun removeElement(key: K)
}

/** Optional parse-time validation hook for required delegated properties. */
interface ObjectBackedValidated {
    fun validate()
}
