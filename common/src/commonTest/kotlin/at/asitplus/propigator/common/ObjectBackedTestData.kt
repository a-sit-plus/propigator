package at.asitplus.propigator.common

import kotlinx.serialization.Serializable

internal interface ObjectBackedTestPerson : ObjectBackedValidated {
    var id: String
    var name: String
    var renamed: Boolean
    val foo: Foo

    override fun validate() {
        id
        name
        foo
    }

    @Serializable
    data class Foo(
        val bar: Int,
        val baz: String,
    )
}

@Serializable
internal object ObjectBackedTestData : ObjectBackedTestPerson {
    override var id: String = "p-1"
    override var name: String = "Ada"
    override var renamed: Boolean = true
    override val foo: ObjectBackedTestPerson.Foo = ObjectBackedTestPerson.Foo(
        bar = 2,
        baz = "eyz",
    )
}