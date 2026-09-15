# OpenAPI SDK compatibility verification

Verified on September 14, 2026, using the candidate generator built from the
`codex/restore-openapi-sdk-compatibility` branch, based on main commit
`5692b534ec6d8b901cdd9d87a7c49def16c3c161` (beta.28).

## Inputs and isolation

The four TurnPost SDKs were regenerated in temporary checkouts using their
production generation arguments and committed aggregate OpenAPI specification.
The specification is identical in each checkout:

- Platform commit: `00b7ba3d026e0a1cee0984ba491d002776074174`.
- SHA-256: `6ac03db18345fc6701a248cbe3624a1b45a04b2a04c016241ee4415dda2282cf`.
- All 907 reference values are local.
- The comparison baseline is the previously compiled beta.27 output, which
  matches the checked-in generated sources at the commits below.

| SDK | Pinned checkout commit | Generated files | Changed from beta.27 |
| --- | --- | ---: | ---: |
| Swift | `a0b65a6a3ebc4293e5bf0a8905f32679558fb8c1` | 494 | 146 |
| Kotlin | `b53e8ddc9f9c13046b99bf44105c782dbcbaca6c` | 482 | 63 |
| TypeScript | `3b10a8078c03dd69ca7eead87661e22b4e39b25a` | 483 | 57 |
| Python | `939ee0373188ec6933e5433054c54f739f38f77d` | 14 | 4 |

No generated source files were added or removed. Sources were compared only
after compilation. Original working-copy HEADs, status hashes, and tracked-diff
hashes are unchanged. SDK specification and dependency/version pins are unchanged;
beta.28 has not been retagged or replaced.

All commands used `-aggregate-services -aggregate-service-name TurnPostAPI`
with `spec/turnpost-api.yaml`. Additional production arguments were:

- Swift: `swift/sunday -no-broker -no-add-generated-header`.
- Kotlin: `kotlin/sunday -pkg cloud.turnpost.sdk -use-jakarta-packages`.
- TypeScript: `typescript/sunday -no-add-generation-header -import-style node-next`.
- Python: `python/sunday -package turnpost_api`.

## Corrected behavior and retained differences

`EntityDetails.currentAsset` uses the canonical `CurrentAsset` type. The synthetic
`EntityDetailsCurrentAsset` is absent. Rendered and refused payloads decode into
their proper variants and retain their subtype fields and discriminator values
when serialized in all four SDKs.

Compatible inherited refinements retain their parent relationship and canonical
enum types. Compiler-backed fixtures cover `FactEditOp`, `BaseNarrativeChangeEvent`,
and `HttpProblem`, including Kotlin assignability, the existing Swift protocol
representation, child constraints, defaults, and unchanged parent metadata.

The remaining source changes fall into these verified categories:

- **Wire restrictions and requiredness:** enum subsets, scalar constants, and
  supported bounds are enforced without replacing inherited enum types. Fixtures
  distinguish absent values, null, zero, and false and verify that unconstrained
  tolerant enums remain tolerant.
- **Defaults:** omitted fields receive declared scalar defaults, including the
  fixture's count of 20, zero/false controls, SDK provenance fields, and problem
  details. Explicit null is handled separately from omission.
- **Reference metadata:** referenced identifier patterns and examples are retained.
  TypeScript scalar-alias parameter validation preserves optionality; actual SDK
  calls accept omitted filters and reject supplied invalid identifiers.
- **Response identities:** the 400, 404, and 422 unions for apply/preview entity
  edits have distinct generated names. Their branches no longer overwrite each
  other. Runtime checks confirm the complete Python union memberships; all
  affected Kotlin and Swift declarations compile.
- **Headers:** Python `Location` response headers use string decoders instead of
  unrestricted object decoders. A runtime check accepts a URL string and rejects
  an object.
- **Discriminators:** Kotlin preserves `EntityIdentityUpdate.type` for its required
  constructor field. TypeScript uses ordinary unions for tolerant enum codecs so
  decoding and encoding both work with the SDK's unchanged Zod 4.3.6 pin.
