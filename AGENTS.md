# Sunday Generator Test Invariant
- Generated language code must compile before any test inspects it, snapshots it, or compares it to expected output.
- Do not snapshot manually rendered `FileSpec`, `TypeSpec`, `ModuleSpec`, `CodeBlock`, strings, or files unless that exact generated source has passed the relevant language compiler and is recorded by `CompiledGeneratedSources`.
- Before editing generator tests, read the KDoc/comments in:
  - `generator/src/test/kotlin/io/outfoxx/sunday/generator/tools/CompiledGeneratedSources.kt`
  - `generator/src/test/kotlin/io/outfoxx/sunday/generator/tools/SnapshotAssertions.kt`
  - The relevant language `TypeCompiler.kt`
- When adding a generator output, source frontend, or new snapshot helper, add or keep compile-backed coverage for every source kind it supports: RAML, OpenAPI, AsyncAPI, and composed inputs.
- The invariant test `GeneratedCodeSnapshotInvariantTest` exists to prevent bypassing the compile-backed snapshot helpers. Do not weaken it unless replacing it with a stricter compile-first gate.
