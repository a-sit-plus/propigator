// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat

abstract class ObjectBacked {
    abstract val serialFormat: SerialFormat
    abstract val backingObject: Any
    abstract fun isFormatNull(element: Any?): Boolean
    abstract fun <V> getElement(key: String, serializer: KSerializer<V>): V?
    abstract fun <S> getSlice(serializer: KSerializer<S>): S

    /** Optional parse-time validation hook for required delegated properties. */
    open fun validate(): Unit = Unit

    override fun equals(other: Any?): Boolean {
        if (other !is ObjectBacked) return false
        return backingObject == other.backingObject
    }

    override fun hashCode(): Int = backingObject.hashCode()
    override fun toString(): String = backingObject.toString()
}
