// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH
// SPDX-License-Identifier: Apache-2.0

package at.asitplus.propigator.multi

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Propigator's integrated multi-format API is experimental and may change without notice.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMultiFormatApi
