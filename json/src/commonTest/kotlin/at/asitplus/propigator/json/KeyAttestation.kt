package at.asitplus.propigator.json

import at.asitplus.propigator.common.ObjectBackedValidated
import at.asitplus.signum.indispensable.josef.EudiWalletInfo
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

internal val keyAttestationJwtClaims = """
    {
      "iss": "https://issuer.example",
      "sub": "holder-key-1",
      "aud": "wallet.example",
      "nonce": "nonce-123",
      "nbf": 1710000000,
      "iat": 1710000060,
      "exp": 1710003660,
      "eudi_wallet_info": {
        "general_info": {
          "wallet_provider_name": "A-SIT Plus",
          "wallet_solution_id": "wallet-1",
          "wallet_solution_version": "1.0.0",
          "wallet_solution_certification_information": "cert-info"
        },
        "key_storage_info": {
          "storage_type": "hardware",
          "storage_certification_information": "cc-eal4"
        }
      },
      "attested_keys": [
        {
          "kty": "oct",
          "kid": "key-1",
          "k": "AQIDBA"
        }
      ],
      "key_storage": ["iso_18045_high"],
      "user_authentication": ["pin"],
      "certification": "https://certs.example/key-storage",
      "status": {
        "status_list": {
          "idx": 12,
          "uri": "https://status.example/list.jwt"
        }
      },
      "future_claim": {
        "nested": true
      }
    }
""".trimIndent()

@Serializable(with = KeyAttestation.Serializer::class)
internal data class KeyAttestation(
    private val raw: JsonObject,
    private val json: Json = joseCompliantSerializer,
) : JsonObjectBacked(raw, JsonBackingCodec(json)), ObjectBackedValidated {
    val jsonWebToken: JsonWebToken
        get() = codec.decode(JsonWebToken.serializer(), rawObject)

    val keyAttestationClaims: KeyAttestationClaims
        get() = codec.decode(KeyAttestationClaims.serializer(), rawObject)

    val issuer: String?
        get() = jsonWebToken.issuer

    val subject: String?
        get() = jsonWebToken.subject

    val audience: String?
        get() = jsonWebToken.audience

    val nonce: String?
        get() = jsonWebToken.nonce

    val notBefore: Instant?
        get() = jsonWebToken.notBefore

    val issuedAt: Instant
        get() = jsonWebToken.issuedAt
            ?: throw SerializationException("Missing required backing property: iat")

    val expiration: Instant?
        get() = jsonWebToken.expiration

    val eudiWalletInfo: EudiWalletInfo?
        get() = jsonWebToken.eudiWalletInfo

    val attestedKeys: Collection<JsonWebKey>
        get() = keyAttestationClaims.attestedKeys

    val keyStorage: Collection<String>?
        get() = keyAttestationClaims.keyStorage

    val userAuthentication: Collection<String>?
        get() = keyAttestationClaims.userAuthentication

    val certification: String?
        get() = keyAttestationClaims.certification

    val status: JsonObject?
        get() = jsonWebToken.status

    override fun validate() {
        issuedAt
        attestedKeys
    }

    object Serializer : KSerializer<KeyAttestation> by JsonObjectBackedSerializer(::KeyAttestation)
}

@Serializable
internal data class KeyAttestationClaims(
    @SerialName("attested_keys")
    val attestedKeys: Collection<JsonWebKey>,

    @SerialName("key_storage")
    val keyStorage: Collection<String>? = null,

    @SerialName("user_authentication")
    val userAuthentication: Collection<String>? = null,

    val certification: String? = null,
)
