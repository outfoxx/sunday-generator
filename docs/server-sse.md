# Server-sent event response modes

Kotlin/JAX-RS server generation supports three SSE response representations. The default is `typed`.
The mode changes the server transport signature, not the event payload schema or generated client decoding.
All client generators ignore this server setting in contracts, including composed contracts.
The CLI rejects an explicitly supplied `-sse-response-mode` when generating clients.

| Mode | Quarkus server signature | Standard JAX-RS server |
| --- | --- | --- |
| `typed` | Existing `Multi<Payload>` behavior | Existing `Sse`/`SseEventSink` behavior |
| `outbound-event` | `Multi<jakarta.ws.rs.sse.OutboundSseEvent>` | `Unit` with the configured Jakarta/Javax `Sse` and `SseEventSink` parameters |
| `raw` | `RestMulti<ByteArray>` | Rejected; this mode requires Quarkus REST |

Existing explicit `sunday.sse` behavior is retained in `typed` mode. Quarkus uses Jakarta packages;
`-use-jakarta-packages` selects Jakarta rather than Javax for the portable JAX-RS target.
The portable target does not gain a dependency on Mutiny.

## Signature matrix

The following matrix assumes `GET /events`, a response declared as `text/event-stream` with payload
`DeviceEvent`, and no legacy `sunday.sse` flag. The string row instead uses a string payload schema.
All CLI examples use the `kotlin/jaxrs` target. Method bodies, `public`, and contract-specific
security and parameter annotations are omitted.

| Framework | App response/event type | Generator mode | SSE mode | Generated signature and method annotations | CLI enablement |
| --- | --- | --- | --- | --- | --- |
| Quarkus | `DeviceEvent` | Server | `typed` | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(): Multi<DeviceEvent>` | `-mode server -quarkus`; `typed` is the default |
| Quarkus | `String` payload | Server | `typed` | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(): Multi<String>` | Same flags; declare a string payload schema |
| Quarkus | `OutboundSseEvent` carrying `DeviceEvent` | Server | `outbound-event` | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(): Multi<OutboundSseEvent>` | `-mode server -quarkus -sse-response-mode outbound-event` |
| Quarkus | `ByteArray` containing complete SSE framing | Server | `raw` | `@GET`<br>`@Path(value = "/events")`<br>`fun events(): RestMulti<ByteArray>` | `-mode server -quarkus -sse-response-mode raw` |
| Standard JAX-RS | Events sent through `SseEventSink` | Server | `typed` | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(@Context sse: Sse, @Context sseEvents: SseEventSink)` | `-mode server`; `typed` is the default |
| Standard JAX-RS | `OutboundSseEvent` sent through the sink | Server | `outbound-event` | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(@Context sse: Sse, @Context sseEvents: SseEventSink)` | `-mode server -sse-response-mode outbound-event` |
| Standard JAX-RS | Raw SSE bytes | Server | `raw` | Generation error: raw SSE requires Quarkus | Unsupported |
| Quarkus | Received `DeviceEvent` | Client | Contract setting ignored; omit CLI option | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(): Multi<DeviceEvent>` | `-mode client -quarkus` |
| Standard JAX-RS | Received events through `SseEventSource` | Client | Contract setting ignored; omit CLI option | `@GET`<br>`@Path(value = "/events")`<br>`@Produces(value = ["text/event-stream"])`<br>`fun events(): SseEventSource` | `-mode client` |

The contract payload stays `DeviceEvent` when selecting `outbound-event` or `raw`. These settings
change the server transport signature; they do not change the client's expected payload schema.
A typed `String` is event data, not a preformatted SSE frame.

With `-resource-adapters`, the method annotations appear on the generated resource; the application
delegate has the same types and parameters without transport annotations, including `@Context`.
Without adapters, the annotations appear on the generated server interface. Quarkus resources also
have class-level `@Path`/`@Singleton` and an injected delegate constructor; Quarkus client interfaces
have `@RegisterRestClient`. Class-level media defaults depend on the configured default media types
and are removed when raw SSE routes are present, as described below.

If the contract/configuration supplies a non-SSE element media type such as `application/json`,
Quarkus additionally emits `@RestStreamElementType(value = "application/json")` for typed/envelope
methods. A response declared directly as `text/event-stream`, as assumed above, does not itself
trigger that annotation. Raw methods emit neither `@Produces` nor `@RestStreamElementType`.

## Configuration

Choose a default for server SSE operations:

```sh
sunday kotlin/jaxrs -mode server -quarkus -resource-adapters \
  -sse-response-mode outbound-event --placement-config placement.yaml -out generated api.yaml
```

The CLI deliberately keeps the short spelling `-sse-response-mode`. It requires `-mode server`
when supplied explicitly, including `-sse-response-mode typed`. With `-mode client`, or when `-mode`
is omitted and therefore defaults to client, it fails before generation:

```text
-sse-response-mode is only supported with -mode server
```

