package at.asitplus.propigator.json

import at.asitplus.signum.indispensable.io.InstantLongSerializer
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    private val jsonFormat: Json = joseCompliantSerializer,
) : JsonBacked(raw, jsonFormat) {
    /**
     * We can serialize into data classes
     */
    val jsonWebToken: JsonWebToken by jsonSlice()

    /**
     * And composite them as necessary
     */
    val keyAttestationClaims: KeyAttestationClaims by jsonSlice()

    /**
     * Defining a local property is optional but allows us to strengthen typing
     * but requires correct serializer
     */
    val issuedAt: Instant by jsonProperty<Instant>("iat", InstantLongSerializer)

    override fun validate() {
        jsonWebToken
        keyAttestationClaims
        issuedAt
    }

    object Serializer : KSerializer<KeyAttestation> by JsonBackedSerializerTemplate(
        create = ::KeyAttestation
    )
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