- **Documentation:** use-site descriptions and examples override referenced
  declarations locally. Of the 146 changed Swift files, 102 differ only in this
  documentation. The event type and actor descriptions match the corresponding
  properties in the pinned specification.
- **Inherited metadata:** Python carries inherited optional-field null checks and
  `additionalProperties` behavior into descendants. Property order and Kotlin
  initializer layout changes accompany the shared field analysis and validation
  code; they do not duplicate stored fields or constructor parameters.

The existing structural field overrides used by AsyncAPI keep their concrete
payload types. The compile-first source-format matrix covers RAML, OpenAPI,
AsyncAPI, and composed inputs.

## Follow-up compatibility review

The four follow-up fixes preserve inherited inline object and union declaration
identities across aliases and ancestor chains, render typed Swift formatted
defaults consistently, validate Python list uniqueness before coercion, and keep
Swift patch omission/null operations independent of ordinary field constraints.

Regression tests cover declaration order, occupied names, alias declarations,
conversion-local recursion state, effective requiredness, unchanged parent
metadata, UUID/URL/date/base64 defaults, malformed formatted literals, and patch
set/delete/unchanged behavior. Python checks include constructor and wire aliases,
validated defaults, nested arrays/objects, numeric equivalence, boolean distinctions,
ordering, and existing set behavior. The remote fixture carries inherited inline
fields, unique lists, and UUID/timestamp defaults through captured generation.
An end-to-end OpenAPI regression also verifies JSON-encoded array defaults with
nested objects, strings, nulls, and booleans and rejects duplicate default values.

After those four follow-up fixes, all four isolated SDK workflows were rerun. Their
compiled generated sources are byte-for-byte identical to the preceding
compatibility candidate, so the beta.27 difference counts and explanations above
remain unchanged. The new regression fixtures exercise cases absent from the
pinned production specification.

Swift's existing non-discriminated value hierarchies remain structs; tests verify
that their inherited declaration types are interchangeable. Class/protocol
assignability checks remain in the existing hierarchy fixtures. Enclosing-parent
cycles are covered at the converter level; compiler fixtures use standalone
recursive nodes. General Swift Sendable propagation through recursive inheritable
classes and mutual builder recursion in the minimal TypeScript test runtime are
outside these four fixes.

## Scalar restrictions and patch payloads

Python scalar restrictions now run through field validation for constructor
names, wire aliases, dictionary/JSON input, and declared defaults. Inherited
restrictions remain effective in descendants. Omission without a default keeps
the existing internal `None` placeholder; explicit null is checked as input.
Numeric and boolean scalar aliases render typed defaults before validation.
Discriminator literals retain Pydantic's variant selection and check their
restrictions afterward, avoiding its prohibition on before validators for tags.
The scalar and unique-list validators share decorator formatting for long field
lists.

Kotlin Sunday validates the payload of `PatchOp.Set` at construction/decoding
boundaries, while preserving `None`, permitted deletion, enum wire types, and the
runtime's explicit-null behavior. Patch constructor defaults remain `none()` even
when the schema declares a default. Inherited constructor fields preserve their
actual ordinary or patch representation. Tests cover numeric bounds, including
RAML boolean exclusivity modifiers and numeric OpenAPI bounds, ordinary models,
and Kotlin JAX-RS controls. Mutable assignment behavior remains unchanged.

The new runtime fixtures cover excluded defaults, empty restrictions, zero/false,
tolerant enum aliases, required and omitted fields, discriminator inheritance,
and patch set/delete/unchanged operations. The OpenAPI-to-Python regression
reproduces the original constructor-name and invalid-default bypasses.

All four isolated SDK build/test/package workflows passed again with these
changes. Compared with the preceding compatibility candidate, Swift's 494,
Kotlin's 482, and TypeScript's 483 generated sources are byte-for-byte unchanged.
Python changes only `turnpost_api/models.py` among its 14 sources: restriction
checks move from raw dictionaries to field validators, with the corresponding
imports. The declared models, field types, defaults, inheritance, and allowed
value sets remain unchanged. No sources were added or removed; the beta.27
difference counts above are unchanged. The alias-default and patch-specific
regressions exercise cases absent from the pinned production specification.

