package at.asitplus.propigator.common

import kotlinx.serialization.Serializable

internal interface ObjectBackedTestPerson : ObjectBackedValidated {
    val id: String
    val name: String
    val renamed: Boolean
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
    override val id: String = "p-1"
    override val name: String = "Ada"
    override val renamed: Boolean = true
    override val foo: ObjectBackedTestPerson.Foo = ObjectBackedTestPerson.Foo(
        bar = 2,
        baz = "eyz",
    )
}