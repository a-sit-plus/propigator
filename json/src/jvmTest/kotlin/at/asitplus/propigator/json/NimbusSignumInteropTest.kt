package at.asitplus.propigator.json

import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.testballoon.matrix.matrixSuite
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.*
import kotlin.random.Random

@Serializable(with = NimbusJwtClaims.Serializer::class)
private class NimbusJwtClaims(
    raw: JsonObject,
    json: Json = joseCompliantSerializer,
) : JsonBacked(raw, json) {
    val jsonWebToken: JsonWebToken by jsonSlice()

    object Serializer : KSerializer<NimbusJwtClaims> by JsonBackedSerializerTemplate(
        create = ::NimbusJwtClaims,
    )
}

internal val NimbusSignumInteropTest by matrixSuite {
    "Nimbus-generated JWT claims" - {
        "deserialize through Signum JsonWebToken while preserving custom claims" {
            val issuer = "https://issuer.example/${UUID.randomUUID()}"
            val subject = "subject-${UUID.randomUUID()}"
            val foo = "foo-${UUID.randomUUID()}"
            val bar = Random.nextInt(1, Int.MAX_VALUE)
            val baz = "baz-${UUID.randomUUID()}"

            val signedJwt = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT)
                    .build(),
                JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .issueTime(Date(1_710_000_060_000))
                    .claim("foo", foo)
                    .claim("bar", bar)
                    .claim("baz", baz)
                    .build(),
            ).also {
                it.sign(MACSigner("0123456789abcdef0123456789abcdef".encodeToByteArray()))
            }

            val claimsJson = SignedJWT.parse(signedJwt.serialize()).payload.toString()
            val claims = joseCompliantSerializer.decodeFromString<NimbusJwtClaims>(claimsJson)

            claims.jsonWebToken.issuer shouldBe issuer
            claims.jsonWebToken.subject shouldBe subject
            claims.backingObject["foo"] shouldBe JsonPrimitive(foo)
            claims.backingObject["bar"] shouldBe JsonPrimitive(bar)
            claims.backingObject["baz"] shouldBe JsonPrimitive(baz)

            val forwardedClaims = joseCompliantSerializer
                .parseToJsonElement(joseCompliantSerializer.encodeToString(claims))
                .jsonObject

            forwardedClaims["jsonWebToken"] shouldBe null
            forwardedClaims["foo"] shouldBe JsonPrimitive(foo)
            forwardedClaims["bar"] shouldBe JsonPrimitive(bar)
            forwardedClaims["baz"] shouldBe JsonPrimitive(baz)
        }
    }
}
