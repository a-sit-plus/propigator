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
| `core` | Format-neutral backing, delegates, and validation |
| `json` | JSON-backed objects and delegates |
| `cbor` | CBOR-backed objects and delegates, including native CBOR keys |
| `multi` | Experimental integrated multi-format backing |
| `borson` | Experimental JSON/CBOR adapters for `multi`, automatically supplied by Modulator |

`json` and `cbor` each depend on `core`, but not on each other. Neither requires the experimental multi-format modules.

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
@Serializable(with = Person.Companion::class)
class Person private constructor(
    backingObject: JsonObject,
    serialFormat: Json,
) : JsonBackedObject(backingObject, serialFormat) {

    val id: String by jsonProperty()

    val displayName: String? by jsonProperty("display_name")

    companion object : JsonBackedSerializerTemplate<Person>(::Person) {
        context(serialFormat: Json)
        operator fun invoke(id: String, displayName: String? = null): Person =
            Person(JsonObject(emptyMap()), serialFormat).validating {
                initBackedProperty(Person::id, id)
                initBackedProperty(Person::displayName, displayName)
            }
    }
}
```

The properties are read-only. Their protected initializers may be consumed exactly once during controlled construction. Non-nullable member delegates are validated automatically after deserialization and when `validating { ... }` completes.

Override `validate()` and call `super.validate()` only for additional semantic constraints.

Defaults are read without changing the backing object or serialized output:

```kotlin
val locale: String by jsonProperty(defaultValue = "en")
```

Downstream modules can add lazy, read-only views without changing the base type:

```kotlin
val Person.locale: String? by jsonProperty("locale")

val Person.displayLabel: String
    get() = locale?.let { "$id ($it)" } ?: id
```

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
@Serializable(with = Claims.Companion::class)
class Claims private constructor(
    backingObject: CborMap,
    serialFormat: Cbor,
) : CborBackedObject(backingObject, serialFormat) {

    val algorithm: String by cborProperty(CborInteger(1))

    val issuer: String by cborProperty()

    companion object : CborBackedSerializerTemplate<Claims>(::Claims)
}
```

The canonical key type is `CborElement`, so integer, tagged, and complex keys remain native CBOR values. String-key and tagged-string overloads cover the common cases.

## Experimental integrated multi-format support

Apply [Modulator](https://github.com/a-sit-plus/modulator) and add both carrier modules:

```kotlin
plugins {
    id("at.asitplus.gradle.modulator") version "0.1.0"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("at.asitplus.propigator:json:<version>")
            implementation("at.asitplus.propigator:cbor:<version>")
        }
    }
}
```

Modulator automatically adds `borson` when both carriers are present. It supplies `JsonObjectFormat`, `CborMapFormat`, and `JsonCborFormats`:

```kotlin
import at.asitplus.propigator.borson.*
import at.asitplus.propigator.common.validating
import at.asitplus.propigator.multi.*

@OptIn(ExperimentalMultiFormatApi::class)
@Serializable(with = XoseHeader.Companion::class)
class XoseHeader private constructor(
    backingObject: Map<*, *>,
    serialFormat: SerialFormat,
) : MultiFormatBackedObject(backingObject, serialFormat, JsonCborFormats) {

    val algorithm: String by multiFormatProperty(
        JsonObjectFormat propertyKey "alg",
        CborMapFormat propertyKey CborInteger(1),
    )

    companion object : MultiFormatBackedSerializerTemplate<XoseHeader>(
        JsonObject.serializer().descriptor,
        JsonCborFormats,
        ::XoseHeader,
    )
}
```

Format sets compose:

```kotlin
val threeFormats = JsonCborFormats + cborThirdFormat
```

Formats that require their own descriptor use the matching serializer:

```kotlin
val serializer = XoseHeader.Serializer.serializerFor(cbor)
val encoded = cbor.encodeToByteArray(serializer, header)
```

Individual formats may also override a property's inferred serializer:

```kotlin
var count: Int by multiFormatProperty(
    JsonObjectFormat propertyKey "count",
    DerObjectFormat.property(
        key = Asn1.Int(1),
        serializer = IntAsAsn1RealSerializer,
    ),
)
```

Known properties may use native keys per format. Unknown values remain lossless in their original format; Propigator does not invent a mapping from arbitrary CBOR keys to JSON strings.

## Limitations

- Backing values must be object/map shaped.
- A backed instance retains its source format; multi-format support is not transcoding.
- The annotated serializer has one default descriptor; use `serializerFor(format)` when a format needs its own.
- Delegated properties are runtime accessors, not generated serialization fields.

---

<p align="center">
The Apache License does not apply to the logos (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
