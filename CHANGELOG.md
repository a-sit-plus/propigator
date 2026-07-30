# Changelog
### Unreleased
- Replaced JSON-backed subclasses, delegated members, per-class factories, and per-class serializers
  with ordinary serializable carriers inside one generic `JsonBacked<T>` envelope.
- Added carrier contracts for compile-time downstream schema feedback and nullable-to-non-null
  property refinement.
- Retained unknown JSON properties losslessly even when the caller's `Json` rejects unknown keys.
- Added reusable JSON flattening for serializable carriers that reuse a base carrier through Kotlin
  interface delegation.
- Added a reusable serializer template for concrete `JsonBacked` subclasses that delegate carrier
  interfaces and expose both carrier and backing-only properties directly.
- Added concise `Json` element and string helpers for generic backed envelopes.
- Added explicit-serializer construction for `JsonBacked` envelopes.
- Reworked CBOR around ordinary serializable carriers in a generic `CborBacked<T>` envelope while
  preserving native keys and tags.
- Added generic `MultiFormatBacked<T>` and `BorsonBacked<T>` carrier envelopes, cross-format
  flattening, concrete subclass templates, and concise JSON/CBOR helpers.
- Pulled native-envelope and flattened-carrier contracts into `core`.
- Made envelope construction format-owned, with receiver and context-parameter forms, and moved
  concrete backed construction to context-aware companion factories.
- Added writable member delegates with caller-controlled setter visibility.
- Added one-shot, type-safe protected initialization for read-only backed properties.
- Added automatic validation for non-nullable member delegates.
- Added native CBOR keys, including tagged and complex keys.
- Added composable multi-format adapters and the JSON/CBOR convenience set.
- Added per-format property serializers and format-specific whole-object serializers.
- Made backed serializer templates open so companions can inherit them directly.
- Added immutable downstream specialization examples for JOSE and COSE.
- Split integrated multi-format support into the experimental `multi` module and Modulator-powered `borson` bridge.
- Added delegated property defaults that do not alter serialized backing objects.
- Temporarily disabled SBOM generation due to incorrect native carrier packaging in SBOMbastic 0.0.3.
- Removed the unfinished YAML module, whole-object slices, and strict unions.

### Version 0.0.1
- Initial version
- Supports json and yaml
- Supports custom serializers
- Supports backedProperties and slices
