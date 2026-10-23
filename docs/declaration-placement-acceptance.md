# Declaration placement acceptance matrix

Baseline: `d379e2d94` (#294). The candidate is the complete #293 working tree. This matrix is the review contract; a passing snapshot alone is not acceptance evidence. Native means annotations and references are parsed from RAML, OpenAPI, or AsyncAPI source without injecting declaration metadata afterward. AsyncAPI 2 and 3 are separate frontend cases; composed inputs and serialized IR are separate paths.

| ID | Contract | Implementation | Evidence / gate |
|---|---|---|---|
| P01 | Fieldwise inheritance; role first within each scope; narrower scope wins | `GeneratedPlacement`, `GeneratedPlacementResolver` | `GeneratedPlacementTest`; native `PlacementFixture` in all four language placement tests |
| P02 | Document, container, service, operation, declaration scopes retain original provenance | frontend source scopes; operation contribution records | native `PlacementRegressionFixture`; new `PlacementNativeInvariantTest` |
| P03 | JSON Pointer and canonical document selectors; configuration-relative, loaded graph only | `GeneratedPlacementConfiguration`, `GeneratedDocumentUri` | resolver selector/escaping tests; CLI and Gradle configuration tests |
| P04 | Rule order independent; field-only overrides; equal-rank conflicts and unused rules fail; relocations use direct fields except service-owned `models` | resolver relocation matching and shared scope validation | resolver tests; native relocation scope invariants and all-language compile gates |
| P05 | Tags filter established services; partial containers cannot move a complete service | service contribution operation URI sets | resolver and native service-container tests; duplicate operation invariant |
| P06 | References bind in the declaring lexical document, once, before composition | `GeneratedTypeReferenceRewriter`, `GeneratedPatchModelNames` | native imported nesting and input-order tests; compact IR recomposition tests |
| P07 | Deduplication uses package/module, enclosing chain and emitted name; referenced identities must agree | `GeneratedDeclarationIdentities` | native distinct enclosing types in one module; incompatible identity tests |
| P08 | Identical operation behavior composes while all provenance survives; incompatible bound signatures fail | composer behavioral signature + retained operation contributions | native duplicate operations, IR round trips, relocation conflict tests |
| P09 | Reference-only aliases retain identity; emitted aliases/derived models own placement | frontend alias lowering and shared indices | all-language alias/inheritance regressions and native external mappings |
| P10 | Nested types and companions stay with their enclosing declaration; filtered ancestors suppress all descendants | resolver nesting, output ownership, registry selection | all-language filtered nesting tests; native nesting invariant |
| P11 | Swift hoisted protocols and their validators retain the owning module | Swift declared symbol naming | native discriminator/nesting compile + runtime tests |
| P12 | Swift dependency validation includes inferred wire cases, problems, helpers; cycles/unnamed dependencies fail before output | shared hierarchy cases + resolver dependency graph | native discriminator-cycle rejection and valid single-module compile; existing problem/helper tests |
| P13 | Exclusion changes emission only; all reference identities still resolve | complete graph resolution then #294 selection | owner/consumer separate-artifact builds, all four backends |
| P14 | Problems use model defaults, retain problems category; service support remains service-owned | resolver and output ownership | existing problem/helper, configuration, Zanzibar tests |
| P15 | Kotlin package; Swift module; TS directory; Python package/module control files and imports | language registries/renderers | all-language compile/runtime placement tests; Python collisions; TS barrels |
| P16 | External substitution/import identity survives relocation and validation | target type mapping separate from placement | custom mapping suites; external validator placement tests |
| P17 | IR retains unresolved placement, sources, declarations, contribution membership and bindings | IR v3 schema; shared reference traversal | round-trip/recomposition/captured document tests; native invariant variants |
| P18 | One shared CLI/Gradle/library config; tracked content and resolution base | command/options/plugin | CLI tests; Gradle cache and config-relative tests |
| P19 | Hard cut old annotations/options, no migration handlers; documentation uses new syntax | public options, bundled annotations, docs | lint/build, CLI option tests, documentation review |
| P20 | Native-source → IR → resolved identity → compiler → runtime invariants | native fixtures and language harnesses | focused suites, snapshot invariant, integrations and full `check` |

## Review ledger

The prior 47 reports are not 47 independent regressions. The audit classified 45 as new placement contract gaps (often over inherited limitations), R7.2 as a working-baseline regression, and R5.7 as a pre-existing TypeScript barrel bug. R1–R6's 42 scenarios passed the audited focused suites; R7's five form the remaining implementation gate.

| Round | Finding IDs → acceptance contracts |
|---|---|
| R1 | 1→P07; 2→P15; 3→P14; 4→P02/P15; 5→P03/P05 |
| R2 | 1→P02; 2→P06/P07; 3→P02; 4→P02; 5→P02; 6→P15; 7→P14; 8→P15; 9→P15 |
| R3 | 1→P06; 2→P15; 3→P14; 4→P13; 5→P02/P05; 6→P10; 7→P15; 8→P18; 9→P15 |
| R4 | 1→P06/P16; 2→P14; 3→P02/P06; 4→P02; 5→P06/P14; 6→P15; 7→P14/P15 |
| R5 | 1→P01; 2→P16; 3→P12; 4→P04/P10; 5→P03; 6→P14; 7→P15 |
| R6 | 1→P06/P17; 2→P07/P10; 3→P15; 4→P14/P15; 5→P12/P14 |
| R7 | 1→P06; 2→P08; 3→P07; 4→P11; 5→P12 |

## Coverage boundaries

- Existing `PlacementIdentityFixture` intentionally constructs retained IR contributions, problem/service graphs and nested metadata after parsing. These are backend/IR evidence, not proof of native syntax. Native invariants supplement them.
- Nesting has no Python nested-class contract: Python emits flat models and detects collisions. Kotlin, Swift and TypeScript test nested identities. Native JSON-schema nesting support is tested only where the frontend exposes the annotation.
- Cyclic Swift modules and named modules depending on an unnamed generated module are unsupported and must fail with diagnostics. Valid adjacent cases must compile. Swift build-target configuration remains the consumer's responsibility.
- Full Cartesian combinations are not implied by a shared fixture name. A review must identify whether a claimed invariant is exercised by native parsing, constructed IR, compilation, or runtime behavior.
- `GeneratedPlacementScopeTest` rejects invalid relocation blocks in all languages and roles against native RAML, OpenAPI, AsyncAPI 2/3, and composed declarations. `PlacementScopeFixture` supplies valid service/model cases to all four language compiler gates. Explicit problem declarations use native RAML/OpenAPI syntax and composed inputs; the shared problem-scope contract also has a direct IR test. An exact native OpenAPI tag selector verifies service-owned model defaults without `kind`.

## Native regression gates for the five remaining findings

| Finding | Native regression | Compiler/runtime gate |
|---|---|---|
| R7.1 | `PlacementNativeFixture.importedNesting` with local and library `Parent` declarations | Kotlin reflected field identities; Swift JSON round trip; TypeScript typed consumer |
| R7.2 | `duplicateOperations` with shared payload models, all three formats, AsyncAPI 2/3 and composed inputs | Kotlin Sunday/JAX-RS client/server, Swift, TypeScript and Python compilation; `PlacementNativeInvariantTest` checks all contributor URIs, reverse input order, IR recomposition and incompatible relocated payload rejection |
| R7.3 | `distinctEnclosures`: `First.Child` and `Second.Child` in one package/module/directory | Kotlin field identity, Swift runtime construction and TypeScript typed consumer; IR round trips retain both enclosing bindings. Native `Nested_Child`/`NestedChild` regressions verify backend-specific emitted spelling. |
| R7.4 | `hierarchy("raml", nested = true)` | Named Swift module compilation and JSON round trip through the nested variant and hoisted protocol |
| R7.5 | `hierarchy` with inferred cases, all native formats and composed inputs | Valid colocated hierarchy compiles and decodes its wire reference; split-module cycle fails before output |

Native nesting annotations are currently exposed by RAML only. Existing constructed IR tests remain explicit coverage for the shared nested-type representation. The shared-document invariant also loads the same schema through OpenAPI and AsyncAPI and verifies canonical lookup independent of the importing frontend; all four owner-filter compiler tests exercise that boundary.

## Freeze and review gate

Run lint/resolver checks, frontend/composition/IR tests, CLI/Gradle checks, compile/runtime and separate-artifact checks, snapshot invariant, then full repository `check`. Record exact commands and results. Freeze the candidate with a content manifest covering tracked changes and untracked source; review that manifest's revision against this matrix. Any implementation change invalidates the freeze and requires affected checks and another review. Unmet rows remain explicit rather than being counted as fixed by unrelated snapshots.
