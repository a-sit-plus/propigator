package at.asitplus.propigator.common

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe

internal val ObjectBackedCommonTest by matrixSuite {
    "Object-backed common test data" - {
        "exposes a valid reusable person fixture" {
            ObjectBackedTestData.validate()

            ObjectBackedTestData.id shouldBe "p-1"
            ObjectBackedTestData.name shouldBe "Ada"
            ObjectBackedTestData.renamed shouldBe true
            ObjectBackedTestData.foo shouldBe ObjectBackedTestPerson.Foo(
                bar = 2,
                baz = "eyz",
            )
        }
    }
}