## Scalar wire validation and Swift defaults

TypeScript enum refinements now validate length and pattern against the primitive
wire schema before composing it with the canonical enum schema. Formatted scalar
restrictions likewise compare wire values, while retaining the selected runtime
codec. Bidirectional pipes enforce the restrictions during decoding and encoding;
optional omission, explicit null, and validated prefaults remain distinct. Tests
cover ordinary and tolerant enum aliases, URL/date-time/buffer inputs, and numeric
and boolean restrictions. A separately compiled fixture also passes JSON and CBOR
decoding/encoding with the actual Sunday runtime and the SDK's locked dependencies,
including URL tags, epoch-millisecond timestamps, and raw buffers.

Python accepts supported typed UUID, temporal, and URL inputs by serializing them
for wire comparison only. Pydantic still receives the original value. Tests cover
constructor names, aliases, dictionary/JSON validation, defaults, nested inheritance,
exact primitive strings, and the existing encoded-byte input convention. Boolean
and numeric comparisons remain distinct; no general string coercion is added.

Swift and Kotlin share numeric-bound normalization. Boolean exclusivity modifiers
are interpreted with their associated bounds, while numeric exclusivity remains
independent. Malformed bounds and values outside Swift Decimal's representation
fail generation. Swift validates optional defaults against effective child
restrictions before rendering their typed literals; errors identify the model,
wire property, default, and failed assertion. Runtime tests verify inclusive and
exclusive boundaries, valid omitted-field defaults, and unchanged ordinary/patch
behavior. Negative generation tests cover excluded and malformed defaults.

All four isolated SDK build/test/package workflows passed after these five fixes.
Sources were compared after compilation against the immediately preceding
compatibility candidate:

| SDK | Generated sources | Changed sources | Explanation |
| --- | ---: | ---: | --- |
| Swift | 494 | 0 | The pinned specification has no newly rejected defaults or boolean-bound cases |
| Kotlin | 482 | 0 | Shared bound normalization preserves existing emitted constraints |
| TypeScript | 483 | 23 | Scalar and enum restriction expressions use the shared bidirectional wire-validation stage |
| Python | 14 | 1 | `models.py` adds typed wire comparison, its imports, declaration-format dispatch, and validator line wrapping |

No generated sources were added or removed. Every TypeScript difference is inside
a schema validation expression; model declarations, inheritance, defaults, and
service interfaces are unchanged. An AST comparison confirms that Python model
declarations, field types, defaults, decorators, and unrelated validators are
unchanged. The beta.27 difference counts and categories above remain unchanged.
The shared remote fixture includes enum and formatted scalar restrictions through
CLI, online/offline, and parallel captured generation. The standalone fixture also
passes configuration-cache reuse without a second generation-time fetch.

## Integer defaults and byte restrictions

Swift integer defaults now use exact integer literals after alias resolution and
effective child validation. Decimal/exponent spellings, negative zero, and both
signed 64-bit boundaries compile and produce identical constructor and decoder
defaults. Malformed, fractional, and overflowing defaults report the model, wire
property, and original value during generation. Required fields, explicit null,
floating-point defaults, and patch omission/deletion retain their behavior.

Kotlin Sunday and JAX-RS compare `format: byte` restrictions against padded base64
without line breaks while retaining `ByteArray` storage and existing serialization.
Compiled runtime fixtures verify direct constructors, Jackson decoding/encoding,
inherited constants and enum restrictions, aliases, empty and non-ASCII bytes,
encoded length/pattern bounds, and `PatchOp.Set` values. A long Kotlin-specific
payload verifies that the wire projection inserts no MIME line breaks. Omission,
permitted deletion, raw-binary storage, and unconstrained parents are unchanged.

The shared local/remote fixture carries inherited byte restrictions and integer
defaults through all four language compilers, CLI export, and parallel captured
Gradle generation. A decimal-spelling variant exercises Swift generation and
online/offline IR export; the common fixture retains ordinary integer spelling
for the other emitters' existing default parsing.

