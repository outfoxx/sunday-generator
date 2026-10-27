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

## Model validation

Generated models enforce declared string, numeric, and collection constraints during
decoding and memberwise construction, including constraints supplied by named scalar
aliases. Invalid input reports the wire property name. Optional non-nullable fields
may be absent, but an explicit JSON `null` is rejected; nullable fields and patch
delete operations retain their existing semantics.

Constrained models have throwing initializers and throwing `with…` methods. Callers
must use `try` when constructing or copying these models. A class inheritance family
shares the throwing signature when any member requires validation, because Swift
does not permit an override to add `throws`. Unconstrained value models keep their
non-throwing initializers.

## Protocol branches in object unions

An object `oneOf` or `anyOf` enum stores a concrete Codable value for every branch.
A discriminated protocol hierarchy uses its `…Ref` enum, including intermediate
protocols on a mapped inheritance path. An ordinary parent schema uses a generated
`Any<ProtocolName>` implementation, such as `AnyCollectionProblem`. This value
conforms to the parent protocol and retains its inherited fields and constraints.
Its name is allocated alongside existing declarations to avoid collisions.
Service-scoped protocol parents and their concrete implementations are emitted at
module scope, because Swift protocols cannot be nested in the generic service.
Their union enums can still be nested inside the service.

The ordinary parent implementation accepts parent-only payloads; it does not
restrict the schema to known descendants. Validation still counts matches against
the original union branches. A payload matching both parent and child is rejected
by `oneOf` and accepted by `anyOf`. A payload satisfying the parent but failing a
child-only constraint can still match exactly one branch. Permitted extra fields
follow the same preservation option as other generated object models.

`SwiftProtocolUnionTest` compiles and executes these cases for OpenAPI, AsyncAPI,
RAML, and composed inputs. RAML unions retain their inclusive matching semantics;
the fixture supplies the shared IR URI format for the runtime Problem field,
because RAML strings do not support that format facet.

## Exit Audit

Swift/Sunday generation is fully IR-backed after RAML parsing.

- `SwiftSundayIrGenerator` has no AMF imports and consumes `GeneratedApi`, `GeneratedService`, `GeneratedOperation`, `GeneratedParameter`, `GeneratedResponse`, `GeneratedProblem`, `GeneratedModel`, and shared IR emitter helpers.
- `SwiftSundayGenerateCommand` builds `GeneratedApi` through `GeneratedApiIrExporter`, then delegates service/model/problem rendering to `SwiftSundayIrGenerator`.
- The AMF-era service hook wrapper has been removed, so Swift/Sunday no longer exposes `generateServiceType`, `processServiceBegin`, `processResourceMethod`, or `processReturnType`.
- Shared models, operation-local models, and referenced problem classes are generated from IR.

Remaining AMF-backed pieces:

- RAML source front-end state: `Document` and `ShapeIndex` are still needed to build the `RamlToGeneratedApi` input.
- No Swift/Sunday service, model, operation-local model, or problem generation uses AMF after the source has been converted to IR.

## Closed model decoding

Models with `additionalProperties: false` inspect all input keys using an unrestricted
`CodingKey` type and reject undeclared keys with `DecodingError.dataCorrupted`. The
allowed names include inherited wire properties. A finite `CodingKeys` enum alone
cannot detect unknown input keys. Explicit `additionalProperties` declarations
opt models into dynamic field storage, including nested JSON objects and nulls.
The default `schema` mode omits implicit storage; `-preserve-unknown-fields on`
enables it, while `off` suppresses catch-all storage. See
[dynamic model fields](unknown-fields.md).
