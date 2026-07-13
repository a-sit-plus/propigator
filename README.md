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

Propigator is a Kotlin Multiplatform library for typed, read-only views over raw object-shaped data. Use it when your code only understands part of a JSON or YAML object, but must preserve unknown fields when the object is re-emitted.

Propigator provides wrappers for:

- `common`: format-agnostic delegates and validation hooks.
- `json`: `JsonObject` backed objects.
- `yaml`: [yamlkt](https://github.com/him188/yamlkt) `YamlMap` backed objects.

## Core Model

1. Keep the raw object map.
2. Add read-only delegated Kotlin properties for fields you care about.
3. Decode each property on demand with `kotlinx.serialization`.
4. Serialize the same raw object again.

For example, if an incoming JSON object contains a future field:

```json
{ "id": "42", "name": "Grace", "futureField": "preserved" }
```

and your wrapper only exposes `id` and `name`, `futureField` stays in `backingObject` and is serialized again unchanged. Delegated properties never rebuild, overwrite, or remove backing fields.

## Using it in your Project

```kotlin
dependencies {
    implementation("at.asitplus.propigator:common:<version>")
    implementation("at.asitplus.propigator:json:<version>")
    implementation("at.asitplus.propigator:yaml:<version>")
}
```

Use `json` for `kotlinx-serialization-json` backed objects and `yaml` for yamlkt backed objects.

## JSON Quick Start

Define a wrapper around `JsonBacked` and add typed properties with `jsonProperty()`.

```kotlin
@Serializable(with = PersonJson.Serializer::class)
class PersonJson(
    backingObject: JsonObject,
    serialFormat: Json = Json.Default,
) : JsonBacked(backingObject, serialFormat) {
    val id: String by jsonProperty()
    val name: String by jsonProperty()
    val active: Boolean by jsonProperty("is_active")
    val nickname: String? by jsonProperty("nick")

    override fun validate() {
        id
        name
    }

    object Serializer : KSerializer<PersonJson> by JsonBackedSerializerTemplate(::PersonJson)
}
```

```kotlin
val json = Json.Default

val person = json.decodeFromString(
    PersonJson.serializer(),
    """
    {
      "id": "42",
      "name": "Grace",
      "is_active": true,
      "futureField": "preserved"
    }
    """.trimIndent()
)

val encoded = json.encodeToString(PersonJson.serializer(), person)
```

The encoded JSON still contains `futureField`.

## YAML Quick Start

YAML uses the same pattern with `YamlBacked` and `yamlProperty()`.

```kotlin
@Serializable(with = ServiceYaml.Serializer::class)
class ServiceYaml(
    backingObject: YamlMap,
    serialFormat: Yaml = Yaml.Default,
) : YamlBacked(backingObject, serialFormat) {
    val id: String by yamlProperty()
    val endpoint: String by yamlProperty()
    val description: String? by yamlProperty()

    override fun validate() {
        id
        endpoint
    }

    object Serializer : KSerializer<ServiceYaml> by
        Yaml.Default.objectBackedSerializer(::ServiceYaml)
}
```

```kotlin
val yaml = Yaml.Default

val service = yaml.decodeFromString(
    ServiceYaml.serializer(),
    """
    id: payments
    endpoint: https://example.test/payments
    x-vendor-option: keep-me
    """.trimIndent()
)

val encoded = yaml.encodeToString(ServiceYaml.serializer(), service)
```

`x-vendor-option` is preserved.

## Delegated Properties

Required and nullable properties both use `jsonProperty()` or `yamlProperty()`.
The property type, together with the supplied serializer, defines the nullability contract.

All delegated properties are read-only. They decode values from the backing object when read, and they do not write values back.

```kotlin
val id: String by jsonProperty()
val displayName: String by jsonProperty("display_name")
val nickname: String? by jsonProperty("nick")
```

If no key is supplied, the Kotlin property name is used as the object key. If a key is supplied, that key is used instead.

Reading a missing required property throws `NoSuchElementException`:

```kotlin
val id = person.id // throws if "id" is absent
```

For nullable properties, missing keys read as `null`.
The serializer must also be nullable; declaring the Kotlin property as `String?` gives the delegate a nullable serializer.

JSON-backed properties can also define a read default for missing keys:

```kotlin
val active: Boolean by jsonProperty(defaultValue = true)
val displayName: String by jsonProperty("display_name", defaultValue = "Anonymous")
```

This mirrors Kotlin serialization's absent-field default semantics for reads. It does not add the field to `backingObject`, and serialization still emits the preserved backing JSON object unchanged. If `Json { encodeDefaults = true }` should affect the serialized payload, construct or receive the backing `JsonObject` with those default fields already present.

## Whole-Object Slices

Use `jsonSlice()` or `yamlSlice()` to decode the entire backing object as an existing `@Serializable` type.

```kotlin
@Serializable
data class PublicClaims(
    val iss: String,
    val sub: String,
    val aud: String,
)

@Serializable(with = ClaimsJson.Serializer::class)
class ClaimsJson(
    backingObject: JsonObject,
    serialFormat: Json = Json.Default,
) : JsonBacked(backingObject, serialFormat) {
    val claims: PublicClaims by jsonSlice()
    val nonce: String? by jsonProperty()

    override fun validate() {
        claims
    }

    object Serializer : KSerializer<ClaimsJson> by JsonBackedSerializerTemplate(::ClaimsJson)
}
```

`claims` is decoded from the whole JSON object, while `nonce` is decoded from the same raw object. Unknown fields are still preserved.

YAML-backed objects provide the same pattern:

```kotlin
val foo: Foo by yamlSlice()
```

## Validation

Propigator is designed for parse-not-validate workflows: parse the raw object, expose the fields your workflow needs, and keep the rest untouched. Required delegated fields are checked when read:

```kotlin
val person = json.decodeFromString(PersonJson.serializer(), payload)

// Missing "name" fails here, when the property is needed.
println(person.name)
```

For parse-time checks, override `validate()` and touch only the fields your component requires:

```kotlin
override fun validate() {
    id
    name
}
```

The format serializer calls `validate()` after decoding. The default implementation does nothing.
This is useful for open-ended formats such as JOSE, where one layer may need typed access to a few fields while preserving claims, headers, or extensions for a later validation layer.

## Extension Properties

You can add semantic fields outside the nominal wrapper class:

```kotlin
val PersonJson.locale: String? by jsonProperty("locale")

val PersonJson.displayLabel: String
    get() = locale?.let { "$name ($it)" } ?: name
```

## Raw Objects and Serializers

Use `backingObject` to inspect, pass through, or debug the complete backing object:

```kotlin
val raw: JsonObject = person.backingObject
```

For JSON, `backingObject` is a `JsonObject`; for YAML, it is a `YamlMap`. To create a modified payload, build a new format object and wrap it.

Attach a serializer to each wrapper type:

```kotlin
@Serializable(with = PersonJson.Serializer::class)
class PersonJson(...) : JsonBacked(...) {
    object Serializer : KSerializer<PersonJson> by JsonBackedSerializerTemplate(::PersonJson)
}
```

The serializer wraps the backing object on decode and serializes the same backing object on encode. Delegated properties are semantic accessors, not constructor properties.

Each wrapper keeps the `Json` or `Yaml` instance passed to its constructor as `serialFormat`. When a JSON-backed wrapper is deserialized, it retains the `Json` instance performing the decode. Delegated properties therefore use the same configuration and serializers module as the outer decode:

```kotlin
private val personJson = Json { ignoreUnknownKeys = true }

val person = personJson.decodeFromString<PersonJson>(payload)
```

YAMLKt does not expose the active `Yaml` instance through its decoder. The default serializer can
bind `Yaml.Default`, as in the quick start above. For custom configuration, bind the serializer
explicitly and use the same `Yaml` instance for both decoding and encoding:

```kotlin
private val serviceYaml = Yaml { /* custom configuration */ }
private val serviceSerializer = serviceYaml.objectBackedSerializer(::ServiceYaml)

val service = serviceYaml.decodeFromString(serviceSerializer, payload)
val encoded = serviceYaml.encodeToString(serviceSerializer, service)
```

Encoding rejects mismatching format configuration content because the wrapper's configured format owns the serialization shape. Separate `Json` or `Yaml` instances with the same relevant settings are accepted.

## Building Backing JSON

When constructing a JSON-backed object from existing serializable values, encode those values to `JsonObject`s and merge them before wrapping. `strictUnion()` combines two JSON objects and rejects duplicate keys:

```kotlin
constructor(
    personData: PersonData,
    addressData: AddressData? = null,
    serialFormat: Json = Json.Default,
) : this(
    backingObject = serialFormat.encodeToJsonElement(personData).jsonObject.strictUnion(
        addressData?.let { serialFormat.encodeToJsonElement(it).jsonObject }
    ),
    serialFormat = serialFormat,
)
```

`strictUnion()` accepts `null` on either side. Duplicate keys throw `IllegalArgumentException` so overlapping generated fields cannot silently overwrite each other.

## Choosing Data Classes vs Propigator

Use regular `@Serializable` data classes when:

- Your service owns the full schema.
- Unknown fields should be ignored or rejected.
- You want constructor-based validation and generated output from declared properties.

Use Propigator when:

- You need to preserve unknown fields.
- You are building downstream tooling for someone else's schema.
- The schema is extensible or versioned.
- You want a typed read-only view over a format object.

## Current Limitations

- Propigator wraps object/map payloads, not arbitrary top-level scalar values.
- YAML element conversion is intentionally simple: individual values are rendered through YAML and decoded again with `yamlkt`.
- Delegated properties are runtime accessors. They are not constructor properties and do not appear as separate generated serialization fields.
- `validate()` checks only what your override reads.

## Contributing

External contributions are greatly appreciated.
Please observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).

---

<p align="center">
The Apache License does not apply to the logos (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
