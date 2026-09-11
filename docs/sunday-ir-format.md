# Sunday IR Format

Sunday IR is the durable YAML contract between source specifications and generated code.

The first phase writes source specs into IR. RAML, OpenAPI 3.1, and AsyncAPI readers produce the same contract while Kotlin, Swift, and TypeScript generators consume IR instead of source-specific parser objects. AMF remains scoped to RAML parsing; OpenAPI and AsyncAPI use native YAML readers.

The native OpenAPI reader resolves local, external file, and public HTTP(S) `$ref` targets before conversion. Shared documents may contain a complete OpenAPI document, a component collection, or a standalone schema. Referenced schemas retain named identity across repeated uses, aliases, and recursive properties. Missing, unreadable, malformed, or incompatible targets and reference-only cycles report the referring source URI, line, and column.

OpenAPI 3.1 schemas follow the OpenAPI base dialect or JSON Schema 2020-12. `$id` establishes a schema resource and rebases relative schema references. JSON Pointer fragments resolve within the selected resource, while `$anchor` and static references to `$dynamicAnchor` identify named schema locations. Identifiers already registered in loaded documents resolve without a download, even when the identifier is an HTTPS URI. Parameter, response, and other OpenAPI Reference Objects remain document-relative. Examples, defaults, and extension data do not establish schema resources. OpenAPI 3.0 retains document-relative resolution. Custom schema dialects and dynamic `$dynamicRef` evaluation are rejected with explicit diagnostics.

Each complete OpenAPI document selects its own schema rules, including when references cross between 3.0 and 3.1 documents. A 3.1 document uses its own `jsonSchemaDialect` or defaults to the OAS base dialect; supported schema-level `$schema` declarations override that default. Bare schemas and component collections retain the referring schema's fallback dialect. See [OpenAPI schema dialect selection](https://spec.openapis.org/oas/v3.1.0.html#specifying-schema-dialects).