All four isolated SDK build/test/package workflows passed after these two fixes.
The post-compilation comparison against the preceding compatibility candidate
found zero changed, added, or removed sources: Swift 494, Kotlin 482, TypeScript
483, and Python 14. The pinned production specification does not exercise the new
integer-spelling and byte-restriction cases. The beta.27 difference counts and
verified explanations above remain unchanged. Original SDK working-copy HEADs,
status hashes, tracked diffs, and pinned specifications were verified unchanged.
The standalone Gradle fixture also passed online/offline configuration-cache
reuse and captured generation, including inherited byte-storage assignability.

## Inherited storage, Python validation, and Swift decoding defaults

Composition checks every parent's declaration before retaining inheritance.
Distinct named enums, objects, and unions cannot share inherited storage merely
because their contracts overlap. Conflicting declarations use the existing
flattened projection while retaining their full constraint intersection: the
two-parent enum regression accepts only `"b"`, in either parent order. Shared
ancestors, canonical aliases, and compatible primitive or collection declarations
retain inheritance; a shared ancestor's stronger requiredness remains effective
in either parent order. Recursive collection comparisons terminate without expanding
their elements. Shared emitter analysis diagnoses conflicting retained IR
declarations instead of silently selecting the last parent.

Python integer defaults resolve declaration aliases and use exact decimal
conversion. Integral decimals, exponents, negative zero, and integers beyond the
signed 64-bit range produce ordinary Python integer literals. Fractional,
nonfinite, and malformed defaults report the model, wire property, and original
value. Runtime checks cover inherited defaults and their effective restrictions.

Python enum length and pattern validation checks the parsed enum's wire value
and returns the original enum instance. Each distinct constrained-string adapter
is constructed once per module. Runtime tests cover wire and typed inputs,
constructor aliases, dictionary/JSON validation, descendants, tolerant unknown
values, validated defaults, null handling, and retained JSON Schema metadata.

Swift real class hierarchies use internal typed class properties for inherited
defaults that vary by descendant. The original declaring decoder selects the
dynamic type's fallback only for an absent key, retaining superclass decoding
and storage. The recursive regression verifies defaults `1`, `2`, and `3` across
three levels, an added child default, UUID aliases, constructor/decoder agreement,
rejection of supplied values outside child bounds, required-field behavior, and
nested-object isolation. Parent defaults and explicit-null behavior are unchanged.

All four isolated SDK build/test/package workflows passed after these four fixes.
The comparison against the preceding compiled compatibility candidate found no
changed, added, or removed sources: Swift 494, Kotlin 482, TypeScript 483, and
Python 14. The pinned production specification does not exercise these remaining
conflict, integer-spelling, enum-string-validation, or recursive decoding-default
cases. The beta.27 differences and their verified explanations above are unchanged.
The shared local/remote fixture passes CLI and parallel Gradle checks with the
flattened enum intersection and inherited defaults. Standalone configuration-cache
verification also passes online/offline reuse and captured generation.

## Multi-parent fields and Kotlin temporal restrictions

Refined multi-parent models now flatten when the parents have different effective
storage-field sets. Both parent orders retain every contributed field and the
complete intersection of requiredness, nullability, validation, defaults, and
allowed values. Same-field parents, shared ancestors, aliases, and single-parent
refinements keep their existing behavior. The shared fixture exercises secondary
fields, a narrowed scalar enum, and a refined default through all four emitters.

Kotlin temporal restrictions project stored dates and times using the matching
Java ISO formatter. Zero seconds, fractional precision, and offsets are preserved
for exact allowed-value and inherited/patch length and pattern checks. The
midnight timestamp regression retains its temporal type and canonical alias;
constructor and Jackson paths use the same restrictions. Custom serializers and
timezone policy are unchanged. Patch defaults remain `none()`, and permitted
deletion bypasses value checks.

Both Kotlin generators pass constructor and Jackson round trips for midnight,
nonzero seconds, nanosecond fractions, UTC/nonzero offsets, local dates/times,
and scalar alias chains. Exact restrictions reject an alternative offset even
when it represents the same instant. Independent inherited and patch length and
pattern checks pass, along with set/none/delete controls. Jackson's Java Time
module is a test-only dependency using the existing Jackson version.

