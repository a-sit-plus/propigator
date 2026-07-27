package at.asitplus.propigator.cbor

import at.asitplus.propigator.common.validating
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.decodeFromCborElement
import kotlinx.serialization.cbor.encodeToCborElement

@Serializable(with = CborCoreObj.Serializer::class)
class CborCoreObj private constructor(
    backingObject: CborMap,
    serialFormat: Cbor,
) : CborBackedObject(backingObject, serialFormat) {

    var aString: String by cborProperty()
        private set

    var anInt: Int by cborProperty()
        private set

    var nativeKeyValue: String by cborProperty(CborInteger(7L))
        private set

    object Serializer : KSerializer<CborCoreObj> by CborBackedSerializerTemplate(::CborCoreObj)

    companion object {
        context(serialFormat: Cbor)
        operator fun invoke(
            aString: String,
            anInt: Int,
            nativeKeyValue: String = "native key",
        ): CborCoreObj =
            CborCoreObj(CborMap(emptyMap()), serialFormat).validating {
                this.aString = aString
                this.anInt = anInt
                this.nativeKeyValue = nativeKeyValue
            }
    }
}

private val complexCborKey = CborMap(
    mapOf(CborString("kind") to CborString("complex"))
)

val CborCoreObj.integerKey: String by cborProperty(CborInteger(42L))
val CborCoreObj.complexKey: String by cborProperty(complexCborKey)
val CborCoreObj.taggedPropertyName: String by cborProperty(keyTags = ulongArrayOf(100u))
val CborCoreObj.taggedWireName: String by cborProperty(
    "wire_name",
    keyTags = ulongArrayOf(200u),
)

val CborCoreTest by matrixSuite {
    val serialFormat = Cbor {
        encodeDefaults = true
        encodeKeyTags = true
        ignoreUnknownKeys = true
    }

    "RTT with unknown native key" {
        val created = with(serialFormat) {
            CborCoreObj("some string", 1337)
        }
        val backingObject = CborMap(
            created.backingObject + mapOf(
                CborInteger(42L) to CborString("integer key"),
                complexCborKey to CborString("complex key"),
                CborString("taggedPropertyName", 100u) to CborString("derived tagged key"),
                CborString("wire_name", 200u) to CborString("renamed tagged key"),
            ),
            created.backingObject.tags,
        )
        val decoded = serialFormat.decodeFromCborElement<CborCoreObj>(backingObject)

        decoded.aString shouldBe "some string"
        decoded.anInt shouldBe 1337
        decoded.nativeKeyValue shouldBe "native key"
        decoded.integerKey shouldBe "integer key"
        decoded.complexKey shouldBe "complex key"
        decoded.taggedPropertyName shouldBe "derived tagged key"
        decoded.taggedWireName shouldBe "renamed tagged key"
        serialFormat.encodeToCborElement(decoded) shouldBe backingObject
    }
}
