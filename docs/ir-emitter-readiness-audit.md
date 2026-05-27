# Sunday IR Emitter Readiness Audit

Date: 2026-05-11

This audit originally compared the AMF-backed emitters against the generated API IR. Its current purpose is to record the completed IR migration and the remaining parser boundary after Kotlin, Swift, TypeScript, and Kotlin JAX-RS generation moved to IR.

## Scope

Original audited code paths:

- Kotlin/Sunday, Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday IR generators.
- Kotlin, Swift, and TypeScript type registries.
- Source-to-IR readers for RAML, OpenAPI 3.1, and AsyncAPI.
- Shared naming, source identity, problem, and source extension helpers.

Current IR already covers the first source-to-IR foundation pass: API/service/operation/model/problem entities, YAML durability, documentation, tags, lifecycle/access metadata, validation, serialization names, structured default values, constant/literal parameter metadata, request and response headers, request and response media, operation-local generated shapes, structured query-string object metadata, operation transport metadata, problem URI parity, typed auth/security metadata, JAX-RS extension metadata, imported declaration identity, examples, inheritance, discriminators, external discriminators, target override metadata, nested model metadata, patchable models, source collection/scalar/object shape fidelity, Sunday `nullify`, request/response-only exchange, and Sunday event streaming.

## Readiness Summary

The RAML-to-IR foundation now covers the current-emitter parity inputs identified by this audit. Current Sunday and JAX-RS emitters detect required single-value parameter schemas and inject their literal values without exposing those parameters in method signatures; IR preserves that behavior explicitly with `GeneratedParameter.constantValue`. Kotlin/Sunday, Swift/Sunday, and TypeScript/Sunday render function defaults from AMF `DataNode` values; IR preserves those defaults as structured YAML scalar, array, or object values with `GeneratedParameter.defaultValue`. Current emitters also generate operation-local types for anonymous payload bodies and inline parameter enums; IR now preserves those as scoped `GeneratedModel` declarations with scoped `GeneratedTypeRef` references.

Final audit pass: after file payloads and inline/generated local shapes were added, a second scan of Kotlin/Quarkus, Kotlin/Sunday, Swift/Sunday, and TypeScript/Sunday AMF reads did not find another current generated-output category missing from IR. Current generated-output paths now consume IR.

## Parity Blockers

No known current-output parity blockers remain before starting IR-to-code emitter migration.

## Kotlin/Sunday Phase 2D Exit

Kotlin/Sunday now uses IR as its canonical generation path. The CLI command exports source specs to `GeneratedApi` and delegates to `KotlinSundayIrGenerator`.

There is no separate Kotlin/Sunday AMF service emitter or public fallback path. The remaining AMF dependency is source-front-end parsing and normalization before RAML becomes Sunday IR.

Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday now follow the same IR-backed public generation pattern.

## Registry Cleanup Audit

Date: 2026-05-21

The concrete Sunday/JAX-RS wrapper generators have been removed. Kotlin/Sunday, Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday CLI commands now export source specs to `GeneratedApi` and invoke their IR renderers directly.

Cleanup completed:

- Removed unused generic AMF-backed Kotlin and TypeScript service test helpers.
- Removed fake `generatorFactory` overrides from IR-backed CLI commands.
- Removed legacy AMF-backed generator and resolver paths.

Registry audit result:

- `KotlinTypeRegistry`, `SwiftTypeRegistry`, and `TypeScriptTypeRegistry` now handle generated type storage, file output, import/module bookkeeping, target language naming, and shared output helpers.
- No `KotlinAmfTypeResolver`, `SwiftAmfTypeResolver`, or `TypeScriptAmfTypeResolver` contracts remain.
- No production `KotlinGenerator`, `SwiftGenerator`, or `TypeScriptGenerator` AMF-backed emitter classes remain.
- `ShapeIndex` is used only by the RAML source processor and `RamlToGeneratedApi`.
- `APIProcessor`, `ShapeIndex`, `APIAnnotations`, `WebApiExts`, `LocalSundayDefinitionResourceLoader`, and AMF model imports remain RAML source-front-end implementation details.

Post-AMF-removal audit result:

- `rg "amf\\." generator/src/main/kotlin -l` reports only RAML parser/mapper support files: `common/APIProcessor.kt`, `common/ShapeIndex.kt`, `common/APIAnnotations.kt`, `utils/WebApiExts.kt`, `utils/LocalSundayDefinitionResourceLoader.kt`, and `ir/RamlToGeneratedApi.kt`.
- OpenAPI and AsyncAPI source readers use native YAML mapping and have no AMF imports.
- Generated-output emitters and type registries have no AMF imports.

## Source-Fidelity Follow-Up

These are worth capturing before OpenAPI 3.1.x/AsyncAPI frontends, but they are not known blockers for reproducing current generated output.

| Area | Current source metadata | Current IR status | Recommended action |
| --- | --- | --- | --- |
| Enum value metadata | AMF enum values can carry scalar identity and may eventually need docs, wire names, or source annotations | `GeneratedModel.values` is a list of strings, which is enough for current enum member generation | Keep strings for emitter migration unless per-value docs/annotations are required; otherwise replace with enum value objects later. |

## Already Covered