Omitting `-sse-response-mode` remains valid in either generation mode. Contract annotations remain
server-only metadata ignored by client generation, so the same contract can generate both sides.
Kotlin and Gradle configuration use the server-qualified name `sseServerResponseMode`; client
generators continue to ignore that programmatic server setting.

In Gradle, set `sseServerResponseMode.set(GeneratedSseServerResponseMode.OUTBOUND_EVENT)` on the generation or task.
The enum is `io.outfoxx.sunday.generator.ir.GeneratedSseServerResponseMode`; its other values are `TYPED` and `RAW`.

An operation can override the default, including resetting it to `typed`. OpenAPI uses:

```yaml
x-sunday-jaxrs:
  sse-server-response-mode: outbound-event
```

Place this on an OpenAPI operation or path item, or an AsyncAPI 2 channel/subscribe operation or
AsyncAPI 3 channel/receive operation. Operation settings take precedence over path/channel settings.
RAML uses `(sunday.sseServerResponseMode): outbound-event` on a method or trait, alongside its existing
SSE/event-stream declaration. Sunday IR retains the setting as `jaxrs.sseServerResponseMode`.

When OpenAPI supplies HTTP framing for an AsyncAPI stream, its response mode is carried into the
composed operation. Explicit conflicting modes fail composition in either source order.
Unknown values, modes on non-SSE server operations, raw mode on portable JAX-RS, and combinations
of a non-default mode with asynchronous `AsyncResponse` are rejected.

The global default affects only SSE operations. With resource adapters, resource and delegate
return types agree, and authentication, request validation, and parameter binding stay on the
generated resource. Interface-only server generation also supports these modes.

## Legacy `sunday.sse` interaction

RAML's `(sunday.sse): true` remains supported. It applies to both generation modes unless overridden
by `(sunday.sse:server)` or `(sunday.sse:client)`; a mode-specific `false` overrides a general `true`.
The IR equivalent is `jaxrs.sse` with `all`, `server`, and `client` flags. Disabling the legacy flag
does not disable an independently declared or inferred event stream.

**`typed` is the compatibility baseline, not a command to undo the legacy flag.** With `typed` or no
new mode setting, the existing flag-only sink/source behavior and event-stream envelope behavior
are retained. The following table assumes the legacy flag is enabled for the selected generator
mode, coroutine service methods are disabled, and no explicit asynchronous `AsyncResponse` setting.
All methods also carry `@GET`, `@Path(value = "/events")`, and
`@Produces(value = ["text/event-stream"])`, plus Quarkus's element-media annotation when applicable.

| Legacy declaration | Framework | Generator mode | Signature with `typed` or no new setting |
| --- | --- | --- | --- |
| `sunday.sse: true` alone, without an event-stream declaration | Quarkus or standard JAX-RS | Server | `fun events(@Context sse: Sse, @Context sseEvents: SseEventSink)` |
| Same flag-only declaration | Quarkus or standard JAX-RS | Client | `fun events(): SseEventSource` |
| `sunday.eventStream: simple` plus `sunday.sse: true` | Quarkus | Server | `fun events(@Context sse: Sse, @Context sseEvents: SseEventSink): Multi<OutboundSseEvent>` |
| Same simple-stream declaration | Quarkus | Client | `fun events(): Multi<InboundSseEvent>` |
| `sunday.eventStream: discriminated` plus `sunday.sse: true` | Quarkus | Server | `fun events(@Context sse: Sse, @Context sseEvents: SseEventSink): Multi<CommonEvent>` |
| Same discriminated-stream declaration | Quarkus | Client | `fun events(): Multi<CommonEvent>` |
| Either event-stream declaration plus `sunday.sse: true` | Standard JAX-RS | Server | `fun events(@Context sse: Sse, @Context sseEvents: SseEventSink)` |
| Either event-stream declaration plus `sunday.sse: true` | Standard JAX-RS | Client | `fun events(): SseEventSource` |

`CommonEvent` denotes the generated common event base type, or the generator's existing fallback
when no common base exists. The discriminated path retains payload objects rather than substituting
the legacy inbound/outbound envelope types.

With `-coroutines` (coroutine service methods), the legacy Quarkus event-stream signatures omit the
`Sse`/`SseEventSink` parameters and remain non-suspending `Multi` methods. Portable event streams
retain their sink parameters. For a flag-only declaration without an event stream, the legacy
coroutine path emits `suspend fun events()` on servers without sink parameters and
`suspend fun events(): SseEventSource` on clients. These signatures are preserved for compatibility;
the new modes do not repair or redefine the legacy coroutine API.

The new server modes take precedence over these legacy signatures:

