# Dynamic model fields

All generation targets preserve schema-permitted dynamic fields by default:
Kotlin Sunday and JAX-RS, Swift Sunday, TypeScript Sunday, and Python Sunday and
Litestar. This includes fields matching `patternProperties` and other fields
allowed by `additionalProperties`. The policy applies to generated model
objects; ordinary map/dictionary entries remain part of their declared data.

## Configuration

- CLI: preservation is enabled without a flag. `-preserve-unknown-fields`
  explicitly enables it; `-no-preserve-unknown-fields` disables it.
- Kotlin Gradle plugin: `preserveUnknownFields` defaults to `true` on both the
  generation DSL and the task. Set `preserveUnknownFields.set(false)` to disable.
- Generator API: the language generation options expose
  `preserveUnknownFields: Boolean = true`. Python's model renderer accepts the
  same default-enabled argument. Use `false` for an explicit override.
- The existing `KotlinTypeRegistry.Option.PreserveUnknownFields` remains source
  compatible. Preservation now follows the generation options, so an explicit
  `preserveUnknownFields = false` takes precedence, and omission from a registry
  option set no longer disables preservation.

These are generation-time settings. Regenerate models to change the behavior.

## Validation and access

Both settings enforce closed schemas, pattern matches and dynamic-value
constraints during decoding. Every matching pattern applies, including patterns
matching declared properties. A closed schema can still permit pattern-matched
keys. With preservation disabled, accepted dynamic fields are discarded after
validation. Declared properties, aliases and discriminator fields retain their
normal behavior.

Kotlin Jackson models expose a read-only `additionalProperties` map and
`setAdditionalProperty` method. Data-class copies retain these fields with
independent map updates. Automatic decoding and encoding require the generated
Jackson annotations. A custom serializer remains responsible for models
generated without Jackson annotations.

Swift models expose an `additionalProperties: [String: AnyValue]` dictionary and
accept it as a default-empty initializer parameter. Generated `with…` methods
retain it. Encoding validates the dynamic contract and writes the fields at the
original JSON level, including dictionaries, arrays and explicit nulls. Typed
event cases retain envelope fields as well as their typed payloads.

TypeScript plain models retain dynamic keys directly. Generated class models
expose a read-only `additionalProperties` record, accepted by their initializer
and retained by `copy`. Schema codecs preserve fields in both directions;
prototype-like keys are handled as own data properties.

Python models use Pydantic's extra-field storage, accessible through
`model_extra`; normal construction and `model_validate` populate it, and
`model_dump` and `model_copy` retain it. Disabled preservation uses validated
input followed by ignoring extras. Closed models continue to forbid extras.

Generated storage names receive a suffix when a schema property would collide.
Dynamic data never replaces declared fields when encoding. Unknown discriminator
fallback bodies remain the explicit raw payload of that fallback variant.

## Migration

Regenerated SDKs may now emit permitted fields that older SDKs silently discarded.
Models may gain storage/accessor members and defaulted constructor parameters.
Use the disabling option when deliberately retaining the previous lossy behavior;
invalid dynamic fields and forbidden keys are still rejected.

Compile-backed runtime tests cover RAML, OpenAPI, AsyncAPI and composed sources,
with preservation enabled by default and explicitly disabled. Additional
OpenAPI 3.1 tests cover closed schemas with patterns and overlapping constraints.
