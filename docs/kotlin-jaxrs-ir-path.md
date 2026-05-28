# Kotlin/JAX-RS IR Path

Kotlin/JAX-RS generation now renders from Sunday IR.

Current first slice:

- Added `KotlinJAXRSIrGenerator`.
- Generates shared object and enum model types needed by service signatures.
- Generates basic JAX-RS service interfaces from IR for server and client modes.
- Covers service-level `@Produces` and `@Consumes`.
- Covers method-level HTTP verb and `@Path` annotations.
- Covers request body parameters.
- Covers current return-shape parity for server `Response`, client typed returns, and no-content methods.
- Covers server `201 Created` `@Context UriInfo` injection.

Current second slice:

- Covers path, query, and header parameters.
- Preserves generated parameter names and wire names.
- Preserves optional/null parameter types.
- Emits JAX-RS `@DefaultValue` from structured IR defaults.
- Skips constant headers on servers and emits Quarkus/MicroProfile `@ClientHeaderParam` constants for clients.
- Emits basic validation annotations from IR, including object `@Valid` and scalar/collection constraint annotations.
- Emits explicit security parameters from IR security scheme metadata.

Current third slice:

- Emits operation-level `@Consumes` when request body media overrides service defaults.
- Emits operation-level `@Produces` when response media overrides service defaults.
- Preserves `sunday.jsonBody` mode-aware body overrides as Jackson `JsonNode` parameters.
- Emits request body object validation annotations from IR-backed model metadata.

Current fourth slice:

- Preserves declared response headers in client mode by returning the JAX-RS response wrapper.
- Uses standard `Response` for non-Quarkus clients and typed `RestResponse<T>` for Quarkus clients.
- Does not emit Quarkus `@ResponseHeader` for RAML response header schemas; that annotation is only appropriate for static header values and does not apply to `Response`/`RestResponse` returns.

Current fifth slice:

- Generates referenced problem types from IR for Quarkus, Sunday, and Zalando problem libraries.
- Emits Jackson subtype `registerProblems` companions from IR when referenced problems exist and Jackson annotations are enabled.
- Emits plain client `sunday.nullify` `OrNull` wrappers from IR, including status-based and problem-type catches.

Current sixth slice:

- Emits coroutine `suspend` service methods from IR.
- Wraps reactive service returns using the configured reactive type, including Quarkus `Uni`.
- Emits asynchronous server methods with `@Suspended AsyncResponse`.
- Emits SSE methods with `text/event-stream` produces metadata and server `Sse`/`SseEventSink` context parameters.
- Lowers `sunday.eventStream` operations to `Flow<T>` return shapes, including typed JAX-RS SSE event elements.

Current seventh slice:

- Emits service-level base URI `@Path` from IR, including default expansion and client/server base URI modes.
- Emits `sunday.jaxrsContext` parameters from IR and avoids duplicates with implicit `201 Created` `UriInfo`.
- Preserves explicit security parameter wire names for Quarkus `@RestHeader` output.
- Covers Quarkus SSE element type annotation parity through the direct IR path.
- Lowers Quarkus SmallRye Fault Tolerance policy metadata and server-side Quarkiverse Zanzibar authorization metadata from IR.
- Generates a Quarkus CDI `UserExtractor` bean for explicit Zanzibar JWT user-source metadata, using configured claims in order and only falling back to the request principal when `principalFallback` is explicitly enabled.

Current eighth slice:

- Routes the public Kotlin/JAX-RS service-generation path through source specs -> IR -> `KotlinJAXRSIrGenerator`.
- Preserves the current Kotlin/JAX-RS fixture suite through the IR path, including nested operation-local service types, freeform inline response maps, coroutine/reactive nullify wrappers, event-stream common base lowering, and container element validation.

Current ninth slice:

- Removes the bypassed AMF-backed service hook overrides and wrapper generator.
- Moves Kotlin/JAX-RS options to `KotlinJAXRSOptions`.
- Leaves public CLI and Gradle plugin generation as source specs -> IR -> Kotlin/JAX-RS while model/problem rendering remains owned by the IR renderer.

Current tenth slice:

- Adds an audit guard that the Kotlin/JAX-RS IR renderer does not import AMF or AMF service-domain types.
- Confirms the only remaining AMF boundary for Kotlin/JAX-RS source generation is RAML -> IR conversion.
- Marks Kotlin/JAX-RS service generation migration complete for the current fixture and snapshot surface.

## Exit Status

Kotlin/JAX-RS generation is fully IR-backed after source parsing. RAML uses AMF only before `RamlToGeneratedApi`; OpenAPI and AsyncAPI use native YAML readers before composition into `GeneratedApi`.
