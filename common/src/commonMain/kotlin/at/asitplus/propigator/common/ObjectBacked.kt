// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

/** Minimal read-only key/value view needed by the delegates. */
interface ObjectBacked<K, V> {
    val codec: BackingCodec<V>
    fun getElement(key: K): V?
}

/** Optional parse-time validation hook for required delegated properties. */
interface ObjectBackedValidated {
    fun validate()
}