All four isolated SDK build/test/package workflows passed after these two fixes.
The post-compilation comparison found zero changed, added, or removed sources:
Swift 494, Kotlin 482, TypeScript 483, and Python 14. The pinned production
specification does not exercise the new multi-parent or temporal-restriction
cases, so the existing beta.27 differences and their explanations are unchanged.
Original SDK working copies and the pinned specification hashes are unchanged.
CLI and parallel Gradle checks pass with both new scenarios, including offline
and captured inputs. The standalone fixture verifies online/offline
configuration-cache reuse and compilation of secondary-parent fields and
retained temporal inheritance.

## Inheritance through collapsed wrappers

Object wrappers emitted as scalar aliases now share their classification with
composition. Descendants inherit the concrete object declaration through alias
chains, and duplicate canonical parents are removed in declaration order. The
original contributions still supply the effective child contract. Expanded root
aliases and structurally refined wrappers retain their existing object identities.

The regression fixture covers `A -> wrapper B -> child C`, including an additional
alias, duplicate parent paths, inherited constraints, and fields introduced by
the child. Converter coverage includes both OpenAPI dialects, reversed declarations,
recursive properties, and repeated/parallel conversions. All emitters consume
the corrected inheritance references without changing general model lookup.

The shared fixture passes Kotlin Sunday/JAX-RS, Swift, TypeScript, and Python
compilation and runtime checks. Kotlin and Python preserve the canonical parent
relationship, TypeScript retains parent assignability, and Swift retains its
existing inherited-field representation. Child restrictions and field round
trips pass. CLI and parallel Gradle verification retain these references online,
offline, and through captured documents. The standalone fixture also compiles
the alias-to-parent assignment with online/offline configuration-cache reuse.

All four isolated SDK build/test/package workflows pass. Comparison with the
preceding compiled compatibility candidate found zero changed, added, or removed
sources: Swift 494, Kotlin 482, TypeScript 483, and Python 14. Original working
copies, pinned specifications, and dependency/version pins remain unchanged.
The earlier beta.27 differences and their verified explanations still apply.

## Discriminator identities through retained wrappers

The reviewed `Pet → Cat → WrappedCat` schema now compiles with the candidate
and beta.28. Discriminator-selected wrappers remain concrete declarations;
ordinary wrappers still collapse and their descendants inherit canonical parents.
Explicit and inferred mappings, renamed imported targets, inline response
schemas, recursive fields, declaration order, and repeated/parallel conversion
are covered by focused regressions. Literal example data cannot retain a model.

The shared local/remote fixture decodes `{"kind":"cat","name":"Mittens"}`
through the mapped hierarchy and preserves both fields on serialization in
Kotlin Sunday/JAX-RS, Swift, TypeScript, and Python. Kotlin and Python retain the
concrete wrapper subtype; Swift preserves assignability through its protocol
hierarchy. All runtime assertions follow successful compilation.

This fixture also exposed intermediate-parent assumptions in Swift and
TypeScript. Shared nominal ancestry traversal now selects descendant cases
without duplicate dispatch, retaining unmapped siblings. Swift intermediate
parents on these mapped paths use protocols instead of invalid struct bases.
These are focused hierarchy-selection changes, with no IR or emitter interface
changes.

CLI and parallel Gradle tests pass online, offline, and with captured documents.
The standalone configuration-cache fixture passes online/offline reuse and
compiles the mapped wrapper's parent relationship without additional generation
requests.

All four isolated SDK build/test/package workflows pass. After compilation,
all 1,473 generated source files are byte-for-byte identical to the preceding
compiled compatibility candidate: Swift 494, Kotlin 482, TypeScript 483, and
Python 14. The Swift ownership manifest is also unchanged. No source files were
added or removed. Original checkout HEADs, status/diff hashes, specifications,
and isolated dependency/version pins are unchanged; beta.28 remains unchanged.

## Kotlin minimum-long defaults

The shared Kotlin default renderer emits `kotlin.Long.MIN_VALUE` through a
KotlinPoet type reference for the signed 64-bit lower boundary. Other long
defaults retain their existing literal rendering. This fixes the compiler's
“value out of range” error without changing default application or storage types.

