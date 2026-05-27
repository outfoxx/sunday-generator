# AsyncAPI IR Exit Audit

Date: 2026-05-14

This audit closes the first AsyncAPI source-spec-to-IR pass. The goal is not to model every AsyncAPI protocol binding or broker-specific runtime behavior before emitter work resumes. The goal is to confirm that AsyncAPI can produce durable Sunday IR fragments, compose with OpenAPI fragments into one generated API shape, and be exported through the normal IR CLI path.

## Summary

AsyncAPI is ready to leave the foundation phase. The frontend parses AsyncAPI documents through the native YAML reader in `AsyncApiToGeneratedApi`, maps directly into `GeneratedApiFragment`, and composes those fragments with OpenAPI through the same `GeneratedApiComposer` identity rules used by the other source frontends. AMF is not part of the AsyncAPI source frontend.

There is no blocking AsyncAPI IR mapping required before moving on. The remaining items are protocol-specific interpretation, source-fidelity details that do not affect the current generated output shape, and future runtime behavior that should be driven by concrete broker/client requirements.

In other words, no blocking AsyncAPI IR mapping is required before returning to merged generation workflows.

## Implemented Coverage

| Area | Fixture or test | IR surface |
| --- | --- | --- |
| Native reader smoke | `project-events.yaml`, `typed-event-envelope-3.1.yaml`, `AsyncApiToGeneratedApiTest` | Confirms the native AsyncAPI reader loads AsyncAPI 2.x and 3.1 documents without AMF |
| First event vertical slice | `project-events.yaml` | API identity, service identity, subscribe operation, JSON message payload media, event-stream response, object payload model |
| Composition with HTTP APIs | `composition-identity-3.1.yaml`, `project-events.yaml` | AsyncAPI subscribe events merge into matching OpenAPI services by API and service identity |
| Operation and message metadata | `operation-surface.yaml` | Explicit `x-sunday-operationId`, publish vs subscribe semantics, message headers, request-only publish operations, response event streams, docs, and message examples |
| Schema breadth | `schema-breadth.yaml` | Component schema refs, object models, arrays, enums, nullable unions, maps, validation constraints, unique item collections, and wire-name preservation |
| Protocol and security metadata | `protocol-security.yaml` | Servers, protocols, protocol versions, server auth, security schemes, security requirements, and server/channel/operation bindings |
| Composition diagnostics | `composition-audit.yaml`, `composition-model-collision.yaml` | OpenAPI + AsyncAPI composition across multiple services/models, operation collision diagnostics, and model collision diagnostics |
| End-to-end export | `GeneratedApiIrExporterTest`, `IrCLITest` | AsyncAPI IR export, source kind detection, explicit `--source asyncapi`, and multi-source OpenAPI + AsyncAPI CLI composition |

## Composition Contract

OpenAPI + AsyncAPI composition is the expected standards-based replacement for the RAML extension pattern where event streams are attached to HTTP resources. The contract is:

- explicit `x-sunday-apiId`, `x-sunday-service`, and `x-sunday-operationId` values win
- native source ids are the first fallback, such as OpenAPI or AsyncAPI `operationId`
- deterministic generated ids are the final fallback
- collisions under default-derived ids are errors that name the missing override, such as `x-sunday-operationId` or `x-sunday-modelName`
- matching service identities merge event operations into the same generated service/client surface as related HTTP operations

RAML can participate in the same composition model later because its reader already emits `GeneratedApiFragment` identity metadata from Sunday annotations and groups.

## Deferred AsyncAPI Features

These do not block current IR use or the merged OpenAPI + AsyncAPI generation path.

| Area | Reason for deferral |
| --- | --- |
| Protocol-specific binding semantics | IR currently preserves raw server/channel/operation/message binding values. Kafka, WebSocket, AMQP, MQTT, and other protocol-specific lowering should be added when a runtime or emitter needs concrete behavior. |
| Message traits and channel parameters | Current fixtures cover message headers and channel identity. Add trait merging and channel parameter lowering when real specs require reusable message/channel metadata. |
| Multi-message alternatives | Current mapping covers a single operation message shape. Add oneOf/multiple-message selection rules when clients need event union dispatch or when AsyncAPI fixtures require multiple messages per operation. |
| Correlation id and reply metadata | These are runtime messaging concerns. Preserve them once request/reply generation has an explicit runtime model. |
| Broker-specific auth details | Current IR preserves security scheme names, requirements, and server auth. Full SASL, OAuth, mTLS, and broker credential modeling belongs with runtime auth adapter design. |
| Alternative AsyncAPI parser evaluation | The native YAML mapper is intentionally small. Evaluate a dedicated AsyncAPI parser if fidelity gaps accumulate around protocol binding semantics, source-location preservation, or future AsyncAPI versions. |

## Exit Decision

AsyncAPI is wrapped for the first source-spec-to-IR milestone.

Before returning to IR-backed generation and merged source workflows, no additional AsyncAPI IR mapping is required. Future AsyncAPI work should be driven by concrete specs, protocol runtimes, or emitter features, and should keep the same rule used in this pass: add a source fixture, verify the failing test, then implement the smallest mapping needed.
