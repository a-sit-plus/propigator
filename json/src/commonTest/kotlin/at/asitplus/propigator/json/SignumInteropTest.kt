package at.asitplus.propigator.json

import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.testballoon.invoke
import at.asitplus.testballoon.minus
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject


internal val SignumInteropTest by testSuite {
    "Signum-backed key attestation" - {
        "matches KeyAttestationJwt while preserving unmodelled claims" {
            val keyAttestation = joseCompliantSerializer.decodeFromString<KeyAttestation>(keyAttestationJwtClaims)
            val josefKeyAttestation =
                joseCompliantSerializer.decodeFromString<KeyAttestationJwt>(keyAttestationJwtClaims)

            keyAttestation.toKeyAttestationJwt() shouldBe josefKeyAttestation
            keyAttestation.rawObject["future_claim"] shouldBe JsonObject(
                mapOf("nested" to JsonPrimitive(true)),
            )

            val forwarded = joseCompliantSerializer.encodeToString(keyAttestation)
            val forwardedClaims = joseCompliantSerializer.parseToJsonElement(forwarded).jsonObject

            joseCompliantSerializer.decodeFromString<KeyAttestationJwt>(forwarded) shouldBe josefKeyAttestation
            forwardedClaims["jsonWebToken"] shouldBe null
            forwardedClaims["keyAttestationClaims"] shouldBe null
            forwardedClaims["future_claim"] shouldBe JsonObject(
                mapOf("nested" to JsonPrimitive(true)),
            )
        }

        "rejects claims missing mandatory key-attestation fields" {
            shouldThrow<SerializationException> {
                joseCompliantSerializer.decodeFromString<KeyAttestation>(
                    """
                    {
                      "iss": "https://issuer.example",
                      "attested_keys": []
                    }
                    """.trimIndent(),
                )
            }
        }
    }
}

internal fun KeyAttestation.toKeyAttestationJwt() = KeyAttestationJwt(
    issuer = issuer,
    subject = subject,
    audience = audience,
    nonce = nonce,
    notBefore = notBefore,
    issuedAt = issuedAt,
    expiration = expiration,
    eudiWalletInfo = eudiWalletInfo,
    attestedKeys = attestedKeys,
    keyStorage = keyStorage,
    userAuthentication = userAuthentication,
    certification = certification,
    status = status,
)
