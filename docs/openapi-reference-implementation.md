# OpenAPI reference implementation

The native reader normalizes references before converting OpenAPI into Sunday IR. Supported reference semantics and retrieval defaults are documented in [the IR format](sunday-ir-format.md).

## Schema traversal

`OpenApiSchemaKeywords` defines recognized schema locations, assertions, and annotation categories. `OpenApiReferenceLocations` adds the supported OpenAPI object relationships. Indexing, dependency discovery, and normalization use those same relationships; schema compatibility uses the same keyword categories. A schema map, list, or single value is traversed only where its parent context permits it. Examples, defaults, constants, enum values, and extension contents remain literal data.

Documentary compatibility and declaration inheritance use separate annotation sets. Adding a documentary keyword must not change whether discriminator or generation metadata is inherited. Dialect-specific reference-sibling policies remain in the resolver and apply before child traversal.

## Conversion-local analysis

`OpenApiReferenceResolution` retains an internal `OpenApiSchemaAnalysis` context. Resolver validation populates its completed composition results, and conversion reuses that context. Its public document and captured-document fields remain unchanged; analysis state is not part of Sunday IR or document snapshots.

An analysis result keeps the normalized schema use, effective conjunction, and optional null-union projection separate. Type selection reads canonical identity from the normalized use, or proves that a reference wrapper has the same effective contract before allocating an inline type. Nullability and parameter metadata read the relevant effective contract, without allocating models or expanding recursive properties. Model projection remains separate from named-model allocation.

Model emission and inheritance share the collapsed-wrapper classification. When a wrapper becomes a scalar alias of an object, descendant inheritance follows that alias chain to the concrete object declaration and deduplicates canonical parents. Effective contracts still come from the original schema contributions. Expanded root aliases that remain object declarations and genuinely refined wrappers retain their nominal identities; emitter lookup semantics do not change.

Completed effective schemas are idempotent inputs to composition. Caches use object identity and belong to one conversion. Unfinished compositions and blocked recursive comparisons are never cached as completed results. Diagnostic provenance travels with `OpenApiSchema`, including after bounds normalization and annotation overlays.

`OpenApiSchemaCompatibility` selects compatible operands once and retains them for annotation merging. Matching structures are compared without dereferencing canonical references, including nested recursive wrappers. Other forms use guarded effective-schema comparison. Positional unions, inherited reference spelling, right-hand documentary overrides, and conservative unsupported-intersection diagnostics retain their existing behavior.

Property and schema-value intersections use the same compatible-schema merge operation as union annotation overlays before resolving a conjunction. Matching recursive references and wrappers retain their canonical structure while documentary annotations are merged. An unproven comparison falls back to the existing intersection path; comparison caches and traversal remain shared within the analysis context.

`GeneratedModelProperties` provides conversion-local inherited-field views to all emitters. Each view separates the original declaration from the effective descendant restriction and uses the wire name to deduplicate fields. Compatible composition retains canonical declaration types; `GeneratedModelProperty.allowedValues` carries additional scalar enum and constant restrictions without changing IR v1. Documentary compatibility still ignores only the established allowlist. Composition also records the declaring model and wire property for inherited refinements, following aliases and ancestor chains. Conversion caches the declaration's type and reserves inline model identities before descending into their fields. Child requiredness, nullability, defaults, and validation do not allocate a second inline object or union.

Multiple inherited declarations are checked together before composition retains a parent relationship. Matching canonical targets, shared inline declarations, and compatible primitive or collection storage can share a field. Distinct named enums, objects, and unions cannot share storage merely because their wire contracts overlap. Conflicts use the existing flattened projection, preserving the full intersection of restrictions. Shared emitter analysis diagnoses incompatible retained declarations instead of selecting the last parent silently. Compatible retained parents also intersect their effective property restrictions, including when the IR supplies no local child override. Lower and upper bounds, requiredness, nullability, allowed values, collection uniqueness, and decimal multiples retain every parent's requirements. Distinct patterns or other assertions that cannot be represented together produce a property-specific diagnostic instead of silently discarding an operand.