| Area | IR field or behavior |
| --- | --- |
| API/service/operation skeleton | `GeneratedApi`, `GeneratedService`, `GeneratedOperation` |
| Lifecycle and access metadata | `deprecated` on operations, parameters, models, and model properties; `readOnly` and `writeOnly` on model properties |
| Imported declarations and declaration identity | `GeneratedModel.source` and `GeneratedTypeRef.source` preserve defining source locations for imported declarations and source-qualified references |
| Operation names | `GeneratedOperation.id` after current name generation |
| Paths and HTTP methods | `GeneratedOperation.path` and `method` |
| Path/query/header params | `GeneratedParameter.location`, `type`, `required`, structured `defaultValue`, `serializationName` |
| Structured default values | `GeneratedParameter.defaultValue` preserves operation and base URI parameter defaults as YAML scalar, array, or object values |
| Constant/literal parameters | `GeneratedParameter.constantValue` preserves required single-value query/header/security parameters as typed YAML values |
| Inline/generated local shapes | `GeneratedModel.scope` and `GeneratedTypeRef.scope` preserve operation-local ownership for inline URI/query/header parameter enums and anonymous request/response payload models |
| Structured query-string object parameters | `GeneratedOperation.queryString`, `GeneratedSecurityScheme.queryString`, and scoped query-string models preserve RAML whole-query-object shapes |
| Request body | `GeneratedOperation.requestBody` |
| Success and error responses | `GeneratedResponse.status`, `type`, `mediaTypes`, `headers` |
| Base URI variables | `GeneratedService.baseUriParameters` preserves URI template variables, defaults, validation, generated names, and source names |
| Parameter encoding | `GeneratedParameter.encoding` preserves `style`, `explode`, `allowReserved`, and `allowEmptyValue` when a source reader provides them |
| Multiple payload alternatives | `GeneratedPayload.payloads` and `GeneratedResponse.payloads` preserve distinct media/schema alternatives, grouping same-schema media together |
| File payload shapes | AMF `FileShape` is mapped directly to `GeneratedTypeRef.scalar("file")`; fixtures cover request body, response body, and problem custom fields |
| Documentation | `GeneratedDocumentation` across major levels |
| Tags | `GeneratedApi.tags` preserves source-declared tags and `GeneratedOperation.tags` preserves operation tag references |
| Examples | `GeneratedExample` on parameters, payloads, responses, models, and properties |
| Model categories | `GeneratedModel.kind` for object, enum, scalar alias, union, array, and map |
| Model properties | `GeneratedModelProperty` with type, requiredness, defaults, validation, examples, documentation, and serialization name |
| Collection semantics | `GeneratedTypeRef.collection` and `GeneratedModel.collection` preserve set semantics from `uniqueItems` |
| Scalar formats | `GeneratedTypeRef.format` plus scalar names preserve target scalar distinctions |
| Closed object semantics | `GeneratedModel.closed` |
| Additional property semantics | `GeneratedModel.additionalProperties` preserves OpenAPI allowed/disallowed state, typed value schemas, and value validation |
| Pattern-property maps | `GeneratedModel.patternProperties` and `GeneratedModel.Kind.MAP` |
| Validation basics | `validation` maps for scalar, array, object, property, and parameter constraints |
| Inheritance | `GeneratedModel.inherits` |
| Internal discriminators | `discriminator`, `discriminatorValue`, `discriminatorMappings` |
| External discriminators | `externallyDiscriminated` and `externalDiscriminator` |
| Sunday operation behavior | `GeneratedExchange`, `GeneratedNullify`, `GeneratedStreaming` |
| Problem payload shape | `GeneratedProblem.payload`, `statusBindings`, standard problem fields, and custom fields |
| Absolute problem URIs | `GeneratedProblem.typeUri` is resolved from source `problemBaseUri`, `problemUriParams`, and API server URL when available; `sourceName` preserves the source problem code |
| Problem definition ownership | `GeneratedProblem.source.location` preserves the defining RAML unit, including library-defined problems |
| Auth names and requirements | `GeneratedAuth.schemes` and `requirements` preserve resolved security requirement alternatives |
| Security scheme parameters | `GeneratedAuth.securitySchemes` preserves scheme type, HTTP scheme details, bearer format, and header/query/cookie parameters with generated parameter metadata |
| JAX-RS mode annotations | `GeneratedOperation.jaxrs` carries `asynchronous`, `reactive`, mode-specific `sse` and `jsonBody`, and `jaxrsContext` values |
| SSE element media | `GeneratedOperation.jaxrs.sse` plus response payload media carries the data needed for `@SseElementType` validation/emission |
| Language type overrides | `GeneratedModel.targets` with target-specific `typeName` |
| Language implementation overrides | `GeneratedModelProperty.targets` with `implementation.code` and ordered `parameters` |
| API/model module/package overrides | `GeneratedApi.targets` and `GeneratedModel.targets`, including imported model defaults inherited from the declaring RAML library |
| Nested types | `GeneratedModel.nested` |
| Patch models | `GeneratedModel.patchable`, including inherited patchability |

## Target-Only Configuration

These should not be pushed into source IR unless a source-spec annotation exists for them. They should remain generator/runtime options or target configuration consumed during IR-to-code.

- Default problem base URI fallback when the source has no API server or problem base annotation.
- Service suffix and default package/module settings that are not source annotations.
- Generated timestamp/header toggles.
- TypeScript import style and file extension strategy.
- Kotlin Jakarta vs Javax package selection.
- Kotlin model implementation vs interface option.
- Kotlin validation annotation toggles such as container element validation.
- Quarkus/JAX-RS response wrapper defaults like always using `Response`.
- Runtime dependency choices such as Solid Foundation adoption.

## Recommended Implementation Slices

1. Remaining source fidelity
   - Add enum value objects if raw strings are not enough for wire names/docs.

## Exit Criteria

The emitter migration can start when:

- Every blocker above is either implemented, explicitly moved to target-only configuration, or intentionally deferred with a named generated-output difference.
- Durable YAML fixtures cover each implemented blocker.
- `sunday ir --validate` still reads all snapshots.
- Focused IR tests, ktlint, and generator/CLI checks pass.
