package at.asitplus.propigator.cbor

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement

interface CborCore {
    val aString: String
    val anInt: Int
}

@Serializable
data class CborCoreValue(
    override val aString: String,
    override val anInt: Int,
    @CborLabel(7)
    val nativeKeyValue: String = "native key",
    val defaultString: String = "default",
    val nullableDefault: String? = null,
) : CborCore

@Serializable(with = ConcreteCborCore.Companion::class)
class ConcreteCborCore private constructor(
    backed: CborBacked<CborCoreValue>,
) : CborBacked<CborCoreValue>(backed), CborCore by backed.value {

    companion object : CborBackedSerializerTemplate<CborCoreValue, ConcreteCborCore>(
        CborCoreValue.serializer(),
        ::ConcreteCborCore,
    )
}

private val complexCborKey = CborMap(
    mapOf(CborString("kind") to CborString("complex"))
)

val CborBacked<CborCoreValue>.integerKey: String by cborProperty(CborInteger(42L))
val CborBacked<CborCoreValue>.defaultInteger: Int by cborProperty(defaultValue = 23)
val CborBacked<CborCoreValue>.complexKey: String by cborProperty(complexCborKey)
val CborBacked<CborCoreValue>.taggedPropertyName: String by
    cborProperty(keyTags = ulongArrayOf(100u))
val CborBacked<CborCoreValue>.taggedWireName: String by cborProperty(
    "wire_name",
    keyTags = ulongArrayOf(200u),
)

val CborCoreTest by matrixSuite {
    val serialFormat = Cbor {
        encodeDefaults = false
        encodeKeyTags = true
        ignoreUnknownKeys = false
        preferCborLabelsOverNames = true
    }

    "construct with an ordinary serializable value" {
        val value = CborCoreValue("some string", 1337)
        val created = serialFormat.CborBacked(value)

        created.value shouldBe value
        created.backingObject shouldBe CborMap(
            mapOf(
                CborString("aString") to CborString("some string"),
                CborString("anInt") to CborInteger(1337L),
            )
        )
    }

    "retain unknown native keys, tags, and map metadata" {
        val input = CborMap(
            mapOf(
                CborString("aString") to CborString("some string"),
                CborString("anInt") to CborInteger(1337L),
                CborInteger(7L) to CborString("native key"),
                CborInteger(42L) to CborString("integer key"),
                complexCborKey to CborString("complex key"),
                CborString("taggedPropertyName", 100u) to CborString("derived tagged key"),
                CborString("wire_name", 200u) to CborString("renamed tagged key"),
                CborString("defaultString") to CborString("from wire"),
            ),
            listOf(999u),
        )

        val decoded = serialFormat.decodeFromCborElementBacked<CborCoreValue>(input)

        decoded.value shouldBe CborCoreValue(
            "some string",
            1337,
            defaultString = "from wire",
        )
        decoded.defaultInteger shouldBe 23
        decoded.integerKey shouldBe "integer key"
        decoded.complexKey shouldBe "complex key"
        decoded.taggedPropertyName shouldBe "derived tagged key"
        decoded.taggedWireName shouldBe "renamed tagged key"
        serialFormat.encodeToCborElementBacked(decoded) shouldBe input
    }

    "use backed byte array shortcuts" {
        val value = with(serialFormat) {
            ConcreteCborCore(CborCoreValue("some string", 1337))
        }

        val encoded = serialFormat.encodeToByteArrayBacked(value)
        serialFormat.decodeFromByteArrayBacked<CborCoreValue>(encoded).value shouldBe value.value
    }

    "expose carrier and backing-only properties directly" {
        val input = CborMap(
            mapOf(
                CborString("aString") to CborString("some string"),
                CborString("anInt") to CborInteger(1337L),
                CborInteger(42L) to CborString("integer key"),
            )
        )

        val decoded = serialFormat.decodeFromCborElement<ConcreteCborCore>(input)

        decoded.aString shouldBe "some string"
        decoded.integerKey shouldBe "integer key"
        serialFormat.encodeToCborElement(decoded) shouldBe input
    }

    "construct with an explicit serializer" {
        val value = CborCoreValue("some string", 1337)

        val created = serialFormat.CborBacked(value, CborCoreValue.serializer())

        created.backingObject shouldBe serialFormat.CborBacked(value).backingObject
    }
}
