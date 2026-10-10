# Environment-aware generation

Sunday uses one model hierarchy across requests and responses. Client/server role, named deployment
profile, and payload mode are independent. A response model can be stored, edited, and submitted without
converting it to a second model type.

## Selecting an environment

All generation entry points expose `profile` and `defaultTolerance`. Tolerance defaults to `response`,
permitting declared fallbacks in responses while keeping requests strict. `all` also permits them in
requests. Explicit schema settings override this default; it does not add fallbacks to closed schemas.

```shell
sunday kotlin/jaxrs -mode client -quarkus -profile internal -default-tolerance response -out generated api.yaml
sunday swift/sunday -profile external -default-tolerance response -out generated api.yaml
```

```kotlin
sundayGenerations {
  create("client") {
    profile.set("internal")
    defaultTolerance.set(io.outfoxx.sunday.generator.Tolerance.Response)
  }
}
```

Programmatic option objects implement `EnvironmentGenerationOptions`. Use `GenerationContext` when
resolving or projecting metadata. The IR format remains version 1.

Policies and security bindings share `all`, `client`, `server`, and `profiles.<name>` scopes. Each
declaration resolves shared values, role overrides, selected-profile shared values, then selected-profile
role overrides. Local declarations override inherited declarations. Missing members inherit.
Operation tags continue to control service grouping and exclusion independently of environment scopes.

## Directional model validation

| Payload mode | Boundary |
| --- | --- |
| Request | A client sends or a server receives |
| Response | A server sends or a client receives |

Both modes check schema constraints. A declared enum fallback or tolerant union is permitted in
responses. Requests reject fallback instances by default, including manually constructed fallbacks
whose raw value looks recognized. `x-sunday-tolerance: response` explicitly retains strict requests;
`x-sunday-tolerance: all` allows fallback values in requests. RAML uses `(sunday.tolerance)` with the same
values. A schema setting overrides the build default. Neither setting adds a fallback to a closed enum
or an ordinary union. Enum fallbacks still require `x-unknown-value`.

Constructors and standalone codecs use response semantics. Request boundaries validate the current
model on every execution, including deferred requests, so an earlier successful application check does
not authorize later mutations. Omitted PATCH properties remain omitted; an explicit null is checked
separately. Validation does not change application-owned model values.

Use the target's native validation API where available:

- **Kotlin:** Hibernate Validator / Bean Validation groups `ModelMode.Request` and
  `ModelMode.Response`, from `io.outfoxx.sunday.validation.javax` or `.jakarta`. For example,
  `validator.validate(item, ModelMode.Request::class.java)` returns native violations. Constructors and
  Sunday boundaries use the same constraints. Named scalar, collection, and constrained union schemas
  expose native validation views, for example `validator.validate(CodesValidation(codes), ModelMode.Request::class.java)`.
  The view retains the original value and checks current collection contents on each call.
  Select a payload mode explicitly; dynamically typed extension containers cascade that mode through
  nested lists and maps using the application's registered native validator. The `Default` group alone
  does not select directional extension validation.
  Named model values in extension storage retain their generated model type; replacing them with a raw
  map or Jackson tree is rejected at a model boundary. Overlapping object patterns preserve the decoded
  model's identity and use native property constraints to validate the other participating schemas,
  without constructing or serializing replacement models. Inherited additional-property constraints
  remain effective after decoding and later mutation.
  Generated model support requires the matching
  `sunday-validation-javax` or `sunday-validation-jakarta` artifact. Quarkus integration uses
  `sunday-jaxrs-quarkus` and the application's managed validator. Kotlin consumers must compile with
  `-Xemit-jvm-type-annotations` so native validation sees collection-element constraints. The Gradle
  generator plugin enables this for Kotlin/JVM projects; CLI consumers configure their Kotlin compiler.
  JAX-RS clients register `ClientModelValidation`; plain JAX-RS servers register
  `ServerModelValidation` alongside their native Bean Validation integration. Quarkus discovers the
  server adapter from the runtime artifact. Invalid request entities produce HTTP 400 before application
  invocation; invalid application responses produce HTTP 500 before encoding. Client request and response
  codecs select their respective schema metadata independently, including root collections.