| Effective new mode | Quarkus server with legacy SSE enabled | Standard JAX-RS server with legacy SSE enabled | Client effect (contract/programmatic setting) |
| --- | --- | --- | --- |
| `typed` | Preserve the legacy signature above | Preserve the legacy signature above | None; preserve legacy behavior |
| `outbound-event` | `fun events(): Multi<OutboundSseEvent>` with SSE media annotations and no automatically added sink parameters | `fun events(@Context sse: Sse, @Context sseEvents: SseEventSink)` with SSE media annotations | None; preserve legacy behavior |
| `raw` | `fun events(): RestMulti<ByteArray>` without SSE media annotations or automatically added sink parameters | Generation error | None; preserve legacy behavior |

An explicitly supplied CLI `-sse-response-mode` is rejected for clients even when the legacy flag is enabled.

Both non-default server modes are non-suspending regardless of coroutine options and reject an
explicit asynchronous `AsyncResponse` setting. These precedence rules apply whether the mode comes
from the CLI/Gradle default or an operation override. An explicit operation mode of `typed` restores
the legacy behavior, not necessarily `Multi<DeviceEvent>`. Explicitly requested context parameters
are still separate from the automatically added sink parameters discussed here.

For new Quarkus APIs needing server envelopes while preserving typed clients, declare an event
stream and select `sse-server-response-mode: outbound-event` without adding the legacy `sunday.sse` flag.
For example, use `(sunday.eventStream): simple` with `(sunday.sseServerResponseMode): outbound-event` in
RAML. The server returns `Multi<OutboundSseEvent>` and the client retains `Multi<DeviceEvent>`.

## Standard envelopes

Use Quarkus's injected `Sse` builder to construct events with an ID, event name, comment, retry delay,
and payload. `reconnectDelay` represents the standard `retry` field. A comment-only event can serve
as a heartbeat. Set the event media type explicitly when supplying a generated JSON payload.
The event builder's data type determines payload serialization; it does not change the contract's
payload model or client decoder.

On portable JAX-RS, construct the same envelope through the provided `Sse` and send it through the
provided sink. Close the sink when the application finishes producing events.

`OutboundSseEvent` cannot carry arbitrary fields such as `retry-max` or `keepalive`. Subclassing it
or registering an ordinary `MessageBodyWriter` does not extend Quarkus's dedicated SSE framing path.
A heartbeat comment also does not advertise a keepalive interval.

Quarkus 3.39.4 commits standard SSE headers before subscribing. Its SSE path does not apply the
headers/status supplied by `RestMulti`, including synchronous `fromMultiData` wrappers. Applications
needing connection headers must set them before commitment through an appropriate framework context;
they cannot change them after streaming starts. `RestMulti.fromUniResponse` is not an SSE header escape hatch.

## Scoped raw responses

Use `raw` only where the application must own the complete wire framing. The delegate returns a
`RestMulti<ByteArray>` containing already encoded bytes and must set `Content-Type: text/event-stream`.
For example, construct the result with `RestMulti.fromMultiData(frames).header("Content-Type", "text/event-stream").build()`.
Use the synchronous builder to supply status, headers, and demand. Browser EventSource expects status 200;
the ability to select a different status does not imply every SSE client accepts it.

Raw methods deliberately have no SSE `@Produces` or stream-element annotation. The generator removes
inherited class-level response media defaults and emits explicit media annotations on neighboring
ordinary/typed methods. This selects Quarkus's byte-stream path without registering a second route
or replacing the normal SSE serializer. The raw method consequently has no declarative `Accept`
restriction: applications requiring negotiation must enforce it themselves. Ordinary methods keep
their normal media negotiation.

Quarkus propagates disconnection to the publisher and requests one item at a time by default.
Do not collect an unbounded stream or detach a manual subscription from the returned publisher.
The application controls how chunks form UTF-8 SSE records, including the final blank line; transport
chunk boundaries are not record boundaries. Validate extension field names, reject embedded CR/LF in
single-line metadata values, encode multiline data/comments correctly, and validate policy numbers.
The raw transport does not sanitize bytes or validate event payloads.

Generated request validation remains active. Streaming responses already bypass ordinary response-entity
validation; neither raw bytes nor `OutboundSseEvent` adds automatic per-event payload validation.
Validate payloads before encoding/building events if the application requires it. Once the response is
committed, failures cannot be converted into a new HTTP status/problem response.

This mode preserves existing in-band extension fields. Moving them into HTTP response headers or
named policy events is a separate protocol change requiring client support on each connection/reconnect.
No new Sunday runtime dependency is required by either mode.

## Verification

`KotlinJAXRSSseServerResponseModeTest` compiles generated source before inspecting it, covering RAML,
OpenAPI, AsyncAPI 2/3, composition, client parity, namespaces, defaults/overrides, legacy SSE precedence, and invalid combinations.
`SseServerResponseModeTest` in the Quarkus integration suite exercises generated routes over real HTTP:
standard metadata and JSON, exact raw policy bytes, status/headers, parameter binding, authentication,
request validation, cancellation, bounded demand, and neighboring media negotiation.
Portable JAX-RS envelope coverage uses Jersey. Quarkus runtime coverage is JVM-based on 3.39.4;
native-image and deployment proxy behavior are not established by these tests.
