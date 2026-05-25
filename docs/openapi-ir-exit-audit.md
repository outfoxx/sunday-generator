# OpenAPI 3.1 IR Exit Audit

Date: 2026-05-14

This audit closes the first OpenAPI 3.1 source-spec-to-IR pass. The goal is not to implement every OpenAPI 3.1 and JSON Schema 2020-12 keyword before AsyncAPI starts. The goal is to confirm that OpenAPI can produce the same durable Sunday IR surfaces needed by the current HTTP client/server emitters and by the next source frontend work.

## Summary

OpenAPI 3.1 is ready to leave the foundation phase. The frontend parses OpenAPI 3.1 through the native YAML reader in `OpenApiToGeneratedApi`, maps directly into `GeneratedApiFragment`, and has durable YAML snapshots for the current HTTP IR surface. AMF is not part of the OpenAPI source frontend.

There are no blocking OpenAPI-to-IR gaps before starting AsyncAPI. The remaining items are either OpenAPI features outside the current Sunday HTTP generation surface or deeper JSON Schema 2020-12 fidelity that should be revisited only if real specifications need them.

## Implemented Coverage

| Area | Fixture or test | IR surface |
| --- | --- | --- |
| Native reader smoke | `project-3.1.yaml`, `OpenApiToGeneratedApiTest` | Confirms the native OpenAPI reader loads OpenAPI 3.1 documents without AMF |
| First vertical slice | `project-3.1.yaml` | API metadata, source metadata, server URL, service, operation, path parameter, response body, object model |
| Operation surface | `operation-surface-3.1.yaml` | Path/query/header parameters, defaults, request body, response headers, multiple response media |
| Schema breadth | `schema-breadth-3.1.yaml` | Arrays, enums, nullable unions, maps, validation constraints, wire-name preservation |
| Composition | `composition-3.1.yaml` | `allOf` inheritance, `oneOf`/union models, discriminator metadata |
| Additional properties | `additional-properties-3.1.yaml` | Typed additional properties, open objects, closed objects |
| Lifecycle metadata | `lifecycle-3.1.yaml` | Deprecated operations/parameters/models/properties, read-only and write-only properties |
| Security metadata | `security-3.1.yaml` | API and operation security requirements, HTTP bearer schemes, API-key header/query parameters |
| Docs/examples/tags | `metadata-3.1.yaml` | API docs, tags, operation docs, parameter examples, request/response examples, model/property docs/examples |
| Sunday extensions | `extensions-3.1.yaml` | `x-sunday-problem*`, `x-sunday-problems`, `x-sunday-nullify`, `x-sunday-zanzibar`, `x-sunday-policy` |

## Extension Contract

The OpenAPI extension surface is intentionally scoped to `x-sunday-*` names. Generic vendor extensions such as arbitrary `x-auth`, `x-fault-tolerance`, or `x-rate-limit` are not mapped into IR without a Sunday-owned contract, because doing so would make the target-independent IR depend on ambiguous third-party naming and merge semantics.

Current supported extensions:

- `x-sunday-problemBaseUri`
- `x-sunday-problemUriParams`
- `x-sunday-problemTypes`
- `x-sunday-problems`
- `x-sunday-nullify`
- `x-sunday-zanzibar`
- `x-sunday-policy`

`x-sunday-zanzibar` maps to `GeneratedAuth.zanzibar`; operation values overlay inherited API values. `x-sunday-policy` maps to operation `GeneratedPolicy` fields: `timeout`, `retry`, `circuitBreaker`, `clientRateLimit`, `serverRateLimit`, and `source`.

## Deferred OpenAPI Features

These do not block AsyncAPI or current HTTP emitter work.

| Area | Reason for deferral |
| --- | --- |
| Callbacks, links, and webhooks | They need event/message/channel semantics closer to AsyncAPI than the current request/response emitter surface. Revisit after AsyncAPI shapes are known. |
| Multiple server selection and path/operation-level server overrides | Current IR captures the selected service base URI and base URI variables. Add explicit server alternatives only when generated clients need runtime server selection. |
| OAuth2/OpenID Connect flow details | Current IR preserves security scheme names, requirements, and transport parameters needed by emitters. Full auth flow modeling belongs with runtime auth adapter design. |
| Exhaustive JSON Schema 2020-12 keywords | Current fixtures cover schemas used by generated types today. Keywords such as `prefixItems`, `contains`, `dependentSchemas`, `if`/`then`/`else`, `unevaluatedProperties`, and content assertions should be added by demand with fixtures. |
| Source-location fidelity for every schema keyword | Existing source metadata is enough for declaration identity and generated placement. Revisit parser choice if diagnostics or generator placement need more precise source locations. |
| Alternative OpenAPI parser evaluation | The native YAML mapper is intentionally small. Evaluate a dedicated OpenAPI 3.1/3.2 parser if fidelity gaps accumulate around JSON Schema 2020-12 semantics, source-location preservation, or future OpenAPI features. |

## Exit Decision

OpenAPI 3.1 is wrapped for the first source-spec-to-IR milestone.

Before AsyncAPI starts, no additional OpenAPI IR mapping is required. Future OpenAPI work should be driven by concrete specs or emitter/runtime features, and should keep the same rule used in this pass: add a source fixture, snapshot the expected YAML IR, verify the failing test, then implement the smallest mapping needed.