Refined compositions also compare the parents' complete effective field sets. Distinct object parents with different sets use the flattened projection so secondary-parent fields cannot be lost or passed to an incompatible superclass constructor. Every field and the complete intersection of constraints, requiredness, nullability, defaults, and allowed values remain in the child, regardless of parent order. Compatible same-field parents, aliases, shared ancestors, and single-parent refinements keep their existing inheritance behavior.

`SwiftModelDefaults` supplies the same typed expression to optional constructor parameters and omitted-field decoder fallbacks. UUIDs, URLs, dates, and base64 byte data are validated during generation; invalid formatted defaults identify their model and property. Temporal literals use UTC when no offset is supplied, midnight for date-only values, and 1970-01-01 for time-only values. Dates are emitted as epoch seconds, independently of the build machine's locale or timezone. Raw binary defaults have no supported encoding and produce a diagnostic.

Swift integer defaults are validated against effective inherited restrictions and then converted exactly to signed 64-bit integer literals for the existing `Int` representation. Decimal and exponent spellings such as `1.0`, `1e3`, and `-0.0` produce `1`, `1000`, and `0`, including through aliases. Malformed, fractional, and overflowing defaults fail generation with the model, wire property, and original literal. Floating-point defaults keep their existing representation; required fields and patch operations do not receive omitted-field defaults.

Python unique-array refinements keep inherited list declarations and use a field validator before coercion. Wire aliases, Python constructor names, and validated JSON-array defaults use that same path. OpenAPI array defaults use JSON text in the existing string-valued IR property metadata, preserving nested strings, objects, nulls, and booleans. Structural comparisons retain ordering, treat numerically equal values as duplicates, and distinguish booleans from numbers, including nested values. Existing set declarations retain their behavior.

