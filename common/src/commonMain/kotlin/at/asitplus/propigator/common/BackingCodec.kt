// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.common

import kotlinx.serialization.KSerializer

/**
 * Format-specific bridge between a raw element type and typed kotlinx.serialization values.
 */
public interface BackingCodec<E> {
    public fun <T> decode(serializer: KSerializer<T>, element: E): T
    public fun <T> encode(serializer: KSerializer<T>, value: T): E
    public fun nullElement(): E
    public fun isNull(element: E): Boolean
}
