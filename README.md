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
[![Java](https://img.shields.io/badge/java-17+-blue.svg?logo=OPENJDK)](https://www.oracle.com/java/technologies/downloads/#java17)
[![Maven Central](https://img.shields.io/maven-central/v/at.asitplus.propigator/core)](https://mvnrepository.com/artifact/at.asitplus.propigator/core)

</div>

Propigator exposes typed Kotlin properties over extensible object-shaped data. Unknown properties remain in the backing object and survive serialization unchanged.

Use ordinary `@Serializable` data classes when you own the complete schema. Use Propigator when downstream modules, protocol extensions, or newer schema versions may add fields that must round-trip losslessly.

## Modules

| Module | Purpose |
|---|---|
| `core` | Format-neutral carrier, backing, flattening, delegates, and validation contracts |
| `json` | Generic JSON carrier envelopes and native JSON property views |
| `cbor` | Generic CBOR carrier envelopes and native CBOR property views |
| `multi` | Experimental format-dispatching carrier envelopes, JSON/CBOR adapters, and advanced delegated backing |

`json` and `cbor` each depend on `core`, but not on each other. Neither requires the experimental `multi` module.

## JSON

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("at.asitplus.propigator:json:<version>")
        }
    }
}
```

```kotlin
@Serializable
data class Person(
    val id: String,
    @SerialName("display_name")
    val displayName: String? = null,
)

val person: JsonBacked<Person> = json.JsonBacked(Person("42", "Arthur"))

val contextualPerson = with(json) {
    JsonBacked(Person("42", "Arthur"))
}
```

`JsonBacked<T>` has one generic custom serializer. For ordinary carriers, `T` is simply
`@Serializable`: its generated serializer handles construction and validation, while the envelope
retains the complete source `JsonObject`. Unknown properties therefore survive unchanged even when
the carrier does not declare them. Constructed envelopes obtain their backing object by encoding
the carrier once.

```kotlin
val decoded = json.decodeFromStringBacked<Person>(
    """{"id":"42","future_claim":{"untouched":true}}"""
)
json.encodeToStringBacked(decoded) // future_claim is retained
```

For extensible protocols, keep the semantic contract separate from its standard serializable
carrier:

```kotlin
interface JoseHeader {
    val algorithm: String
    val type: String?
}

@Serializable
data class StandardJoseHeader(
    @SerialName("alg") override val algorithm: String,
    @SerialName("typ") override val type: String? = null,
) : JoseHeader
```

Downstream carriers can reuse every base field through normal Kotlin delegation while remaining a
flat JSON object:

```kotlin
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = ApplicationJoseHeader.Serializer::class)
data class ApplicationJoseHeader(
    override val base: StandardJoseHeader,
    val applicationClaim: String,
) : JoseHeader by base, JsonFlattened<StandardJoseHeader> {

    @Transient
    override val type: String = requireNotNull(base.type)

    object Serializer : JsonFlatteningSerializerTemplate<ApplicationJoseHeader>(
        ApplicationJoseHeader.generatedSerializer()
    )
}
```

`JsonFlatteningSerializerTemplate` retains the compiler-generated serializer, flattens its `base`
property during encoding, and reconstructs that nested shape internally during decoding. It works
without `JsonBacked`; combining both features additionally preserves unknown source properties.

Concrete backed types can delegate the same semantic interface and expose carrier properties
without `.value`:

```kotlin
@Serializable(with = JsonBackedApplicationJoseHeader.Companion::class)
class JsonBackedApplicationJoseHeader private constructor(
    backed: JsonBacked<ApplicationJoseHeader>,
) : JsonBacked<ApplicationJoseHeader>(backed), JoseHeader by backed.value {

    companion object :
        JsonBackedSerializerTemplate<ApplicationJoseHeader, JsonBackedApplicationJoseHeader>(
            ApplicationJoseHeader.serializer(),
            ::JsonBackedApplicationJoseHeader,
        )
}
```

Additional typed views can read undeclared claims directly from the retained object:

```kotlin
val JsonBacked<JoseHeader>.locale: String? by jsonProperty()
```

Both kinds of property then have the same call-site shape: `header.algorithm` comes from the
carrier interface and `header.locale` comes from the retained backing object.

## CBOR

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("at.asitplus.propigator:cbor:<version>")
        }
    }
}
```

```kotlin
@Serializable
data class Claims(
    @CborLabel(1)
    val algorithm: String,
    val issuer: String? = null,
)

val claims = cbor.CborBacked(Claims("ES256"))
val encoded = cbor.encodeToByteArrayBacked(claims)
val decoded = cbor.decodeFromByteArrayBacked<Claims>(encoded)

val CborBacked<Claims>.applicationClaim: String? by
    cborProperty(CborInteger(-70_000L))
```

`CborBacked<T>` mirrors `JsonBacked<T>`, including explicit-serializer construction, concrete
subclass templates, and element/byte-array helpers. Its retained `CborMap` preserves integer,
tagged, complex, and unknown keys plus map tags. Complex keys are kept out of the generated carrier
decoder but remain untouched in the backing map.

## Experimental integrated multi-format support

Add the integrated multi-format module:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("at.asitplus.propigator:multi:<version>")
        }
    }
}
```

`multi` exposes the JSON and CBOR carrier modules transitively. Ordinary multi-format values use a
single serializable carrier and a generic `BorsonBacked<T>` envelope:

```kotlin
import at.asitplus.propigator.multi.*

@OptIn(ExperimentalMultiFormatApi::class)
@Serializable
data class XoseHeader(
    @SerialName("alg")
    @CborLabel(1)
    val algorithm: String,
)

val jsonHeader = json.BorsonBacked(XoseHeader("ES256"))
val cborHeader = cbor.BorsonBacked(XoseHeader("ES256"))
```

`BorsonFlatteningSerializerTemplate` flattens delegated base carriers in both formats.
`BorsonBackedSerializerTemplate` gives concrete backed subclasses the same direct-property pattern
as JSON and CBOR. Unknown values remain lossless in their native source format.

Backing-only properties may still select native keys and serializers by format:

```kotlin
val MultiFormatBacked<XoseHeader>.applicationClaim: String? by
    multiFormatProperty<String?>(
        JsonObjectFormat propertyKey "application_claim",
        CborMapFormat propertyKey CborInteger(-70_000L),
    )
```

For formats that ordinary carrier annotations cannot express, the advanced
`MultiFormatBackedObject` API remains available. Format sets compose, individual properties may
override their serializer, and format-specific descriptors come from `serializerFor(format)`:

```kotlin
val threeFormats = JsonCborFormats + derFormat

val count: Int by multiFormatProperty(
    JsonObjectFormat propertyKey "count",
    DerObjectFormat.property(
        key = Asn1.Int(1),
        serializer = IntAsAsn1RealSerializer,
    ),
)
```

Propigator never transcodes arbitrary unknown JSON, CBOR, or ASN.1 entries.

## Limitations

- Backing values must be object/map shaped.
- A backed instance retains its source format; multi-format support is not transcoding.
- The annotated serializer has one default descriptor; use `serializerFor(format)` when a format needs its own.
- Advanced delegated properties are runtime accessors, not generated serialization fields.

---

<p align="center">
The Apache License does not apply to the logos (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
