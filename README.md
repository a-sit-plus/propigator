<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="propigator-w.png">
  <source media="(prefers-color-scheme: light)" srcset="propigator-b.png">
  <img alt="Propigator – Typed properties over untamed data" src="propigator-b.png">
</picture>

# Typed properties over untamed data

[![A-SIT Plus Official](https://raw.githubusercontent.com/a-sit-plus/a-sit-plus.github.io/709e802b3e00cb57916cbb254ca5e1a5756ad2a8/A-SIT%20Plus_%20official_opt.svg)](https://plus.a-sit.at/open-source.html)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-orange.svg?logo=kotlin)](http://kotlinlang.org)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Java](https://img.shields.io/badge/java-17+-blue.svg?logo=OPENJDK)](https://www.oracle.com/java/technologies/downloads/#java17)
[![Maven Central](https://img.shields.io/maven-central/v/at.asitplus.propigator/common)](https://mvnrepository.com/artifact/at.asitplus.propigator/common)

</div>

Propigator is a Kotlin Multiplatform library for building typed views over raw object-shaped data while keeping the original payload forward-compatible.
Use it when you want Kotlin properties for the fields your code understands, but you must not destroy fields you do not understand yet. This is useful for protocol objects, configuration files, extension points, signed or externally-owned payloads, and versioned data formats where newer producers may send fields older consumers should preserve.

Propigator currently provides object-backed wrappers for:

- `common`: format-agnostic delegates and validation hooks.
- `json`: `JsonObject` backed objects.
- `yaml`: [yamlkt](https://github.com/him188/yamlkt) `YamlMap` backed objects.

## What It Does

Normal `@Serializable` data classes are great when your schema is the whole truth. They parse known fields into constructor parameters and usually ignore or reject the rest depending on format configuration.

Propigator uses a different model:

1. Keep the raw object map.
2. Add delegated Kotlin properties for fields you care about.
3. Decode each property on demand with `kotlinx.serialization`.
4. Serialize the raw object again, including unknown fields.

That means a downstream integrator can parse, inspect, and re-emit objects without becoming the schema authority for every field in the document.

## Forward Compatibility

Propigator preserves unknown fields by design.

If an incoming JSON object contains:

```json
{
  "id": "42",
  "name": "Grace",
  "futureField": {
    "addedBy": "newer-service"
  }
}
```

and your wrapper only knows `id` and `name`, `futureField` stays in the backing object and is emitted again when you serialize.

This makes Propigator a good fit for downstream tools that should be conservative:

- Read a document from an upstream system.
- Preserve extension fields, vendor fields, and future fields.
- Avoid forcing a full validation model onto the entire object.

## Using it in your Project

Use the module matching the format you need.

```kotlin
dependencies {
    implementation("at.asitplus.propigator:common:<version>")
    implementation("at.asitplus.propigator:json:<version>")
    implementation("at.asitplus.propigator:yaml:<version>")
}
```

`json` depends on `common` and `kotlinx-serialization-json`.

`yaml` depends on `common` and `yamlkt`.

## JSON Quick Start

Define a nominal wrapper class around `JsonObjectBacked`. Add typed properties with `jsonProperty()`.
The declared Kotlin type controls whether the backing field is required or nullable.

```kotlin
@Serializable(with = PersonJsonObject.Serializer::class)
class PersonJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, JsonBackingCodec(json)), ObjectBackedValidated {
    val id: String by jsonProperty()
    val name: String by jsonProperty()
    val active: Boolean by jsonProperty("is_active")
    val nickname: String? by jsonProperty("nick")

    override fun validate() {
        id
        name
    }

    object Serializer : KSerializer<PersonJsonObject> by JsonObjectBackedSerializer(::PersonJsonObject)
}
```

Use it through `kotlinx.serialization`:

```kotlin
val json = Json { prettyPrint = true }

val person = json.decodeFromString(
    PersonJsonObject.serializer(),
    """
    {
      "id": "42",
      "name": "Grace",
      "is_active": true,
      "futureField": "preserved"
    }
    """.trimIndent()
)

val encoded = json.encodeToString(PersonJsonObject.serializer(), person)
```

The encoded JSON still contains `futureField`.

## YAML Quick Start

YAML works the same way, using `YamlObjectBacked` and YAML-specific delegates.

```kotlin
@Serializable(with = ServiceYamlObject.Serializer::class)
class ServiceYamlObject(
    raw: YamlMap,
    yaml: Yaml = Yaml.Default,
) : YamlObjectBacked(raw, YamlBackingCodec(yaml)), ObjectBackedValidated {
    val id: String by yamlProperty()
    val endpoint: String by yamlProperty()
    val description: String? by yamlProperty()

    override fun validate() {
        id
        endpoint
    }

    object Serializer : KSerializer<ServiceYamlObject> by YamlObjectBackedSerializer(create = ::ServiceYamlObject)
}
```

```kotlin
val yaml = Yaml.Default

val service = yaml.decodeFromString(
    ServiceYamlObject.serializer(),
    """
    id: payments
    endpoint: https://example.test/payments
    x-vendor-option: keep-me
    """.trimIndent()
)

val encoded = yaml.encodeToString(ServiceYamlObject.serializer(), service)
```

`x-vendor-option` is preserved.

## Delegated Properties

Required and nullable properties both use `jsonProperty()` or `yamlProperty()`.
The property type, together with the supplied serializer, defines the nullability contract.

```kotlin
val id: String by jsonProperty()
val displayName: String by jsonProperty("display_name")
val nickname: String? by jsonProperty("nick")
```

If no key is supplied, the Kotlin property name is used as the object key. If a key is supplied, that key is used instead.

Reading a missing or explicit-null required property throws `SerializationException`:

```kotlin
val id = person.id // throws if "id" is absent or null
```

For nullable properties, missing keys and explicit format-native null values both read as `null`.
The serializer must also be nullable; declaring the Kotlin property as `String?` gives the delegate a nullable serializer.

Read-only views are also useful:

```kotlin
val PersonJsonObject.publicName: String by jsonProperty("name")
val PersonJsonObject.optionalNick: String? by jsonProperty("nick")
```

## Whole-Object Slices

Use `jsonSlice()` or `yamlSlice()` when you want to decode the entire backing object as an existing
`@Serializable` type instead of defining one delegated property per field.

A slice is a read-only view over `rawObject`. It uses the wrapper's configured `JsonBackingCodec` or
`YamlBackingCodec`, so the same format settings and serializers apply.

```kotlin
@Serializable
data class PublicClaims(
    val iss: String,
    val sub: String,
    val aud: String,
)

@Serializable(with = ClaimsJsonObject.Serializer::class)
class ClaimsJsonObject(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, JsonBackingCodec(json)), ObjectBackedValidated {
    val claims: PublicClaims by jsonSlice()
    val nonce: String? by jsonProperty()

    override fun validate() {
        claims
    }

    object Serializer : KSerializer<ClaimsJsonObject> by JsonObjectBackedSerializer(::ClaimsJsonObject)
}
```

`claims` is decoded from the whole JSON object, while `nonce` is decoded from the same raw object.
Unknown fields are still preserved when the wrapper is serialized again.

YAML-backed objects provide the same pattern with `yamlSlice()`:

```kotlin
val foo: Foo by yamlSlice()
```

## Parse, Not Validate

Propigator is designed for parse-not-validate workflows.

Parsing creates a typed view over the raw object. It does not require you to model every field in the payload. Unknown fields are kept as raw data.

By default, delegated required fields are checked when read:

```kotlin
val person = json.decodeFromString(PersonJsonObject.serializer(), payload)

// Missing "name" fails here, when the property is needed.
println(person.name)
```

This is useful for downstream integrators that should accept future payloads, inspect a small subset, and forward the rest unchanged.

## Prime Example: JOSE

JOSE-style objects are a prime Propigator use case. They have a few core fields that many libraries need to understand, but they are also intentionally open-ended: deployments add domain-specific parameters, claims, headers, and policy fields.

For example, a `JwsSigned` wrapper may need typed access to signature-critical fields while preserving every application-specific field:

```kotlin
@Serializable(with = JwsSigned.Serializer::class)
class JwsSigned(
    raw: JsonObject,
    json: Json = Json.Default,
) : JsonObjectBacked(raw, JsonBackingCodec(json)) {
    val protectedHeader: String by jsonProperty("protected")
    val payload: String by jsonProperty()
    val signature: String by jsonProperty()

    object Serializer : KSerializer<JwsSigned> by JsonObjectBackedSerializer(::JwsSigned)
}

val JwsSigned.kid: String? by jsonProperty("kid")
val JwsSigned.trustDomain: String? by jsonProperty("trust_domain")
val JwsSigned.policyVersion: Int? by jsonProperty("policy_version")
```

An integrator can read the fields it needs:

```kotlin
val jws = json.decodeFromString(JwsSigned.serializer(), incoming)

val signature = jws.signature
val trustDomain = jws.trustDomain

val forwarded = json.encodeToString(JwsSigned.serializer(), jws)
```

Fields not modelled by `JwsSigned`, including future JOSE extensions and domain-specific fields, stay in `rawObject` and are emitted again.

This is parse-not-validate by design. A component that routes or inspects a JOSE object may need `payload` and `signature`, but it should not reject an object because it does not understand a domain-specific property. Full JOSE validation belongs to the layer that has the keys, algorithms, policy, critical-header handling, and domain rules. Propigator keeps the object forward-compatible until that layer needs to make a decision.

When you do need parse-time checks for your own mandatory fields, implement `ObjectBackedValidated` and touch those properties in `validate()`:

```kotlin
override fun validate() {
    id
    name
}
```

The format serializer calls `validate()` after decoding if the object implements `ObjectBackedValidated`.

Use this sparingly:

- Validate fields your component truly requires.
- Do not validate fields you merely want to preserve.
- Do not reject unknown fields just because this version of your code does not understand them.

## Extension Properties

You can add semantic fields outside the nominal wrapper class.

```kotlin
val PersonJsonObject.locale: String? by jsonProperty("locale")

val PersonJsonObject.displayLabel: String
    get() = locale?.let { "$name ($it)" } ?: name
```

This is useful when several downstream integrations share the same raw object but each integration reads different extension fields.

## Raw Object Access

Use `rawObject` when you need to inspect, pass through, or debug the complete backing object.

```kotlin
val raw: JsonObject = person.rawObject
```

For JSON, `rawObject` is a `JsonObject`.

For YAML, `rawObject` is a `YamlMap`.

The wrapper exposes the original raw object through `rawObject`.

## Serializer Pattern

Propigator serializers are attached to each nominal wrapper type:

```kotlin
@Serializable(with = PersonJsonObject.Serializer::class)
class PersonJsonObject(...) : JsonObjectBacked(...) {
    object Serializer : KSerializer<PersonJsonObject> by JsonObjectBackedSerializer(::PersonJsonObject)
}
```

The serializer deserializes and serializes the raw object. Delegated properties are not discovered by the Kotlin serialization compiler plugin as constructor properties. They are semantic accessors over the backing object.

## Choosing Data Classes vs Propigator

Use regular `@Serializable` data classes when:

- Your service owns the full schema.
- Unknown fields should be ignored or rejected.
- You want constructor-based validation and immutable values.

Use Propigator when:

- You need to preserve unknown fields.
- You are building downstream tooling for someone else's schema.
- The schema is extensible or versioned.
- You only own a few fields inside a larger object.
- You need to parse first and validate only the fields your workflow touches.



## Current Limitations

- Propigator wraps object/map payloads, not arbitrary top-level scalar values.
- YAML element conversion is intentionally simple: individual values are rendered through YAML and decoded again with `yamlkt`.
- Delegated properties are runtime accessors. They are not constructor properties and do not appear as separate generated serialization fields.
- `ObjectBackedValidated` validates only what your `validate()` function reads.


## Contributing

External contributions are greatly appreciated.
Please observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).

---

<p align="center">
The Apache License does not apply to the logos (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
