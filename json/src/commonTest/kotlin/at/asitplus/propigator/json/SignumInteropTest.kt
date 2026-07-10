package at.asitplus.propigator.json

import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject


internal val SignumInteropTest by matrixSuite {
    "Signum-backed key attestation" - {
        "matches KeyAttestationJwt while preserving unmodelled claims" {
            val keyAttestation = joseCompliantSerializer.decodeFromString<KeyAttestation>(keyAttestationJwtClaims)
            val josefKeyAttestation =
                joseCompliantSerializer.decodeFromString<KeyAttestationJwt>(keyAttestationJwtClaims)

            keyAttestation.toKeyAttestationJwt() shouldBe josefKeyAttestation
            keyAttestation.backingObject["future_claim"] shouldBe JsonObject(
                mapOf("nested" to JsonPrimitive(true)),
            )

            val forwarded = joseCompliantSerializer.encodeToString(keyAttestation)
            val forwardedClaims = joseCompliantSerializer.parseToJsonElement(forwarded).jsonObject
            forwardedClaims["jsonWebToken"] shouldBe null
            forwardedClaims["keyAttestationClaims"] shouldBe null
            forwardedClaims["future_claim"] shouldBe JsonObject(
                mapOf("nested" to JsonPrimitive(true)),
            )

            joseCompliantSerializer.decodeFromString<KeyAttestationJwt>(forwarded) shouldBe josefKeyAttestation
        }

        "rejects claims missing mandatory key-attestation fields" {
            shouldThrow<NoSuchElementException> {
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
    issuedAt = issuedAt,
    issuer = jsonWebToken.issuer,
    subject = jsonWebToken.subject,
    audience = jsonWebToken.audience,
    nonce = jsonWebToken.nonce,
    notBefore = jsonWebToken.notBefore,
    expiration = jsonWebToken.expiration,
    eudiWalletInfo = jsonWebToken.eudiWalletInfo,
    status = jsonWebToken.status,
    attestedKeys = keyAttestationClaims.attestedKeys,
    keyStorage = keyAttestationClaims.keyStorage,
    userAuthentication = keyAttestationClaims.userAuthentication,
    certification = keyAttestationClaims.certification,
)
