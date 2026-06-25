# Changelog
### Unreleased
- Removed write-through backing logic. Backed properties are now read-only delegates over the preserved raw object.
- Removed the format-agnostic backing codec layer; JSON and YAML wrappers now decode values directly with their configured format instance.
- Updated backed property nullability so nullable fields are declared with nullable Kotlin types; this keeps the same optional-field functionality without a separate nullable delegate.

### Version 0.0.1
- Initial version
- Supports json and yaml
- Supports custom serializers
- Supports backedProperties and slices