Compiler-backed Sunday and JAX-RS regressions cover the lower boundary, its next
value, the upper boundary, zero, and ordinary negative values through direct
properties, scalar references, and inherited defaults. Generated constructor
calls compile before runtime assertions verify omitted arguments and JSON keys,
exact integer round trips, supplied values, explicit null, and unchanged parent
defaults. A model named `Long` verifies type-reference collision handling.
Existing required-field and patch controls pass.

Focused converter/shared-property checks and CLI/Gradle integration pass. The
isolated Kotlin SDK's `check` and CLI `shadowJar` workflow passes using its pinned
specification, dependencies, and production arguments. After compilation, all
482 generated Kotlin files are byte-for-byte identical to the preceding compiled
candidate; no files were added or removed. Other SDK outputs, working copies,
version pins, and beta.28 are unchanged. The full repository check and coverage
verification pass with the two new Kotlin runtime cases included.

## Four-SDK reverification after the minimum-long fix

Rebuilt the local candidate CLI and regenerated all four isolated SDKs with the
same pinned specification and production arguments. Candidate CLI SHA-256:
`3d3f08c214306fa7bef979139dca7deaf561e0686c2461960d58ce51740bd5d4`.

All build/test/package workflows pass: Swift has 15 passing tests and its one
existing live-stack skip; Kotlin has eight passing tests with a forced fresh
`check` and CLI `shadowJar`; TypeScript has ten passing tests plus typecheck,
build, and package dry run; Python has eight passing tests plus compilation,
wheel/sdist builds, and dependency verification. The focused asset subtype,
wire-value round-trip, inherited enum, and parent-type regressions remain green.

After compilation, all 1,473 generated source files match the preceding compiled
compatibility candidate exactly: Swift 494, Kotlin 482, TypeScript 483, and Python
14. No source files were added or removed; the Swift ownership manifest is also
unchanged. Fresh before/after audits confirm that original SDK working copies,
specifications, dependency pins, and version pins were not modified.

## PR #213: inherited constraint intersections and numeric multiples

Compatible parent declarations now combine their effective restrictions even when
an IR child has no local property override. Both parent orders retain lower and
upper bounds, requiredness, nullability, allowed values, uniqueness, and decimal
multiples. Unsupported assertion intersections identify the model/property.
Python explicitly redeclares combined field metadata where Pydantic would otherwise
select one parent's constraints.

Kotlin Sunday and JAX-RS enforce `multipleOf` using exact decimal remainders. Swift
checks decimal digits with integer long division, avoiding rounded quotients and
exponent overflow. Compiler-backed tests cover both parent orders in all four
languages, aliases, omitted defaults, valid/invalid integer and fractional values,
and Kotlin/Swift patch omission, deletion, and set operations. Swift also rejects
a near-multiple with 38 significant digits and accepts multiples whose quotient
would exceed Foundation Decimal's exponent range.

The shared remote fixture includes `multipleOf` and passes CLI, parallel Gradle,
offline, captured-document, and standalone configuration-cache verification.
All four isolated SDK build/test/package workflows pass again using the same
pinned specification and production arguments. After compilation, all 1,473
source files remain byte-identical to the preceding candidate (Swift 494, Kotlin
482, TypeScript 483, Python 14). Original SDK working-copy status is unchanged.
No dependency or version pins were edited.

Candidate CLI SHA-256:
`35855e515d44dad9f273bda8e33feae5a4c89c7bd26675a225052c592cc2e7d9`.
Specification SHA-256:
`6ac03db18345fc6701a248cbe3624a1b45a04b2a04c016241ee4415dda2282cf`.

## RAML array-item numeric validation

Shared declaration analysis now distinguishes numeric scalars from numeric collection
items through scalar and array aliases, preserving element nullability. Kotlin
Sunday/JAX-RS and Swift apply item `multipleOf` and accompanying numeric bounds to
each non-null element. Size and uniqueness checks remain on the collection; normal
list/set storage and decoding are unchanged. Empty collections pass element checks,
and patch omission/deletion still bypass supplied-value validation. Unsupported
numeric targets identify the affected property during generation.

