// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer

/** Minimal read-only key/value view needed by the delegates. */
interface ObjectBacked<V> {
    fun getElement(key: String): V?
    fun isNull(element: V): Boolean
    fun <T> decode(serializer: KSerializer<T>, element: V): T
}

/** Optional parse-time validation hook for required delegated properties. */
interface ObjectBackedValidated {
    fun validate()
}
