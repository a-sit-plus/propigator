// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

/**
 * Minimal mutable key/value view needed by the delegates.
 *
 * Implementations are expected to preserve unknown fields in their raw snapshot.
 */
public interface ObjectBacked<K, V> {
    public val codec: BackingCodec<V>
    public fun getElement(key: K): V?
    public fun putElement(key: K, value: V)
    public fun removeElement(key: K)
}

/** Optional parse-time validation hook for required delegated properties. */
public interface ObjectBackedValidated {
    public fun validate()
}