A RAML frontend regression and direct IR controls compile and run in Kotlin
Sunday/JAX-RS and Swift. They cover empty, zero, negative, fractional, nullable,
aliased, inherited, optional, sized, unique, and patch collections. Existing scalar
multiple controls and Python/TypeScript inherited-constraint runtime checks pass.
Frontend extraction and the existing RAML item-facet convention remain unchanged.

All four isolated SDK build/test/package workflows pass using the same pinned
specification and production arguments. After compilation, all 1,473 generated
source files remain byte-identical to the preceding compiled candidate (Swift
494, Kotlin 482, TypeScript 483, Python 14); Swift's generated-file manifest is
also unchanged. Original working-copy status and specification, dependency, and
version pins remain unchanged. CLI/Gradle online/offline and captured-generation
checks pass, as does standalone parallel configuration-cache reuse.

Candidate CLI SHA-256:
`839b7ca0becb5920ded702492da8d23aa07e2e96b0052c63b7c694a634ed9337`.

## Swift numeric range during multiple validation

Swift numeric validation now retains Decimal-supported precision while accepting
finite Double values outside Decimal's range. The fallback parses the Double's
canonical decimal representation into normalized sign, digits, and exponent.
Exact divisibility, accompanying bounds, and numeric uniqueness operate on that
representation without narrowing it back to Decimal. Each collection element is
decoded independently, preserving high-precision near-multiple rejection even
when another element requires the fallback. Normal stored types and decoding,
optional omission, nullable elements, aliases, inheritance, and patch operations
retain their existing behavior. Constraint-literal representability limits are
unchanged; fallback validation does not recover original JSON numeric tokens.

Compiler-backed Swift regressions accept and round-trip `1e200`, negative wide
values, and finite Double boundaries; reject large nonmultiples, subnormal
nonmultiples, nonfinite inputs, and excluded bounds; and retain exact Decimal
near-multiple rejection in mixed-range arrays. Alias, inheritance, collection
uniqueness, set, optional, and patch controls pass. The original RAML CLI
reproduction now compiles and decodes `1e200` successfully.

CLI/Gradle integration and standalone online/offline configuration-cache reuse
pass. All four isolated SDK build/test/package workflows pass with no differences
in the 1,473 generated source files or Swift's generated-file manifest. Original
working-copy status and specification, dependency, and version pins are unchanged.

Candidate CLI SHA-256:
`d0aeb9ee570fcb97db1d12f38cb11126638c7b166bfd86e4853740a4d1f34a88`.

## Validation

| Scope | Result |
| --- | --- |
| Swift SDK | Build and test compilation; 15 passing tests and one existing live-stack test skipped; package manifest and release-version fixture pass |
| Kotlin SDK | `check` and CLI `shadowJar` pass, including eight tests and the asset, inheritance, and identity-update regressions |
| TypeScript SDK | Typecheck, build, ten tests, and package dry run pass with unchanged locked dependencies |
| Python SDK | Compile/import checks, eight tests, wheel/sdist build, and dependency checks pass |
| Generator focused checks | Analysis/converter/IR tests and compiler-backed Kotlin Sunday/JAX-RS, Swift, TypeScript, and Python runtime checks pass |
| CLI and Gradle | Shared local/remote fixture, online/offline equivalence, parallel captured generation, and dependency-change checks pass |
| Configuration cache | Standalone fixture passes online and offline reuse, default network rejection, and captured generation without extra requests |
| Full generator gate | `ktlintCheck`, `check --parallel` (including coverage verification), and `git diff --check` pass |

The full repository check passed 1,093 generator tests, 90 CLI tests, and 18 Gradle
plugin tests. One existing in-process TestKit configuration-cache test was skipped
under its Java-agent restriction; the standalone fixture verified that behavior
online and offline.

The Swift live-stack test requires `TURNPOST_SDK_IT_ENABLED=1` and a running
TurnPost Compose stack. Its existing skip is retained. The release-version test
uses a temporary local bare Git remote, with file-only transport and signing
disabled only for its temporary test commits.

The SDK checks use the locally built candidate CLI, not a newly published beta.
Release publication remains a separate step after verification.
