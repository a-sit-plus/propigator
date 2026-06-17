package at.asitplus.propigator.common

import at.asitplus.testballoon.invoke
import at.asitplus.testballoon.minus
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

internal val ObjectBackedCommonTest by testSuite {
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
