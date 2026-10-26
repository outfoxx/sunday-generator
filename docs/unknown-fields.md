# Dynamic model fields

All generation targets use the schema to choose dynamic field storage: Kotlin
Sunday and JAX-RS, Swift Sunday, TypeScript Sunday, and Python Sunday and Litestar.

| Schema declaration | Catch-all storage |
| --- | --- |
| `additionalProperties: true` | Enabled |
| `additionalProperties: { ... }` | Enabled, with the declared value constraints |
| `additionalProperties: false` | Disabled for unmatched keys |
| `additionalProperties` omitted | Follows `preserveUnknownFields`, which defaults to `false` |

Declared `patternProperties` always retain matching fields, including when other
additional properties are forbidden. Ordinary map/dictionary entries remain part
of their declared data and are unaffected by this option.

## Configuration

- CLI: `-preserve-unknown-fields` enables storage for implicitly open schemas;
  `-no-preserve-unknown-fields` selects the default disabled behavior.
- Kotlin Gradle plugin: `preserveUnknownFields` defaults to `false` on both the
  generation DSL and task. Set `preserveUnknownFields.set(true)` to enable it.
- Generator API: language options expose `preserveUnknownFields: Boolean = false`.
  Python's model renderer uses the same default.
- The existing `KotlinTypeRegistry.Option.PreserveUnknownFields` remains source
  compatible. The generation option controls the default for implicitly open
  models; explicit schema declarations take precedence.

These are generation-time settings. Regenerate models to change the behavior.

## Validation and access

Both settings enforce closed schemas, pattern matches and dynamic-value
constraints during decoding. Every matching pattern applies, including patterns
matching declared properties. A closed schema can still permit pattern-matched
keys. Implicitly permitted fields are discarded when preservation is disabled;
explicitly declared dynamic fields are retained. Declared properties, aliases and discriminator fields retain their
normal behavior.

OpenAPI and AsyncAPI object schemas can also close unevaluated fields with
`unevaluatedProperties: false`. An outer `allOf` closure includes inherited fields,
so they do not need to be redeclared beside the closing keyword. Explicit
`additionalProperties: true` or a value schema already evaluates remaining keys
and keeps those keys allowed. Schema-valued `unevaluatedProperties` is not mapped
to dynamic-value validation.

Models selected for storage expose the following language APIs.

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
`model_dump` and `model_copy` retain it. Implicitly open models without storage
ignore extras after validation. Closed models continue to forbid extras.

Generated storage names receive a suffix when a schema property would collide.
Dynamic data never replaces declared fields when encoding. Unknown discriminator
fallback bodies remain the explicit raw payload of that fallback variant.

## Inheritance and migration

A child can introduce storage with `additionalProperties: true` even when its
parent has none. A closed class does not introduce catch-all storage. If an
ancestor already declares storage, the inherited member remains part of the
class API; closed descendants still reject unknown input keys. Closing a child
does not change the generated API of independently used ancestors.

Regenerating with the new default removes catch-all members and initializer
parameters from implicitly open models. Enable `preserveUnknownFields` to retain
that storage, or opt in on selected schemas with `additionalProperties: true`.
Explicitly closed models no longer introduce storage even with the option enabled.
These changes can require updates to code that accesses catch-all members.

Compile-backed runtime tests cover RAML, OpenAPI, AsyncAPI and composed sources,
with preservation enabled and disabled. Additional OpenAPI 3.1 tests cover closed
schemas with patterns and overlapping constraints.

## Freeform object schemas

An OpenAPI object with no declared or pattern properties and
`additionalProperties: true` maps to a dictionary, whether declared inline or
referenced by name:

| Target | Dictionary type |
| --- | --- |
| Kotlin (Sunday and JAX-RS) | `Map<String, Any?>` |
| Swift | `[String: AnyValue]` |
| TypeScript | `Record<string, unknown>` |
| Python | `dict[str, Any]` |

Swift uses PotentCodables `AnyValue` to retain Codable support. `AnyValue.nil`
represents a null entry, so dictionary values do not need an additional Optional.
Generated model encoding preserves nested JSON objects with Foundation's `JSONEncoder` as well.
Null dictionary entries are valid. The containing property remains independently
optional or nullable according to its schema: omitting an optional property is
valid, while an explicit null requires a nullable schema. String-encoded objects
such as `"{}"` are rejected.