In OpenAPI 3.0 schemas, every sibling of `$ref` is ignored, including annotations, properties, and discriminator mappings. Ignored content cannot introduce dependencies or schema resources. This follows the [OpenAPI 3.0 Reference Object rules](https://spec.openapis.org/oas/v3.0.3.html#reference-object).

OpenAPI 3.1 `$ref` siblings are conjunctive: referenced fields and requirements remain in effect alongside local properties and constraints. Normalized documents express this through `allOf`. Conversion combines supported constraints, and incompatible or unrepresentable intersections produce source-located errors rather than choosing one constraint. Referenced inline schemas are promoted once, so their original uses, anchors, pointer aliases, and recursive properties share a model type. Declared and promoted component names are reserved before allocating inline object or union names. Inline names retain their existing spelling when available; collisions receive deterministic numeric suffixes starting at `2`, preserving both models and their references.

Name reservations compare the UpperCamelCase spelling used by language emitters, including case and separator normalization. For example, a declared `cat` reserves the generated name `Cat`, so a distinct imported `Cat` becomes `Cat2`. Original component keys and explicit language naming overrides retain their existing behavior.

Canonical model names do not change implicit discriminator wire values. An imported `Cat` renamed to `Cat2` still uses `"Cat"` on the wire. When a named variant is renamed, normalization records a complete discriminator mapping for the reachable named alternatives, including unchanged variants. Explicit mappings remain authoritative. Values are inferred from original component names, preserving case and punctuation, rather than names allocated to anonymous schemas. Conflicting inferred values within a hierarchy require explicit mappings and produce a source-located diagnostic. This uses the existing dependency graph without importing otherwise unreachable variants.

Discriminator mapping targets first match exact, case-sensitive component names in the document containing the discriminator. All other string values resolve as schema URI references, including extensionless paths such as `cat` and query-only references such as `?schema=cat`. They follow the schema's effective base URI and dialect rules, retain explicit wire values, and capture dependencies even when a subtype is reachable only through its mapping. Non-string, unresolved, malformed, and incompatible targets report errors at the mapping's source location instead of becoming unresolved model names.

Schemas containing only `nullable` and annotations remain unconstrained scalar `any` values, with the requested nullability. `nullable` alone does not create an empty object model. This preserves existing OpenAPI 3.0 and 3.1 type inference while keeping nullability constraints in composed schemas.

OpenAPI 3.1 references may target boolean `true` schemas, including standalone documents and nested schema locations. They normalize to the existing empty-schema representation and retain canonical identity, source locations, requiredness, and the existing empty-schema nullability convention. Referenced parameters may also contain `schema: true`. Boolean schema reference targets remain invalid under OpenAPI 3.0 rules. References to `false` schemas report an explicit unsupported-feature diagnostic; they are never widened to unconstrained values. Existing boolean keyword forms such as `additionalProperties: true` and `additionalProperties: false` remain supported.

Canonical named references preserve the effective schema's nullability, including `nullable: true` and `type: [object, 'null']`. Promoting an inline schema, following an alias chain, or using a single-reference `allOf` keeps the same nullable type without duplicating the model. A property's requiredness remains independent of whether its value may be null. Nullable unions with one payload branch and a `type: 'null'` branch retain the same payload type after promotion, including named aliases and recursive inline objects. They use IR nullability rather than an unrestricted `any` alternative. Nullability combines the effective `type`, supported `nullable`, `enum`, `const`, and composition assertions: null must satisfy every applicable assertion. In particular, an enum or constant excluding null keeps a nullable type declaration non-null at its uses. `anyOf` accepts null when at least one branch does; `oneOf` accepts it only when exactly one branch does.

Bounded null-union reductions also apply wrapper constraints. An object-constrained `Item | null` reduces to non-null `Item`, and `oneOf` with `string | null` and a null-only branch reduces to non-null string. Unchanged payloads retain their canonical references. Narrowing a nullable target uses the wrapper's model identity and leaves the target declaration unchanged. Unsupported intersections, indeterminate null acceptance, multiple payload alternatives with a null-only branch, and reductions requiring recursive union expansion report source-located errors. A remaining null-only alternative is never emitted as unrestricted `any`.

Within composition, each operand's `nullable` modifies only its own explicit `type` before intersection. For example, `type: string` with `allOf: [{type: string, nullable: true}]` remains non-nullable; every operand must accept null for the resulting type to accept it. A nullable referenced declaration stays nullable when a separate wrapper excludes null. An intersection whose only remaining type is null reports an unsupported-case diagnostic instead of becoming an arbitrary value.

For OpenAPI 3.1 nullability analysis, `then` and `else` have no effect without an `if` in the same Schema Object, and a lone `if` imposes no restriction. Composition keeps these boundaries: an `if` in one operand cannot activate a `then` in another. Active conditional or `not` assertions whose effect on null acceptance is unsupported produce source-located errors when no other assertion definitively excludes null. Unconstrained schemas retain their existing fallback, and indeterminate results cannot authorize null-union reductions. General conditional schema evaluation remains unsupported.

Promoted parameter schemas retain effective defaults, constants, and supported validation while type references remain canonical. Parameter annotations, encoding, and requiredness stay local to the parameter, including defaults such as `0`, `false`, and `20`.

Redeclaring an inherited property with only documentary changes preserves `allOf` inheritance when its effective type, constraints, and requiredness are unchanged. Compatibility ignores only `description`, `summary`, `title`, `example`, `examples`, `externalDocs`, and `$comment` at schema locations, including nested schemas and resolved aliases. Defaults, deprecation, read/write flags, discriminator metadata, Sunday extensions, and unknown metadata remain significant; literal values inside enums, constants, defaults, and extensions are compared unchanged. Child documentation remains in the normalized and effective schemas without changing the parent declaration. Child IR models retain only local fields, and inherited properties follow each emitter's existing representation without duplicate declarations. Actual refinements, or recursive comparisons that cannot establish compatibility, retain the existing flattening behavior; incompatible intersections still fail with a source location.

This documentary compatibility also applies inside inherited `anyOf` and `oneOf` branches, including nullable unions. Intersections require the same union keyword, the same number of branches, and equivalent contracts at each branch position. Later operands override supplied documentary annotations recursively at schema locations; omitted annotations remain inherited. Matching canonical references stay references, including recursive uses inside matching nested `allOf` wrappers. Documentary changes on wrappers and reference uses are compared structurally before resolving targets, so they do not expand the recursive declaration being composed. Different wrapper structures still require effective-schema comparison and retain its recursion guards. Branch reordering, deduplication, and general union-intersection evaluation remain unsupported; contract differences retain source-located intersection diagnostics.

Annotation-bearing aliases retain their own declaration identity. Descriptions, examples, deprecation, and read/write annotations apply through that alias; naming or promoting its target does not apply those overrides to original or direct target uses.

Numeric composition interprets each operand using its own schema dialect. OpenAPI 3.0 boolean `exclusiveMinimum`/`exclusiveMaximum` modifiers are combined with their associated bounds; OpenAPI 3.1 numeric exclusive bounds remain supported during composition. Equal boundaries retain exclusivity, stronger inclusive bounds supersede weaker exclusive bounds, and empty intervals produce source-located errors. The existing IR validation projection is preserved: explicit `minimum`/`maximum` constraints are exported, while exclusive-bound enforcement is not added to language validators by this conversion change.

An OpenAPI 3.0 exclusivity modifier set to `false` has no effect and does not require a corresponding bound. A modifier set to `true` still requires its associated `minimum` or `maximum`; malformed modifiers and missing required bounds retain source-located diagnostics.

Arbitrary Schema Object annotation properties are preserved in normalized documents and ignored for reference discovery. Names such as `schema`, `content`, or `openapi` do not make an otherwise valid schema an incompatible target. References to objects known to occupy incompatible OpenAPI locations still fail.

HTTP loading follows up to five redirects and retains the final retrieval URI as the document base. Schema references, resource identifiers, and redirect locations use RFC 3986 URI resolution; query-only references replace the query while retaining the document path. Repeated path slashes and existing percent escapes are preserved, so `/schemas//item` and `/schemas/item` remain distinct resources. HTTPS-to-HTTP redirects, authenticated URLs, and retrieval schemes other than `file`, `http`, and `https` are unsupported. Defaults are a 10-second connection timeout, a 30-second response timeout, and a 16 MiB document limit. Each loading session revalidates remote documents using ETag/Last-Modified when available. Successfully loaded content is cached under the requested URL, the effective URL, and observed intermediate redirect URLs, allowing later offline builds to reference any of them. Online failures fail conversion; offline mode uses cached documents and reports a cache miss when content is unavailable. Existing version-one cache entries remain readable; online revalidation adds any missing redirect aliases.

CLI export and generation commands accept `--openapi-reference-cache-dir` (default: `~/.cache/sunday-generator/openapi`) and `--openapi-offline`. For example:

```shell
sunday ir --openapi-reference-cache-dir .sunday-cache -out api.ir.yaml api.yaml
sunday ir --openapi-reference-cache-dir .sunday-cache --openapi-offline -out api.ir.yaml api.yaml
```

The library exposes `OpenApiReferenceOptions`, `OpenApiDocumentLoader`, and `OpenApiReferenceResolver`. The resolver returns normalized content and captured source documents. `OpenApiDocumentSnapshot` persists those documents for subsequent conversions that must use exactly the same inputs. Existing converter and exporter calls use the default loader; injected loaders can provide captured documents without network access.

Gradle's `openApiReferenceCacheDirectory` defaults to `caches/sunday/openapi` beneath Gradle User Home. Gradle `--offline` also applies to OpenAPI retrieval. Discovery for generations containing OpenAPI runs on each build, resolves the transitive dependency graph, and captures source content before grouping or generation. The generation task remains cacheable: unchanged content leaves it up to date, and changes to referenced content invalidate it. Capture manifests use relative local paths and exclude HTTP validators and retrieval timestamps. Generations containing only RAML or AsyncAPI retain their existing discovery caching. Empty source sets skip discovery and generation with `NO-SOURCE`. Removing the last specification cleans previous outputs (which Gradle reports as successful work); subsequent empty builds skip, and adding a source enables generation again.

```kotlin
sundayGenerations {
  create("client") {
    openApiReferenceCacheDirectory.set(layout.projectDirectory.dir(".sunday-cache"))
  }
}
```

See [Sunday IR Emitter Readiness Audit](ir-emitter-readiness-audit.md) for the current stabilization checklist before IR-to-code emitter migration.

See [OpenAPI 3.1 IR Exit Audit](openapi-ir-exit-audit.md) for the OpenAPI frontend closure criteria and deferred feature list.

See [AsyncAPI IR Exit Audit](asyncapi-ir-exit-audit.md) for the AsyncAPI frontend closure criteria and deferred feature list.

## Source Fragment Composition

Source frontends may produce `GeneratedApiFragment` values before final code generation. A fragment wraps a source-produced `GeneratedApi` plus composition identities for the API, services, operations, models, and problems. `GeneratedApiComposer` merges fragments with the same API identity into one coherent `GeneratedApi`.

Composition identity follows the Sunday source rules:

- explicit `x-sunday-*` identity fields win
- native source ids are the first fallback, such as OpenAPI `operationId`
- deterministic generated ids are the final fallback
- default-derived collisions are errors with diagnostics naming the override to add, such as `x-sunday-apiId`, `x-sunday-service`, `x-sunday-operationId`, or `x-sunday-modelName`

OpenAPI source fragments currently map `x-sunday-apiId`, `x-sunday-service`, and `x-sunday-operationId` into fragment identity metadata. RAML source fragments map equivalent `sunday.apiId` and `sunday.operationId` annotations, and existing `sunday.group` values normalize into service identities.

This composition boundary lets OpenAPI and AsyncAPI describe different parts of the same API while preserving one generated service/client surface when API and service identities line up.

The AsyncAPI reader maps subscribe operations as event-stream responses on composed services and publish operations as request-only message sends. It currently covers API identity, service identity, operation identity, channel operations, JSON message payload media, object payload models, message headers, component schema refs, operation/message documentation, message examples, arrays, enums, maps, nullable unions, validation constraints, wire-name preservation, servers, security requirements, security schemes, and server/channel/operation bindings. Broader protocol-specific binding interpretation remains source-reader follow-up work.

Composition keeps the first fragment's API-level metadata by default and fills missing metadata such as `auth`, `jaxrs`, `protocol`, `media`, targets, tags, and documentation from later fragments. When services merge by identity, service-level metadata such as `baseUri`, `auth`, `jaxrs`, `protocol`, `media`, and documentation is also preserved from the first fragment that provides it.

## Top-Level Fields

- `irVersion`: Current format version. Version `1` is the only supported version.
- `name`: API name.
- `source`: Source specification metadata, including `kind` and `location`.
- `services`: Generated service declarations and operations.
- `models`: Generated model declarations.
- `problems`: Problem type declarations.
- `auth`, `media`, `policy`, `targets`, `tags`, and `documentation`: Optional shared metadata.

## Documentation

Every major IR level may carry `documentation` with `summary` and/or `description` when the source specification provides it. This includes APIs, services, operations, parameters, request payloads, responses, models, model properties, and problems.

## Tags

APIs may carry source-declared `tags`, each with a name and optional documentation. OpenAPI tags may set `x-sunday-service-group: true`, which maps to `GeneratedTag.serviceGroup` and marks that tag as an explicit service grouping tag. `GeneratedOperation.tags` carries operation tag references as source-fidelity metadata. The `-services-from-tags` option remains a broad convenience mode that treats every operation tag as a service grouping tag; without that option, OpenAPI service grouping only uses tags marked with `x-sunday-service-group: true`. Other tag metadata, such as `x-sunday-policy` and `x-sunday-jaxrs`, does not imply service grouping. OpenAPI `x-sunday-exclude` may be set to `true`, `client`, or `server` on tags, operations, parameters, and request bodies. Target-specific exclusions are applied only when source conversion is running for a known target; standalone IR export only applies global `true` exclusions.

## Lifecycle Metadata

Operations, parameters, models, and model properties may carry `deprecated` when the source marks them deprecated. Model properties may also carry `readOnly` and `writeOnly` so client and server emitters can preserve source access semantics without re-reading the source document.

## Names

IR identifiers use generated-code-friendly names. When that identifier differs from the source wire name, the IR entry carries `serializationName` with the original source name. This lets generators expose ergonomic names such as `pageSize` and `xTraceId` while still encoding `page-size` and `X-Trace-Id` on the wire.

## Validation

Model properties and operation parameters may carry a `validation` map. RAML scalar and container constraints are preserved as string values, including bounds such as `minimum`, `maximum`, `minLength`, `maxLength`, `pattern`, `minItems`, `maxItems`, and `uniqueItems`.

Required query and header parameters with a single source literal may carry `constantValue`. Emitters can use this to inject required fixed header/query/security values without exposing them as user-supplied method parameters.

Parameter `defaultValue` values are preserved as YAML scalars, arrays, or objects. This keeps typed client defaults durable for booleans, numbers, enums, arrays, and object values; target emitters that need string defaults, such as JAX-RS `@DefaultValue`, derive that representation during IR-to-code generation.

## Operation Payloads And Headers

Request payloads carry `mediaTypes` for the source request body content types. Request headers are represented as operation `parameters` with `location: "HEADER"`. Response headers are represented on each response as `headers`, using the same generated parameter shape as request headers.

File payload shapes are represented as scalar type refs with `name: "file"` so later emitters can map them to target-native binary payload types.

Anonymous operation-local shapes are preserved as generated models with `scope`. Scoped models identify their owning `service`, `operation`, and `usage` (`PARAMETER`, `QUERY_STRING`, `REQUEST_BODY`, or `RESPONSE_BODY`), plus the generated parameter `name` or response `status` when applicable. Type references to these models carry the same scope, so later emitters can reproduce operation-local names such as `FetchProjectStateQueryParam`, `FetchProjectQueryString`, `FetchProjectRequestBody`, and `FetchProjectResponseBody` without relying on AMF synthetic names like `schema`.

## Operation Transport

Services may carry `baseUri` plus `baseUriParameters` for URI template variables. Base URI parameters use the same generated parameter shape as operation parameters, including generated names, `serializationName`, defaults, validation, examples, and documentation.

Operation parameters may carry `encoding` with `style`, `explode`, `allowReserved`, and `allowEmptyValue` when the source specification exposes those wire-format controls. RAML does not support every encoding field directly, but the IR contract preserves them for OpenAPI 3.1 and any source reader that can provide them.

Operations may carry `queryString` when the source models the entire query string as a structured object instead of individual query parameters. This is source-fidelity metadata for future frontends and emitters; current query parameters still use normal operation `parameters`.

Request bodies and responses keep the historical top-level `type` and `mediaTypes` for the first payload alternative. When different media types use distinct schemas, `payloads` carries each distinct payload type with the media types and examples that belong to that type. Media types sharing the same schema are grouped into the same payload alternative.

## Protocol Metadata

APIs, services, and operations may carry `protocol` metadata for non-HTTP source transports. `protocol.servers` preserves source server names, URLs, protocol names, protocol versions, server variables, server auth, server bindings, and documentation. Services may still expose the first resolved server URL as `baseUri` for targets that need one transport endpoint.

`protocol.bindings` preserves source binding objects with a `kind` (`SERVER`, `CHANNEL`, `OPERATION`, or `MESSAGE`), protocol key such as `kafka`, and raw YAML-compatible `values`. This keeps AsyncAPI binding data durable without forcing the IR to model every protocol-specific binding shape up front.

## Auth And Target Metadata

An explicit OpenAPI `security` declaration sets `auth.securityOverride: true`, including when its requirement list is empty. Omitted operation security inherits the service or API requirements. `security: []` produces an override with no requirements, while `security: [{}]` preserves one empty requirement alternative; an empty alternative alongside named requirements permits anonymous access. Non-empty declarations replace inherited requirements. The override flag survives YAML round trips and composition and defaults to `false` for existing IR documents. Zanzibar-only metadata does not override inherited security requirements.

Auth metadata preserves source scheme names in `schemes`, resolved security requirement alternatives in `requirements`, and explicit security scheme transport parameters in `securitySchemes`. Security schemes preserve their source `type`, HTTP `scheme`, and `bearerFormat` when present. Security scheme header, query, and cookie parameters use the same generated parameter shape as operation parameters, so requiredness, wire names, validation, constant values, and documentation survive into IR. Security schemes may also carry a structured `queryString` type reference; anonymous security query-string models use `scope.usage: "SECURITY_QUERY_STRING"` with the owning `securityScheme`. OpenAPI `x-sunday-zanzibar` and RAML `sunday.zanzibar` map into `auth.zanzibar`; operation values overlay inherited API values. Zanzibar user extraction metadata is carried in `auth.zanzibarUserSource`, currently with a JWT source that lists ordered claim names and an explicit `principalFallback` flag. Principal fallback is strict opt-in; absent user-source metadata leaves platform defaults in place.

Operations, OpenAPI path items, and OpenAPI tags may carry `policy` metadata for target-independent policy inputs. The OpenAPI reader maps `x-sunday-policy` into `timeout`, `retry`, `circuitBreaker`, `clientRateLimit`, `serverRateLimit`, and `source` fields. Tag-level policy is preserved on the tag and overlaid into tagged operations before emission; path-item policy applies to all operations under that path; operation metadata overrides both. These fields remain source metadata until target emitters decide whether to generate runtime policy data, Quarkus Fault Tolerance annotations, or no output. Kotlin/JAX-RS Quarkus output lowers supported policy fields to SmallRye Fault Tolerance annotations.

APIs, services, tags, and operations may carry `jaxrs` metadata for Quarkus/JAX-RS parity. Operation metadata includes `asynchronous`, `reactive`, mode-specific `sse` and `jsonBody` flags, and requested JAX-RS `context` parameters. RAML supports `sunday.jaxrsContext`, `sunday.jaxrsContext.client`, and `sunday.jaxrsContext.server`. OpenAPI supports `x-sunday-jaxrs.context` entries as strings for both targets or objects such as `{type: routingContext, target: server}`. API, service, and tag metadata may include `restClient` metadata for Quarkus REST Client output: `configKey`, `oidcClient`, and `providers`. In aggregated JAX-RS client output, only API-level `restClient` metadata is lowered onto the registered aggregate client; tag/service metadata remains available for non-aggregated service client generation. This metadata is target-specific source metadata, not a recommendation that non-JAX-RS clients expose these concepts.

## Examples

Parameters, request payloads, responses, models, and model properties may carry `examples`. Each example may include `name`, `mediaType`, `value`, `strict`, and `documentation`. Example values are serialized as YAML scalar, array, or object values so source examples remain available to every IR-to-code generator.

## Problems

Problem declarations carry the source problem key as `sourceName`, optional source metadata, the problem `typeUri`, status/title/detail metadata, and `statusBindings`. For RAML and OpenAPI `x-sunday-problem*` extensions, `typeUri` is resolved from `problemBaseUri`, `problemUriParams`, and the API server URL when the source provides enough information; `sourceName` remains the original source problem code for operation references and naming. Library-defined problems keep the defining source unit in `source.location` so later emitters can preserve package/module placement rules. Their `payload` describes the generated problem payload type, `application/problem+json` media type, and concrete payload fields. The payload field list includes the standard problem fields `type`, `title`, `status`, `detail`, `instance`, followed by source-defined custom fields. The legacy `fields` list remains the custom field subset for generators that only need extension fields.

The OpenAPI reader maps `x-sunday-problemBaseUri`, `x-sunday-problemUriParams`, `x-sunday-problemTypes`, `x-sunday-problems`, and `x-sunday-nullify` into the same IR fields used by RAML Sunday annotations.

## Model Metadata

Object models may carry inheritance and discriminator metadata. `inherits` lists direct parent model references. Root discriminator models carry `discriminator`, and concrete child models carry `discriminatorValue` when RAML defines one. Child models declare only local properties; inherited parent properties remain on the parent model.

Externally discriminated root models carry `externallyDiscriminated`. Their discriminator value to model references are carried in `discriminatorMappings`. Model properties that are discriminated by a sibling wire field carry `externalDiscriminator` with the generated property name of that sibling discriminator field.

Enum models carry wire values in `values`. When the source provides explicit generated enum member names, such as OpenAPI `x-enum-varnames`, those names are preserved positionally in `enumValueNames`; otherwise emitters derive target-language identifiers from the wire values.

An enum may opt into tolerant decoding by setting `unknownValue` to one of its declared wire values. OpenAPI and AsyncAPI map `x-unknown-value` to this field; RAML maps the `sunday.unknownValue` annotation. Emitters use the selected value's generated member name for a fallback arm that carries the unrecognized raw string and serializes that original string unchanged. Enums without `unknownValue` remain strict.

When a discriminator property uses a tolerant enum, the same `unknownValue` automatically enables a catch-all hierarchy variant. Discriminator mappings continue to describe exact known cases; they are not wildcard declarations. Known tags decode only their mapped subtype, while an unmapped string tag decodes the generated fallback with the original tag, declared root properties, and complete raw payload. Mapping the reserved `unknownValue` sentinel to a concrete subtype is a generation error.

Models declared outside the root source document may carry `source` with the defining source location. Named type references may also carry `source` when the referenced declaration is imported. This allows IR to preserve duplicate declaration names across RAML documents and libraries without depending on AMF unit state during IR-to-code emission.

## Model Source Fidelity

Array type refs and array models may carry `collection: "SET"` when the source shape has unique item semantics. Scalar type refs may carry `format` when the source scalar format is needed to select target-specific scalar types.

Object models may carry `closed` when the source object disallows unknown properties. OpenAPI additional property semantics are represented as `additionalProperties`, with `allowed`, optional value `type`, optional `validation`, and optional `documentation`. Pattern properties are represented as `patternProperties`, each with a source regex `pattern`, value `type`, optional `validation`, and optional `documentation`. Pure pattern-property object shapes are represented as `kind: "MAP"` with the map value type in `aliases`.

## Target Metadata

APIs, models, and model properties may carry `targets`, keyed by target id such as `kotlin`, `kotlinClient`, `kotlinServer`, `swift`, and `typescript`. Target entries preserve source annotations for package/module names, model package/module names, target type names, and implementation overrides. Imported RAML declaration models also inherit target defaults from their declaring library, so library-level model package/module annotations remain durable in IR.

Implementation overrides carry source `code` plus ordered `parameters` with `type` and `value`. Models may also carry `nested` metadata with the resolved enclosing type reference and nested name, and `patchable` when the source model or one of its parents is marked patchable.

## Source And Codegen Boundary

Sunday IR is now the canonical boundary between source readers and code emitters.

Phase 1 defined the durable IR contract and source readers; the current codebase has also completed the IR-to-code migration for existing emitters.

- RAML -> IR uses AMF for RAML parsing and normalization, then maps through `RamlToGeneratedApi`.
- OpenAPI 3.1 -> IR uses the native YAML reader in `OpenApiToGeneratedApi`.
- AsyncAPI -> IR uses the native YAML reader in `AsyncApiToGeneratedApi`.
- All current codegen paths consume `GeneratedApi`: Kotlin/Sunday, Kotlin/JAX-RS, Swift/Sunday, and TypeScript/Sunday.

## CLI

Export RAML, OpenAPI, or AsyncAPI to IR. The source format is detected by default:

```bash
sunday ir -out api.ir.yaml api.raml
sunday ir -out api.ir.yaml api.openapi.yaml
sunday ir -out api.ir.yaml events.asyncapi.yaml
```

An explicit source format can be supplied when detection is ambiguous:

```bash
sunday ir -out events.ir.yaml --source asyncapi events.yaml
```

Multiple sources can be composed into one IR document when their Sunday API identities match:

```bash
sunday ir -out api.ir.yaml api.openapi.yaml events.asyncapi.yaml
```

Validate an existing IR file:

```bash
sunday ir --validate api.ir.yaml
```
