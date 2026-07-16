package at.asitplus.propigator.common

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe

internal val ObjectBackedCommonTest by matrixSuite {
    "Alibi" {
        true shouldBe true
    }
}
