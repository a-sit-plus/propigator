package at.asitplus.propigator.common

import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.shouldBe
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private fun RegisterEntry<out Person>.shouldMatchPayload() {
    person?.personData shouldBe expectedPersonData
    someField shouldBe "register-value"
}

internal val MultiFormatTest by matrixSuite {
    "object-backed values nested in serializable data classes" - {
        "round-trip a JSON-backed person as JSON" {
            val json = Json {
                ignoreUnknownKeys = true
//                encodeDefaults = true
            }
            val serializer = RegisterEntry.serializer(PersonJson.serializer())
            val source: JsonObject = getExpectedPayload(json)

            val decoded = json.decodeFromJsonElement(serializer, source)
            decoded.shouldMatchPayload()

            val encoded = json.encodeToString(serializer, decoded)
            json.decodeFromString(serializer, encoded).shouldMatchPayload()
        }

        "round-trip a CBOR-backed person as CBOR" {
            val cbor = Cbor {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val serializer = RegisterEntry.serializer(PersonCbor.serializer())
            val source: CborElement = getExpectedPayload(cbor)
            val decoded = cbor.decodeFromCborElement(serializer, source)
            decoded.shouldMatchPayload()

            val encoded = cbor.encodeToByteArray(serializer, decoded)
            cbor.decodeFromByteArray(serializer, encoded).shouldMatchPayload()
        }
    }
}
