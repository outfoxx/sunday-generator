# OpenAPI reference implementation

The native reader normalizes references before converting OpenAPI into Sunday IR. Supported reference semantics and retrieval defaults are documented in [the IR format](sunday-ir-format.md).

## Schema traversal

`OpenApiSchemaKeywords` defines recognized schema locations, assertions, and annotation categories. `OpenApiReferenceLocations` adds the supported OpenAPI object relationships. Indexing, dependency discovery, and normalization use those same relationships; schema compatibility uses the same keyword categories. A schema map, list, or single value is traversed only where its parent context permits it. Examples, defaults, constants, enum values, and extension contents remain literal data.

Documentary compatibility and declaration inheritance use separate annotation sets. Adding a documentary keyword must not change whether discriminator or generation metadata is inherited. Dialect-specific reference-sibling policies remain in the resolver and apply before child traversal.

## Conversion-local analysis

`OpenApiReferenceResolution` retains an internal `OpenApiSchemaAnalysis` context. Resolver validation populates its completed composition results, and conversion reuses that context. Its public document and captured-document fields remain unchanged; analysis state is not part of Sunday IR or document snapshots.

An analysis result keeps the normalized schema use, effective conjunction, and optional null-union projection separate. Type selection reads canonical identity from the normalized use. Nullability and parameter metadata read the relevant effective contract, without allocating models or expanding recursive properties. Model projection remains separate from named-model allocation.

Completed effective schemas are idempotent inputs to composition. Caches use object identity and belong to one conversion. Unfinished compositions and blocked recursive comparisons are never cached as completed results. Diagnostic provenance travels with `OpenApiSchema`, including after bounds normalization and annotation overlays.

`OpenApiSchemaCompatibility` selects compatible operands once and retains them for annotation merging. Matching structures are compared without dereferencing canonical references, including nested recursive wrappers. Other forms use guarded effective-schema comparison. Positional unions, inherited reference spelling, right-hand documentary overrides, and conservative unsupported-intersection diagnostics retain their existing behavior.

Property and schema-value intersections use the same compatible-schema merge operation as union annotation overlays before resolving a conjunction. Matching recursive references and wrappers retain their canonical structure while documentary annotations are merged. An unproven comparison falls back to the existing intersection path; comparison caches and traversal remain shared within the analysis context.

Ordinary OpenAPI Reference Objects are already expanded when the converter sees them. Only schema uses retain canonical references, interpreted through `OpenApiSchemaReferences`.

## Shared test support

The generator's `testFixtures` source set contains `OpenApiHttpFixture` and reusable `OpenApiReferenceDocuments` fragments. Library, CLI, and Gradle tests share transport setup and schema scenarios while retaining their own contract and build assertions. The fixture supports exact query routes, redirects, and conditional responses to changing documents. Fixtures are test dependencies only, and their variants are excluded from publication.

Run lint, focused resolver/converter tests, compiler-backed fixtures, and CLI/Gradle integration tests before the full `check` task. Generated Kotlin, Swift, TypeScript, and Python must compile before source assertions or snapshots. Keep `GeneratedCodeSnapshotInvariantTest` and coverage for RAML, OpenAPI, AsyncAPI, and composed inputs. Configuration-cache checks also need a standalone Gradle fixture when the existing TestKit Java-agent restriction prevents that check in-process.

CLI export and language commands register the same `OpenApiReferenceOptionGroup`. Each command keeps its existing accessors, flags, validation, help text, and defaults.