Python scalar restrictions also use field validators before coercion. Effective descendant restrictions apply through wire aliases, Python constructor names, dictionary/JSON validation, and declared defaults. Numeric and boolean defaults resolve scalar aliases before selecting their Python literals, so valid defaults also satisfy pre-coercion checks. Discriminator literals use field validators after literal validation, because [Pydantic forbids before validators on discriminator fields](https://docs.pydantic.dev/latest/errors/usage_errors/#invalid-discriminator-validator); their restrictions remain enforced without changing variant selection. Optional omission without a declared default keeps its existing internal `None` placeholder; an explicitly supplied value still undergoes validation. Inherited canonical enum types and scalar wire-value equality are unchanged.

Kotlin Sunday supplies the constructor's actual operation parameters to `KotlinModelConstraints`. Ordinary fields retain their existing checks; patch fields apply those same predicates to `PatchOp.Set.value`. `None` and deletion operations bypass value restrictions and ordinary required-field checks. Patch constructor defaults use `none()` rather than schema defaults, and null decoding remains governed by the Sunday runtime. Validation remains at construction/decoding boundaries rather than mutable assignments. Both Kotlin emitters enforce numeric `multipleOf` with exact decimal remainders, including inherited refinements and Sunday patch set payloads. Swift validates `multipleOf` before decoding inherited storage, using integer long division on decimal digits to avoid rounded quotients or exponent overflow. Zero and negative multiples remain valid; divisors must be positive, and Swift divisors must fit its existing Decimal literal range. Patch omission and deletion bypass value checks. Legacy RAML array-item `multipleOf` metadata is classified through scalar and collection aliases: numeric multiples and accompanying numeric bounds validate each non-null element, while size and uniqueness checks remain on the collection. Lists and sets retain their normal storage and decoding; empty collections pass element validation unless a separate collection constraint excludes them. Unsupported numeric target shapes produce a property-specific generation diagnostic. Swift multiple validation first decodes each value as Decimal and retains its exact decimal representation. When Decimal cannot decode the value, a finite Double supplies its canonical decimal digits and exponent instead; values such as `1e200` therefore remain valid for `multipleOf: 1`. Multiples, accompanying bounds, and numeric uniqueness use the normalized representation without converting it back to Decimal. Each collection element selects its own path, so a wide-range element does not weaken high-precision checks on its neighbors. Fallback validation describes the stored Double value, not original JSON digits outside Decimal support. Existing constraint-literal limits and normal stored-type decoding remain unchanged.

Kotlin `format: byte` restrictions compare the stored `ByteArray` as standard padded base64 without line breaks, matching the default JSON serialization. Allowed string values and inherited or patch string-length/pattern checks use this same wire projection across Sunday and JAX-RS, including scalar aliases. The stored bytes and their serializer remain unchanged. This projection does not apply to raw `binary` or file types.

Kotlin temporal restrictions use the Java ISO formatter corresponding to the stored `LocalDate`, `LocalTime`, `LocalDateTime`, or `OffsetDateTime`. The shared projection retains zero seconds, fractional precision, and offsets for allowed-value and inherited or patch length/pattern comparisons. Alias resolution, Sunday/JAX-RS storage types, serialization settings, and patch omission/deletion are unchanged. Restrictions compare exact ISO wire strings, not instant equivalence; custom temporal serializers and timezone policies remain outside this behavior.

Swift patch constraints validate only supplied non-null values. Omission and null pass through the existing `decodeIfExists` operation semantics: omitted fields are unchanged, null is unchanged for `UpdateOp` and deletion for `PatchOp`. Patch decoding and constructors never insert ordinary field defaults.

Ordinary OpenAPI Reference Objects are already expanded when the converter sees them. Only schema uses retain canonical references, interpreted through `OpenApiSchemaReferences`.

## Presence and nullability on the wire

Optional properties may be omitted; nullability independently determines whether an explicit JSON `null` is valid. Kotlin/JAX-RS and Kotlin/Sunday add getter-level Jackson `NON_NULL` inclusion only to optional, non-nullable model fields when Jackson annotations are enabled. Python emits equivalent Pydantic field-level exclusion metadata, supported by the runtime's Pydantic 2.12 minimum. Empty strings and collections, zero, and false remain present. Nullable aliases and unconstrained values retain null.

Swift ordinary models encode nullable properties with `encode`, preserving explicit null even for required fields, and omit unset non-nullable optional properties with `encodeIfPresent`. Kotlin and Swift still use nullable storage for ordinary optional fields; this does not introduce a separate presence wrapper for optional nullable values. Existing patch operations retain their own omission/deletion representation.

TypeScript uses `undefined` for optional presence and adds `null` only when the schema permits it. Generated Zod schemas use `optional()` for non-nullable optional properties and `nullish()` for nullable optional properties. This tightens previously over-permissive generated types and validation; callers that supplied null to a non-nullable optional field must omit it instead.

## Shared test support

The generator's `testFixtures` source set contains `OpenApiHttpFixture` and reusable `OpenApiReferenceDocuments` fragments. Library, CLI, and Gradle tests share transport setup and schema scenarios while retaining their own contract and build assertions. The fixture supports exact query routes, redirects, and conditional responses to changing documents. Fixtures are test dependencies only, and their variants are excluded from publication.

Run lint, focused resolver/converter tests, compiler-backed fixtures, and CLI/Gradle integration tests before the full `check` task. Generated Kotlin, Swift, TypeScript, and Python must compile before source assertions or snapshots. Keep `GeneratedCodeSnapshotInvariantTest` and coverage for RAML, OpenAPI, AsyncAPI, and composed inputs. Configuration-cache checks also need a standalone Gradle fixture when the existing TestKit Java-agent restriction prevents that check in-process.

CLI export and language commands register the same `OpenApiReferenceOptionGroup`. Each command keeps its existing accessors, flags, validation, help text, and defaults.

## Retrieval boundaries

`OpenApiNetworkPolicy` owns HTTP destination validation and wraps proxy selection for the default loader. Its address and proxy dependencies are injectable internally for deterministic tests. DNS preflight runs only for actual network requests, uses the configured request timeout, and rejects any prohibited address among the returned answers. The address tables derive from IANA's [IPv4](https://www.iana.org/assignments/iana-ipv4-special-registry) and [IPv6](https://www.iana.org/assignments/iana-ipv6-special-registry) special-purpose registries. More-specific globally reachable assignments override their enclosing reservations. Multicast, reserved IPv6 space, and transition mechanisms without established global reachability remain excluded. The JDK client still resolves independently, so this is not a DNS-pinning implementation.

The resolver separately checks each reference's effective retrieval origin before loading a local target and before accepting a registered target from a local document. This prevents resource identifiers, anchors, and captured inputs from bypassing the remote-to-file boundary while keeping embedded identifiers usable. The network opt-in only controls network access and cannot authorize remote-to-file references.

Snapshot URI encoding uses a shared path compatibility check before relativizing. Cross-root and cross-provider targets use the existing absolute `uri` representation; loading remains restricted to captured bodies. Local HTTP fixtures explicitly enable private-network access, and policy regressions exercise the production default separately.

### Scalar wire restrictions and defaults

Inherited enum refinements retain their canonical enum declarations. TypeScript
validates length, pattern, and allowed-value restrictions against scalar wire
values while preserving enum and formatted-value codecs. Checks run on both
decoding and encoding; supplied primitive literals are compared exactly.
Alternative transport representations use the selected runtime codec before
comparison with the JSON scalar contract, without changing its output policy.

Python restriction validators accept UUID, temporal, and URL objects supported
by the declared field as well as wire inputs. Recognized objects are serialized
for comparison only; Pydantic still receives the original input. Strings are
compared exactly, booleans remain distinct from numbers, and encoded byte inputs
retain their existing interpretation. Constructor names, wire aliases, declared
defaults, and inherited restrictions use the same field validators.

Boolean exclusivity modifiers from RAML and OpenAPI 3.0 are interpreted with
their associated numeric bounds: `false` disables the modifier and `true` makes
the bound strict. Numeric OpenAPI 3.1 exclusivity bounds remain supported.
Malformed bounds and numeric literals outside Swift Decimal's representation
produce generation diagnostics instead of invalid generated comparisons.

Swift defaults that violate effective child restrictions fail generation with
the model, wire property, default, and failed assertion. Constructors and omitted
optional-field decoder fallbacks share the validated literal. Required fields,
explicit null, and patch omission/deletion retain their existing behavior.

### Inherited defaults and Python enum validation

Python integer defaults use exact decimal-to-integer conversion after resolving aliases. Integral spellings such as `1.0`, `1e3`, and `-0.0` become ordinary integer literals, with Python's arbitrary-precision range. Fractional, nonfinite, and malformed defaults fail generation with model/property context. Length and count constraints retain their separate integer parsing rules.

Python enum length and pattern refinements validate the enum's wire value after Pydantic has parsed the field, then return the original enum instance. Constrained-string adapters are reused within the generated module; pattern and length metadata remain in the field's JSON Schema. Constructor and wire aliases, validated defaults, tolerant unknown enum values, and inherited restrictions use the same checks.

Swift class hierarchies with differing inherited defaults use internal typed class properties to supply the most-derived fallback inside the original declaring class's decoder. `super.init(from:)` and inherited storage remain intact. The fallback is selected only when a key is absent; explicit null and supplied values retain their decoding behavior. Nested objects choose their own type's defaults. Required fields and patch operations do not acquire default values. Hooks are emitted only for affected declarations and are not part of the public model interface.

### Discriminator-selected wrappers

Contract-preserving wrappers selected by normalized discriminator mappings or
named alternatives of discriminated `oneOf`/`anyOf` schemas remain concrete
object declarations. Their mapped wire values and inheritance identities are
preserved, including when distinct wrappers share the same structural contract.
Other contract-preserving wrappers still collapse to aliases, and their children
inherit the canonical concrete declaration. Literal example and extension data
do not select discriminator variants.

Swift and TypeScript dispatch through selected descendant variants without also
adding the intermediate parent as an overlapping case. Unmapped sibling cases
remain available. Swift intermediate declarations on these mapped paths use its
existing protocol hierarchy representation, preserving parent assignability.
