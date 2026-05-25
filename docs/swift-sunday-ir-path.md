# Swift/Sunday IR Path

Swift/Sunday service, shared model, and problem type generation now render from Sunday IR.

Current coverage:

- Adds `SwiftSundayIrGenerator`.
- Generates the basic Swift/Sunday service class from IR for the request-method fixture.
- Preserves the existing request-method output shape for request methods, request bodies, no-body methods, `sunday.requestOnly`, and `sunday.responseOnly`.
- Covers path/query/header parameters, default values, optionality, nullable request-map filtering, constant headers, mixed inline parameter enums, and explicit security scheme header parameters.
- Covers request bodies, optional request bodies, explicit request content types, explicit response content types, no-content responses, and inline response body models.
- Covers result-response mode, referenced problem registration, `sunday.nullify` `OrNil` wrappers, `sunday.eventSource`, and `sunday.eventStream`, including simple decoders, discriminated decoders, and common-base stream return lowering.
- Covers base URI companion generation and dedicated request/response builder fixture parity.
- Generates shared object, enum, scalar alias, array, set, map, union, direct inheritance, discriminator, patchable, external discriminator, nested type, imported duplicate-name, and Swift target module/type override models from IR.
- Generates referenced Swift/Sunday problem types from IR, including type URI resolution and custom problem fields.
- Routes the public Swift/Sunday service generator through RAML -> Sunday IR -> Swift/Sunday service rendering.
- Removes the legacy `SwiftSundayGenerator` wrapper; the CLI now exports source specs to `GeneratedApi` and delegates directly to `SwiftSundayIrGenerator`.
- Removes the temporary AMF shared model and problem type seeding boundaries. Operation-local enum and object types referenced by IR service methods are emitted from scoped IR models.

## Exit Audit

Swift/Sunday generation is fully IR-backed after RAML parsing.

- `SwiftSundayIrGenerator` has no AMF imports and consumes `GeneratedApi`, `GeneratedService`, `GeneratedOperation`, `GeneratedParameter`, `GeneratedResponse`, `GeneratedProblem`, `GeneratedModel`, and shared IR emitter helpers.
- `SwiftSundayGenerateCommand` builds `GeneratedApi` through `GeneratedApiIrExporter`, then delegates service/model/problem rendering to `SwiftSundayIrGenerator`.
- The AMF-era service hook wrapper has been removed, so Swift/Sunday no longer exposes `generateServiceType`, `processServiceBegin`, `processResourceMethod`, or `processReturnType`.
- Shared models, operation-local models, and referenced problem classes are generated from IR.

Remaining AMF-backed pieces:

- RAML source front-end state: `Document` and `ShapeIndex` are still needed to build the `RamlToGeneratedApi` input.
- No Swift/Sunday service, model, operation-local model, or problem generation uses AMF after the source has been converted to IR.
