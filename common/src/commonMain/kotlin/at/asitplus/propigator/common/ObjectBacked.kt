// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat

interface ObjectBacked {
    val serialFormat: SerialFormat
    val backingObject: Any
    fun isFormatNull(element: Any?): Boolean
    fun <V> getElement(key: String, serializer: KSerializer<V>): V?
    fun <S> getSlice(serializer: KSerializer<S>): S

    /** Optional parse-time validation hook for required delegated properties. */
    fun validate(): Unit = Unit
}
