# IR Emitter Helper Extraction Plan

Phase 2E extracts shared IR emitter helpers after the Kotlin/Sunday migration proved the RAML -> IR -> code path can reproduce current output. The goal is to make Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday migrations cheaper without moving target-specific rendering rules into a generic layer.

## Extraction Boundary

Shared helpers should own IR semantics:

- lookup by IR identity
- response and payload selection
- media grouping decisions
- source and target metadata lookup
- operation-local model ownership
- problem identity and URI fallback rules

Emitters should keep language and runtime rendering:

- KotlinPoet, SwiftPoet, TypeScript AST/string generation
- language scalar mappings and import formatting
- runtime-specific request factory calls
- framework annotations and dependency choices
- generated public type naming where the target intentionally differs

## Candidate Helpers From KotlinSundayIrGenerator

The first audit of `KotlinSundayIrGenerator` found these stable helper patterns.

| Area | Current Kotlin/Sunday pattern | Shared helper direction |
| --- | --- | --- |
| type ref resolution | `GeneratedTypeRef.modelOrNull`, union flattening, source-qualified model lookup, scoped model lookup | Add an IR lookup index for named/scoped/source-qualified models and reusable union traversal. |
| operation response selection | `GeneratedOperation.primarySuccessResponse` chooses the first unspecified or 2xx response | Add an operation helper for primary success response selection and no-content checks. |
| media negotiation grouping | service constructor defaults collect referenced request/response media, and request/response calls compare operation media to defaults | Add helpers for referenced content/accept media, ordered defaults, and same-schema media grouping. |
| problem URI resolution | `GeneratedProblem.resolvedTypeUri` resolves relative type URIs against target default problem base URI | Add a problem URI helper that preserves absolute URIs and applies target fallback only when needed. |
| parameter naming | `OperationParameter` combines generated names, wire names, default values, constants, locations, and nullability filtering | Add a target-neutral operation parameter view with source name, generated name, wire name, default, constant, and location. |
| local model ownership | `GeneratedService.referencedScopedModels` follows operation refs and includes scoped models owned by the service | Add scoped model ownership helpers for service-owned operation-local models. |
| target override lookup | Kotlin uses `targets["kotlinClient"] ?: targets["kotlin"]` on API, models, and properties | Add target lookup helpers with preferred and fallback target ids. |
| source/library identity lookup | Model refs compare name, scope, and source to disambiguate imported declarations | Add source/library identity lookup helpers for duplicate names across RAML units. |

## Proposed Package Shape

Start with `io.outfoxx.sunday.generator.ir.emit` for target-neutral emitter support.

Initial types:

- `GeneratedApiIndex`
- `GeneratedTypeRefTraversal`
- `GeneratedOperationView`
- `GeneratedMediaSelection`
- `GeneratedProblemResolution`
- `GeneratedTargetLookup`

Keep APIs small and immutable. Prefer extension functions when there is no cached index state; use `GeneratedApiIndex` when repeated lookup would otherwise rescan `api.models`, `api.problems`, or `api.services`.

## Slice Order

1. Type/model lookup helpers.
   - named/scoped/source-qualified model lookup
   - operation-local model ownership
   - union flattening
   - target lookup wrappers
2. Operation response and media helpers.
   - primary success response
   - no-content response checks
   - referenced request/response media
   - ordered default media
3. Parameter helper view.
   - generated name allocation boundary
   - wire name preservation
   - default and constant value exposure
   - nullable parameter filtering signal
4. Problem helpers.
   - referenced problem lookup
   - problem URI resolution
   - source problem code vs resolved URI
5. Policy/auth/JAX-RS accessors.
   - keep fields target-neutral
   - do not emit annotations in shared helpers

## First Implementation Slice

Implemented first: type/model lookup helpers because they are low risk and useful to every emitter.

- Created `GeneratedApiIndex`.
- Added lookup by `(name, scope, source)`.
- Added `GeneratedTypeRef.modelOrNull(index)`.
- Added union flattening.
- Added service-owned scoped model traversal.
- Added target lookup helpers for preferred target id plus fallback target id.

Focused helper tests cover the shared helper directly, and Kotlin/Sunday now consumes these helpers while retaining current snapshot behavior.

## Second Implementation Slice

Implemented second: operation response and media helpers because response selection and media defaults are shared by every client/server emitter but language rendering should stay target-owned.

- Added `GeneratedOperation.primarySuccessResponse`.
- Added `GeneratedResponse.isNoContent`.
- Added `GeneratedApi.orderedDefaultMediaTypes`.
- Added `GeneratedService.defaultMediaSelection`.
- Added payload/response explicit media helpers.

Focused helper tests cover the shared helper directly, and Kotlin/Sunday now consumes these helpers while retaining current snapshot behavior.

## Third Implementation Slice

Implemented third: parameter helper views because path/query/header filtering, wire-name preservation, constants, defaults, and nullable-map filtering are common emitter semantics.

- Added `GeneratedOperationParameter`.
- Added `GeneratedOperation.operationParameterViews`.
- Added parameter location filtering helpers.
- Kept target-specific identifier shaping and name allocation as injected boundaries.

Focused helper tests cover the shared helper directly, and Kotlin/Sunday now consumes these helpers while retaining current snapshot behavior.

## Fourth Implementation Slice

Implemented fourth: problem helpers because generated problem registration and type generation need consistent source-name matching and URI resolution across emitters.

- Added `GeneratedApiIndex.problemOrNull`.
- Added `GeneratedTypeRef.problemOrNull`.
- Added operation and service referenced-problem collection helpers.
- Added `GeneratedProblem.sourceCode`.
- Added `GeneratedProblem.resolvedTypeUri`.

Focused helper tests cover the shared helper directly, and Kotlin/Sunday now consumes these helpers while retaining current snapshot behavior.

## Fifth Implementation Slice

Implemented fifth: policy/auth/JAX-RS accessors because the migrated emitters need consistent metadata reads while keeping annotation rendering target-owned.

- Added effective API/service/operation auth selection.
- Added security scheme lookup and parameter accessors.
- Added policy emptiness and overlay helpers.
- Added mode-aware JAX-RS flag accessors.

Focused helper tests cover the shared helper directly. No current Kotlin/Sunday refactor is needed because Kotlin/Sunday does not emit auth, policy, or JAX-RS annotations.

## Phase 2E Exit Audit

Result: Phase 2E is complete for the planned shared IR emitter helper extraction.

| Planned helper area | Exit result |
| --- | --- |
| Type/model lookup | Covered by `GeneratedApiIndex`, model/problem source identity lookup, scoped model traversal, union flattening, and target lookup helpers. |
| Operation response and media | Covered by primary success response selection, no-content detection, default media ordering, service media selection, and explicit content/accept helpers. |
| Parameter views | Covered by generated/wire names, default and constant values, location filtering, required/nullability metadata, and nullable-map filtering signals. |
| Problem helpers | Covered by generated/source-name lookup, source identity matching, referenced-problem collection, source problem code access, and target fallback URI resolution. |
| Policy/auth/JAX-RS metadata | Covered by effective auth selection, security scheme parameter lookup, policy overlays, mode-aware JAX-RS flags, async/reactive reads, and context parameters. |

No target-specific rendering has moved into the shared helper package. Kotlin/Sunday, Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday now consume the shared helper layer while preserving target-owned rendering rules.

No additional shared-helper blocker remains. The emitter migrations that depended on this extraction are complete for current-output parity.
