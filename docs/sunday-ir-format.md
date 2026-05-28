# Sunday IR Format

Sunday IR is the durable YAML contract between source specifications and generated code.

The first phase writes source specs into IR. RAML, OpenAPI 3.1, and AsyncAPI readers produce the same contract while Kotlin, Swift, and TypeScript generators consume IR instead of source-specific parser objects. AMF remains scoped to RAML parsing; OpenAPI and AsyncAPI use native YAML readers.

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

APIs may carry source-declared `tags`, each with a name and optional documentation. `GeneratedOperation.tags` carries operation tag references as source-fidelity metadata. Emitters can later decide whether tags affect service grouping, module placement, generated documentation, or no generated output.

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

Auth metadata preserves source scheme names in `schemes`, resolved security requirement alternatives in `requirements`, and explicit security scheme transport parameters in `securitySchemes`. Security schemes preserve their source `type`, HTTP `scheme`, and `bearerFormat` when present. Security scheme header, query, and cookie parameters use the same generated parameter shape as operation parameters, so requiredness, wire names, validation, constant values, and documentation survive into IR. Security schemes may also carry a structured `queryString` type reference; anonymous security query-string models use `scope.usage: "SECURITY_QUERY_STRING"` with the owning `securityScheme`. OpenAPI `x-sunday-zanzibar` and RAML `sunday.zanzibar` map into `auth.zanzibar`; operation values overlay inherited API values. Zanzibar user extraction metadata is carried in `auth.zanzibarUserSource`, currently with a JWT source that lists ordered claim names and an explicit `principalFallback` flag. Principal fallback is strict opt-in; absent user-source metadata leaves platform defaults in place.

Operations may carry `policy` metadata for target-independent policy inputs. The OpenAPI reader maps `x-sunday-policy` into `timeout`, `retry`, `circuitBreaker`, `clientRateLimit`, `serverRateLimit`, and `source` fields. These fields remain source metadata until target emitters decide whether to generate runtime policy data, Quarkus Fault Tolerance annotations, or no output. Kotlin/JAX-RS Quarkus output lowers supported policy fields to SmallRye Fault Tolerance annotations.

APIs, services, tags, and operations may carry `jaxrs` metadata for Quarkus/JAX-RS parity. Operation metadata includes `asynchronous`, `reactive`, mode-specific `sse` and `jsonBody` flags, and requested JAX-RS `context` parameters. API, service, and tag metadata may include `restClient` metadata for Quarkus REST Client output: `configKey`, `oidcClient`, and `providers`. In aggregated JAX-RS client output, only API-level `restClient` metadata is lowered onto the registered aggregate client; tag/service metadata remains available for non-aggregated service client generation. This metadata is target-specific source metadata, not a recommendation that non-JAX-RS clients expose these concepts.

## Examples

Parameters, request payloads, responses, models, and model properties may carry `examples`. Each example may include `name`, `mediaType`, `value`, `strict`, and `documentation`. Example values are serialized as YAML scalar, array, or object values so source examples remain available to every IR-to-code generator.

## Problems

Problem declarations carry the source problem key as `sourceName`, optional source metadata, the problem `typeUri`, status/title/detail metadata, and `statusBindings`. For RAML and OpenAPI `x-sunday-problem*` extensions, `typeUri` is resolved from `problemBaseUri`, `problemUriParams`, and the API server URL when the source provides enough information; `sourceName` remains the original source problem code for operation references and naming. Library-defined problems keep the defining source unit in `source.location` so later emitters can preserve package/module placement rules. Their `payload` describes the generated problem payload type, `application/problem+json` media type, and concrete payload fields. The payload field list includes the standard problem fields `type`, `title`, `status`, `detail`, `instance`, followed by source-defined custom fields. The legacy `fields` list remains the custom field subset for generators that only need extension fields.

The OpenAPI reader maps `x-sunday-problemBaseUri`, `x-sunday-problemUriParams`, `x-sunday-problemTypes`, `x-sunday-problems`, and `x-sunday-nullify` into the same IR fields used by RAML Sunday annotations.

## Model Metadata

Object models may carry inheritance and discriminator metadata. `inherits` lists direct parent model references. Root discriminator models carry `discriminator`, and concrete child models carry `discriminatorValue` when RAML defines one. Child models declare only local properties; inherited parent properties remain on the parent model.

Externally discriminated root models carry `externallyDiscriminated`. Their discriminator value to model references are carried in `discriminatorMappings`. Model properties that are discriminated by a sibling wire field carry `externalDiscriminator` with the generated property name of that sibling discriminator field.

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