- **TypeScript:** resolve the existing Zod schema with `runtime.forMode('request')` or
  `runtime.forMode('response')`. For example,
  `runtime.forMode('request').resolveSchema(ItemSchema).safeEncode(item)` checks a model in its encoding
  direction. Native `parse` / `safeParse` check wire input. The same schema definition owns predicates
  in both directions.
- **Python:** use Pydantic's context, for example
  `Item.model_validate(item, strict=True, context={'mode': ModelMode.REQUEST})`, or the equivalent
  `TypeAdapter` call. `SundayModel` revalidates current instance fields rather than accepting an existing
  instance unchecked. The Litestar `SundayPlugin` validates request entities before application code and
  reports invalid input as HTTP 400.
  Instance revalidation reports `model_key_conflict` when an extra property collides with a field's
  wire alias, or when changing alias/name lookup would overwrite a distinct value. The diagnostic
  retains the original property path. Ordinary input alias selection remains native Pydantic behavior;
  existing instances, extras, and presence sets are never modified by validation.
- **Swift:** objects and enums implement `isValid(_:)` and `validate(_:)` with an explicit `.request` or
  `.response` argument. Named schemas also have a type-associated validator, such as
  `ItemValidation.isValid(item, .request)`. `validate` collects diagnostics while calling the canonical
  `isValid` implementation once. `ModelValidationError` exposes reason codes and wire paths. Initializers,
  codecs, and transport hooks delegate to the generated validator. Lazy field views retain the schema
  identities covered by a stored subtype. Independent overlapping schemas therefore check their own
  restrictions, while already validated decoded children avoid redundant subtype checks.

Constraints declared alongside union alternatives belong to the union use site. A reusable payload may
therefore be valid independently but invalid under a union with tighter common constraints. Native
validation metadata and Swift's canonical validators preserve that distinction without replacing the
payload model. Checks apply during decoding and again at the payload boundary, including after mutation.
Common discriminator constraints inspect the wire tag without converting a reusable payload's enum to
another enum class. Absent optional fallback fields remain absent in validation views, matching encoding.
Kotlin decoding views use the codec's converted temporal values while retaining exact numeric tokens,
wire presence, and unknown keys for common constraints. Existing timestamp serialization stays unchanged.
Swift also validates event envelopes, external discriminators, and unions stored as `AnyValue` through
normalized field views; these checks do not construct or reparse application models.

Typed path, query, header, and cookie parameters participate in request validation wherever the target
supports that parameter location. Conversion can retain an unknown value; request-mode validation then
rejects it before transmission or server delegate invocation unless the schema permits request tolerance.
Collection elements follow the same rule. Sunday transports invoke the generated `parameterValidation`
(`parameter_validation` in Python) callback on every request build, including bodyless and deferred
requests, so mutating a captured collection cannot bypass a previous check. The callback delegates to
the same native model validators (or Swift canonical validators) as body validation. Optional parameters
remain omitted when absent. Invalid event requests close without reconnecting; event APIs preserve
native validation failures within their transport error contract. Raw binary streams retain their
transport-specific contracts.

## Client parameter defaults

Client arguments preserve schema nullability and also allow absence when a parameter is optional or
has a declared default. This applies to path, query, header, and cookie parameters where the target
supports them, and to generated base-URI helpers. Required parameters without nullability or a default
remain required. Body payloads keep their existing presence contract.

Client methods use language-level defaults when arguments are omitted (`undefined` in TypeScript).
Explicit `null`, `nil`, or `None` remains absent, including for path parameters and base-URI variables.
Sunday omits absent parameter values and expands undefined URI-template variables according to RFC 6570:
`/items{/id}` becomes `/items` when `id` is absent, while an empty string produces `/items/`.
Native frameworks retain their own parameter-conversion behavior. The generator does not classify path
templates or replace explicit nulls with defaults. Enum and URI defaults retain their declared types,
and zero and false values are preserved.

