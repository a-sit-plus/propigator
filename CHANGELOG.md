# Changelog
### Unreleased
- Added writable member delegates with caller-controlled setter visibility.
- Added automatic validation for non-nullable member delegates.
- Added native CBOR keys, including tagged and complex keys.
- Added composable multi-format adapters and the JSON/CBOR convenience set.
- Added per-format property serializers and format-specific whole-object serializers.
- Added immutable downstream specialization examples for JOSE and COSE.
- Split integrated multi-format support into the experimental `multi` module and Modulator-powered `borson` bridge.
- Added an interface-only JOSE/COSE comparison alongside the integrated multi-format tests.
- Temporarily disabled SBOM generation due to incorrect native carrier packaging in SBOMbastic 0.0.3.
- Removed the unfinished YAML module, whole-object slices, read defaults, and strict unions.

### Version 0.0.1
- Initial version
- Supports json and yaml
- Supports custom serializers
- Supports backedProperties and slices
