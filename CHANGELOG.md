# Changelog
### Unreleased
- Renamed the JSON and YAML format-specific APIs to shorter names.
- Replaced the generic `ObjectBacked<V>`/`ObjectBackedValidated` split with an abstract `ObjectBacked` base class that owns `serialFormat`, `backingObject`, validation, equality, hashing, and string rendering.
- Renamed format wrapper state from `rawObject`/`json`/`yaml` to `backingObject`/`serialFormat` for JSON and YAML backed objects.
- Missing non-null delegated properties now throw `NoSuchElementException` instead of `SerializationException`.
- Removed write-through backing logic. Backed properties are now read-only delegates over the preserved backing object.
- Removed the format-agnostic backing codec layer; JSON and YAML wrappers now decode values directly with their configured format instance.
- Updated backed property nullability so nullable fields are declared with nullable Kotlin types.
- Added backed-property defaults through the common delegate layer and `jsonProperty(defaultValue = ...)` for absent JSON keys. Serialization still emits the preserved backing `JsonObject` unchanged.
- Added `JsonObject?.strictUnion(...)` to combine JSON objects while rejecting duplicate keys.
- `JsonBackedSerializerTemplate` now uses the active `JsonDecoder` configuration when creating decoded wrappers instead of accepting a separate `Json` instance.
- Object-backed serializers now compare JSON/YAML configuration content, not format instance identity, before serialization. Serializer modules are intentionally not part of this comparison.

### Version 0.0.1
- Initial version
- Supports json and yaml
- Supports custom serializers
- Supports backedProperties and slices