JAX-RS client methods also declare Kotlin default arguments, including aggregate subresource locators;
explicit nulls reach the native proxy unchanged. Server defaults remain framework-owned: JAX-RS uses
`@DefaultValue`, and Litestar supplies defaults even for parameters marked required in the contract.
Runtime validation checks supplied values without substituting defaults for absent values.

## Scoped policies

```yaml
x-sunday-policy:
  all:
    timeout: PT5S
  client:
    rateLimit: {value: 3, window: PT1S}
  server:
    rateLimit: {value: 7, window: PT1S}
    circuitBreaker: {failureRatio: 0.5}
  profiles:
    internal:
      client:
        retry:
          maxRetries: 1
          retryOn: [{problem: unauthorized}]
          abortOn: [{problem: forbidden}]
```

Tags, paths, and operations contribute policy declarations in that order. Conflicting peer tags are
errors. `false` disables an inherited policy; exception lists replace inherited lists, and `[]` clears a
list. Exception references may be class names or `{problem: name}` references to generated exceptions.
Flat policies and `clientRateLimit` / `serverRateLimit` are rejected; update specifications directly.

Quarkus emits native SmallRye annotations on concrete resource boundaries and selected client methods.
Delegates do not repeat server enforcement. `skipOn` uses SmallRye's native behavior: matching outcomes
count as successes in the circuit breaker's rolling window.

## Client acquisition and server providers

