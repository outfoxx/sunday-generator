# Kotlin/Sunday IR Path

`KotlinSundayGenerateCommand` is the canonical CLI entrypoint for Kotlin/Sunday output.

The generator path is:

- `GeneratedApiIrExporter` reads RAML, OpenAPI, and AsyncAPI sources into `GeneratedApi`.
- For RAML inputs only, `APIProcessor` produces the processed RAML document and `ShapeIndex`.
- `RamlToGeneratedApi` converts that processed RAML result into `GeneratedApiFragment`.
- `KotlinSundayIrGenerator` reads `GeneratedApi` for service, operation, model, and problem generation.
- `KotlinTypeRegistry` remains the output collector for generated Kotlin files.

Kotlin/Sunday no longer has a separate AMF-backed service emitter or wrapper generator. The CLI and Gradle plugin export source specs to IR and call `KotlinSundayIrGenerator` directly.

The remaining AMF dependency is the RAML source frontend: RAML is still parsed and normalized by AMF before it becomes Sunday IR. OpenAPI and AsyncAPI source frontends use native YAML readers.

## Phase 2D Exit Audit

Kotlin/Sunday generation now has one public IR-backed path:

- `KotlinSundayGenerateCommand` exports sources with `GeneratedApiIrExporter`.
- The Gradle plugin task exports sources with `GeneratedApiIrExporter`.
- Both entrypoints delegate service, operation, model, and problem output to `KotlinSundayIrGenerator`.

Regression tests assert that the public generator path contains `GeneratedApiIrExporter` and `KotlinSundayIrGenerator`, does not reference removed wrapper generators, does not expose AMF service hook overrides, and does not walk AMF operation request/response surfaces.

Phase 2D is complete for Kotlin/Sunday.
