# Dynamic model fields

All generation targets use the schema to choose ordinary model dynamic field storage: Kotlin
Sunday and JAX-RS, Swift Sunday, TypeScript Sunday, and Python Sunday and Litestar.

| Schema declaration | `on` | `off` | `schema` (default) |
| --- | --- | --- | --- |
| `additionalProperties` omitted | Storage | No storage | No storage |
| `additionalProperties: true` or a value schema | Storage | No storage | Storage |
| `additionalProperties: false` | No storage | No storage | No storage |

Declared `patternProperties` always retain matching fields, including when other
additional properties are forbidden. Ordinary map/dictionary entries remain part
of their declared data and are unaffected by this option.

## Configuration

- CLI: `-preserve-unknown-fields on|off|schema` (default: `schema`).
- Kotlin Gradle plugin: `preserveUnknownFields` is a
  `Property<UnknownFieldPreservation>`, defaulting to `UnknownFieldPreservation.Schema`.
  For example, use `preserveUnknownFields.set(UnknownFieldPreservation.On)`.
- Generator API: language options and Python's model renderer expose
  `preserveUnknownFields: UnknownFieldPreservation = UnknownFieldPreservation.Schema`.
- `on` preserves permitted fields on open models by default; explicit closure still
  prevents new catch-all storage. `off` never introduces catch-all storage on ordinary models.
  `schema` requires an explicit `additionalProperties: true` or value schema.
- The existing `KotlinTypeRegistry.Option.PreserveUnknownFields` remains source
  compatible; language generation options control the storage policy.

These are generation-time settings. Regenerate models to change the behavior.

PATCH companions derive their properties and dynamic-field rules from their own
schema and explicit inheritance. Fields declared only by sibling variants do not
create synthetic exclusions, pattern constraints, storage, or catch-all handlers.
For example, `knownAliases` on a character variant has no effect on a prop variant;
on the prop it follows the same unknown-field policy as any other undeclared name.

## Validation and access

All three modes enforce closed schemas, pattern matches and dynamic-value
constraints during decoding. Every matching pattern applies, including patterns
matching declared properties. A closed schema can still permit pattern-matched
keys. Accepted fields are discarded when storage is disabled and a dynamic-value
validation handler is required. Unconstrained Kotlin models without catch-all
storage generate no `@JsonAnySetter`: Jackson's `FAIL_ON_UNKNOWN_PROPERTIES`
setting determines whether unknown input fields fail or are ignored. This also
applies to inherited ordinary models. Other targets discard permitted fields when
storage is disabled. Declared properties, aliases and discriminator fields retain
their normal behavior.

For example, Quarkus applications can enable strict handling with
`quarkus.jackson.fail-on-unknown-properties=true`. Unknown fields then produce
HTTP 400 responses for unconstrained models without storage.

Ordinary models selected for storage expose the following language APIs.

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

## Kotlin problem types

`preserveUnknownFields` does not affect generated Kotlin problem classes in
Sunday or JAX-RS, including Quarkus and the Sunday, Quarkiverse, and Zalando
problem libraries. No separate `additionalProperties` map is generated for problems.
Use the native `extensions` (Sunday) or `parameters` (Quarkiverse/Zalando) API.
Open problems preserve extension parameters in all three modes, even when
`additionalProperties` is omitted. Explicit schema closure, pattern rules, and
value constraints still apply.

Sunday's runtime already captures extensions through an any-setter. Generated
schema-aware bindings retain that behavior and share its extension map.
Quarkiverse extensions use its native parameter map so serialization
and `HttpProblem.builder(existing)` copies retain them. Zalando exposes accepted
extensions through its native parameters API. Ordinary models referenced by
problem properties continue to follow the configured preservation mode.

## Inheritance and migration

In `on` and `schema` modes, a child can introduce storage with
`additionalProperties: true` even when its parent has none. A closed class does not introduce catch-all storage. If an
ancestor already declares storage, the inherited member remains part of the
class API; closed descendants still reject unknown input keys. Closing a child
does not change the generated API of independently used ancestors.

Regenerating with the new default removes catch-all members and initializer
parameters from implicitly open models. Use `preserveUnknownFields = UnknownFieldPreservation.On` to retain
that storage, or opt in on selected schemas with `additionalProperties: true`.
Explicitly closed models no longer introduce storage even with the option enabled.
These changes can require updates to code that accesses catch-all members.
Migrate boolean API/Gradle settings to enum values and CLI flags to the explicit
mode argument: the former `true` behavior is `On`, and the schema-driven `false`
behavior from this change is `Schema`.

Compile-backed runtime tests cover RAML, OpenAPI, AsyncAPI and composed sources,
with all three preservation modes. Additional OpenAPI 3.1 tests cover closed
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