Security schemes describe wire requirements. Scoped `x-sunday-security` bindings select application
providers. They do not replace logical schemes, OAuth scopes, server issuer/audience trust, or complete
AND/OR alternatives. Public `security: []` operations remain public. If multiple configured alternatives
are usable, select a complete alternative explicitly. See [the IR security contract](sunday-ir-format.md#scoped-security-bindings).

Profiles containing bindings must be selected explicitly. Plain HTTP bearer schemes never imply token
acquisition. Client credentials, authorization codes, token stores, and secrets remain runtime inputs.
Acquisition URLs may be supplied by the selected application's provider when deployment configuration
owns them. A provider must resolve missing endpoints before attempting an OAuth exchange.

API, path, and operation declarations can override a scheme's acquisition binding by logical name:

```yaml
x-sunday-security:
  profiles:
    internal:
      client:
        bindings:
          bearerAuth: {provider: graphs, flow: clientCredentials}
    external:
      client:
        bindings:
          bearerAuth: {provider: application, flow: external}
```

These overrides follow declaration precedence and survive composition. Separate services can share
one wire scheme while selecting different application providers. They never alter `security` requirements.

Server bindings remap each logical scheme to the selected validation provider; applications register
that provider using the generated security setup API.

Sunday's Kotlin, Swift, TypeScript, and Python runtimes expose a `TokenManager` and application token
providers. Their transports use generated bindings to acquire credentials. OAuth providers support
client credentials, application-managed authorization-code/PKCE, and refresh-token rotation. External
and static providers supply credentials through the same contract. An authorization-code session that
cannot refresh requires fresh application authorization; it never reuses a consumed code.

Token identity includes provider/client identity, profile, endpoints, scopes, audience/resource, and
relevant grant inputs. Shared managers coalesce concurrent renewal, apply expiry skew, and honor
cancellation. Applications must change client/grant identity when those inputs change. Sunday transports
allow at most one authentication recovery per invocation and only for a safe, bodyless request with an
invalid-token bearer challenge. Quarkus associates each native transport attempt with its own identity;
late responses and credential callbacks cannot change a later attempt's recovery classification. They do
not automatically replay 403 responses or unsafe requests.

### Supported client flows

| Target | Client credentials | Application-managed authorization code / PKCE | External / static provider | Refresh-token rotation |
| --- | --- | --- | --- | --- |
| Kotlin / Sunday | Supported | Supported | Supported | Supported |
| Swift / Sunday | Supported | Supported | Supported | Supported |
| TypeScript / Sunday | Supported | Supported | Supported | Supported |
| Python / Sunday | Supported | Supported | Supported | Supported |
| Kotlin / Quarkus REST client | Native named OIDC client | Diagnosed as unsupported | Use an application REST provider without acquisition bindings | Native OIDC lifecycle |

Applications own interactive authorization and token storage. The four Sunday clients use the same
bounded recovery contract; the Quarkus integration shares its invocation budget with native fault-tolerance
policies. Plain JAX-RS transports retain explicit application providers and diagnose acquisition bindings
that require the Quarkus integration.

### Quarkus named OIDC clients

Quarkus client generation supports bearer `clientCredentials` bindings through native named OIDC
clients. Method-level `@ClientAuthentication` bindings select isolated clients by profile, acquisition
endpoints, and scopes. A binding's `provider` names application configuration:

```properties
quarkus.oidc-client.service-identity.client-id=my-client
quarkus.oidc-client.service-identity.credentials.secret=${CLIENT_SECRET}
# Optional deployment override of the selected binding's acquisition endpoint:
quarkus.oidc-client.service-identity.token-path=https://deployment.example/token
```

Generated defaults supply the grant, declared scopes, and selected endpoints. They defer token
acquisition until an authenticated method executes. Native OIDC handles caching and refresh rotation.
The application can override acquisition configuration without changing server trust settings.

Include generated `META-INF/services` resources when packaging. The Gradle plugin merges registrations
from all generations in each source set. Other build systems must retain and merge those descriptors.
The application needs `sunday-client-quarkus`, which includes the reactive REST client, native OIDC
client, and SmallRye Fault Tolerance extensions. The credential filter delegates acquisition, expiry,
and rotation to Quarkus's `TokensHelper`; concurrent recovery of a rejected lease is coalesced.

Generated public methods allocate one invocation context. Their `WithRetry` helpers own the native
`@Retry` policy, and their `Transport` helpers retain timeout, rate-limit, and circuit-breaker policies.
Applications call the public methods; native method-specific configuration addresses the corresponding
helper. Typed retry and breaker exception lists retain their declared meaning. Authentication recovery
uses the declared retry budget, or one authentication-only retry when no retry policy is declared.
Repeated 401 responses, missing invalid-token challenges, 403 responses, and unsafe requests stop
authentication replay. The original native exception reaches the application. Streamed subscriptions
are never automatically replayed; an explicit retry policy on an acquired streaming client is diagnosed.

Do not combine a legacy class-level `rest-client.oidc-client` setting with scoped bindings on the same
client; generation diagnoses that conflict. Unsupported native acquisition flows or transports produce
an actionable diagnostic. Use a Sunday client or an explicit application JAX-RS provider for those flows.

## Public metadata and rollout

Project source or IR metadata to the external client profile before distributing SDK contracts.
`GeneratedSourceEnvironmentProjection` and `GeneratedApi.projectEnvironment` share this behavior:
internal bindings and policies are removed while wire security and applicable external metadata remain.
Standard OAuth/OIDC endpoints must already be public; projection does not rewrite them.

The environment-aware output requires these companion runtimes:

| Target | Runtime version | Distribution |
| --- | --- | --- |
| Kotlin | `2.0.0-beta.14` | Maven Central, including validation and Quarkus client artifacts |
| Swift | `2.0.0-beta.15` | Swift Package Manager Git tag |
| TypeScript | `2.0.0-beta.11` | npm; Node.js 22 or later |
| Python | `2.0.0-beta.10` | Released Git tag until PyPI publication |

Compiler-backed tests use these released dependencies by default. Set `SUNDAY_KOTLIN_PATH`,
`SUNDAY_SWIFT_PATH`, `SUNDAY_TYPESCRIPT_PATH`, or `SUNDAY_PYTHON_PATH` explicitly to verify a runtime
checkout. The TypeScript checkout must already be built. `-PuseLocalSundayKt=false` disables Kotlin
composite substitution even when its path is set.

Release and verify companion runtime artifacts before distributing generator output that references
new APIs. Then generate and compile server stubs, internal clients, and external SDKs before promoting a
consumer's shared generator version.


## Configuration-based client construction

Sunday HTTP clients generate server configuration types and require an application-supplied transport
factory. Server security profiles can be selected independently of the generation profile used for
policies. Client projection preserves all applicable client security profiles for these factories.
See [client configurations](client-configurations.md) for credentials, transport adapters, profile
precedence, and the `generateClientConfig` opt-out.
