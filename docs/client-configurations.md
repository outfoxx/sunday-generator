# Client configurations

Sunday HTTP clients can be constructed from server configuration and an application-supplied transport
factory. Configuration generation is enabled by default in Kotlin, Swift, TypeScript, and Python.
Generated code imports runtime contracts only; the application chooses an installed transport module.
Existing transport-instance constructors and factories remain available.

A single server produces `<ApiName>Config`. Multiple servers produce `<ApiName><ServerName>Config`,
using OpenAPI server names or `Server1`, `Server2`, and so on when unnamed. Server variables are flat,
typed fields with their declared defaults, documentation, and enum restrictions. Equivalent declarations
can share a configuration across services. Conflicting generated names and incompatible service or
aggregate endpoints are errors.

For example, an API with a `tenant` server variable and an `identity` bearer scheme can use:

```typescript
import { FetchTransport } from '@outfoxx/sunday';
import { createAPI } from './generated/api.js';

const client = createAPI(
  { tenant: 'secondary' },
  settings => FetchTransport.fromSettings(settings),
  { credentials: { identity: { kind: 'bearer', token: accessToken } } },
);
```

The callback is required. It is invoked exactly once after configuration, credentials, server selection,
and security alternatives have been validated. Its immutable runtime settings contain the resolved
endpoint, operation-specific security bindings, and the selected token provider instances. Preparing settings never acquires tokens or performs authentication requests. The returned
client preserves the transport's native request/response types. Applications own transport lifecycle
and cleanup.

Transport adapters include `ClientSettings.jdkTransport` and `ClientSettings.okhttpTransport` in the
corresponding Kotlin modules, `URLSessionTransport(settings:)` in Swift, `FetchTransport.fromSettings`
in TypeScript, and `HttpxTransport.from_settings` in Python. The HTTPX adapter borrows the application's
client and checks that its base URL matches the resolved endpoint. Native constructors remain available
inside the callback for additional transport options.

## Aggregate clients

With `-aggregate-services -aggregate-service-name ExampleAPI`, the aggregate has the same configuration
factory surface as an individual service: `createExampleAPI` in Kotlin, Swift, and TypeScript, and
`create_example_api` in Python. Credentials and security alternatives are named for the aggregate
(`ExampleAPICredentials` and `ExampleAPISecurityAlternative`). The existing transport entry point remains
available.

The aggregate resolves security for every child operation before invoking the transport callback once.
All children receive the same transport and resolved settings, so server-selected profiles, explicit
profile overrides, scopes, public operations, and provider ownership remain consistent across services.
Credential and alternative validation happens before constructing any child. All services must have
compatible effective server configurations and unique operation security identities.

## Provider ownership and optional caching

Factories preserve application-supplied providers in `settings.tokenProviders` (`token_providers` in
Python). Transports use those instances directly. No implicit manager, cache, refresh loop, or manager
factory is created. A provider backed by an external framework such as Auth0 owns its entire credential
lifecycle; supply that adapter directly through provider credentials.

To opt into Sunday's caching, refresh coalescing, and persistence, wrap the provider explicitly and pass
the manager in the same credential slot:

```typescript
import { FetchTransport, TokenManager } from '@outfoxx/sunday';

const manager = new TokenManager(provider, { store: applicationStore });
const client = createAPI(config, settings => FetchTransport.fromSettings(settings), {
  credentials: { identity: { kind: 'provider', provider: manager } },
});
```

Equivalent manager construction in other runtimes:

```kotlin
val manager = TokenManager(provider = provider, store = applicationStore)
val credentials = APICredentials(identity = ProviderCredentials(manager))
```

```swift
let manager = try TokenManager(provider: provider, store: applicationStore)
let credentials = APICredentials(identity: ProviderCredentials(provider: manager))
```

```python
manager = TokenManager(provider, store=application_store)
credentials = APICredentials(identity=ProviderCredentials(manager))
```

Settings resolution and transport construction perform no token acquisition or storage I/O. Public
operations acquire no credentials. A managed operation acquires once per send attempt; a supported,
bounded invalid-token recovery obtains a new credential only for the retry. Providers may implement
rejection recovery against the original request context and rejected token. Providers without that
capability return the original unauthorized response without an automatic authentication retry.

Request-export APIs materialize an optionally authorized transport-specific request, acquiring once
when authorization is required. Execution, retries, refreshing, and lifecycle then belong to the consumer.
Exported requests do not retain hidden Sunday security state.

The application owns storage security, session/environment identity, and manager lifecycle. Share one
manager across clients that should share a cache: separate managers sharing a store do not coordinate
refreshes. Reopening a session may use a new manager with the same store and stable identities.
Distinct sessions/environments need distinct grant/provider/profile/endpoint identities or isolated stores;
the API base URL alone does not create a storage namespace.

Kotlin exposes clock, skew, and parent coroutine scope; close the manager when all sharing clients are done.
Swift accepts a Sendable store and clock; call `await manager.close()` at shutdown.
TypeScript supports `expirySkewMs` and a millisecond clock; Python supports `expiry_skew` and a seconds clock.
Those managers have no close method: cancel/await requests before discarding them.
Transport cleanup does not erase saved tokens. Invalidation retains refresh state and is not logout.
External providers must ensure rejection of an older token cannot invalidate a newer session.

### Migration and coordinated runtime updates

The `TokenManagerFactory` contract and generated `tokenManagerFactory` / `token_manager_factory` options
are removed. Move manager construction into application setup and pass it as a provider. Custom transport
factories forward `settings.tokenProviders` / `settings.token_providers` instead of a singular manager.

This generator requires Swift `2.0.0-beta.20`, Kotlin `2.0.0-beta.20`, TypeScript
`2.0.0-beta.19`, and Python Git tag `2.0.0-beta.14`, or compatible newer versions.
Compiler fixtures pin these releases. Older runtimes construct implicit managers and do not provide
the new settings surface. During coordinated development, set `SUNDAY_SWIFT_PATH`,
`SUNDAY_KOTLIN_PATH`, `SUNDAY_TYPESCRIPT_PATH`, and `SUNDAY_PYTHON_PATH` to updated runtime checkouts;
build the TypeScript runtime first.

## Kotlin native request and response types

Generated Kotlin HTTP services and aggregate clients carry both native types as `TReq` and `TRes`.
Their constructors accept `Transport<TReq, TRes>`, and generated factories infer both types from the
application's transport factory. For example, a JDK-backed client uses `java.net.http.HttpRequest` and
`java.net.http.HttpResponse<kotlinx.io.Source>`; an OkHttp-backed client uses `okhttp3.Request` and
`okhttp3.Response`. These type arguments identify native values, not Sunday's wrapper classes.

Request exports return `Request<TReq>` and expose the native request through `.transport`. Response
exports return `Response<TRes>`; operations, decoded operation responses, and event sources preserve
the corresponding native types. Execute managed requests through the transport or generated operation;
request wrappers no longer execute themselves. Sending an exported native request remains the consumer's
responsibility, including any subsequent response adaptation or retry policy.

The pinned Kotlin runtime includes this adapter and execution contract. Both JDK and OkHttp generated
clients are compiled against the published runtime in the generator's regression tests.

## Request and response adapters

Configure request and response adapters independently on the transport returned by the application
factory. Swift uses `RequestAdapter` and `ResponseAdapter`, TypeScript uses objects with `adapt`
methods, and HTTPX accepts asynchronous functions or adapter instances through `request_adapter`
and `response_adapter`. Use the runtime's explicit composition helpers when combining adapters.
Generated clients forward operation specifications and leave adaptation, response handling, and
retry decisions to the transport.

Request exports apply request adaptation once and return without sending. Raw-response operations
remain managed executions: Swift and TypeScript pass their operation specifications to
`transportResponse`, and Python passes its specification to `execute_request`. Python's
`transport_request` followed by a native send is the consumer-owned export path and does not preserve
the invocation's managed security recovery context.

Generated Swift event-stream methods now return `AsyncThrowingStream<Element, any Error>`.
Consume them with `for try await` and handle errors, including adapter stop signals. TypeScript's
`AsyncIterable` and Python's `EventStream` already propagate failures during iteration; their generated
return types do not need to change. The matching adapter runtime updates are required for these paths.

## Credentials and alternatives

Server configurations contain no credentials. Generated `<Service>Credentials` groupings use the
narrowest runtime credential family for each scheme: bearer, API key, Basic, OAuth, or a custom provider.
Runtime OAuth credentials select client-credentials or authorization-code/PKCE acquisition and carry an
application-selected provider factory. That factory constructs the provider from the installed module,
without acquiring tokens. Providers own refresh, caching, and cancellation behavior; an explicitly supplied TokenManager adds Sunday's lifecycle.

A factory selects one complete alternative per operation. Supplying only part of a conjunctive requirement
is invalid. When supplied credentials satisfy more than one alternative, select one explicitly with the
generated `<Service>SecurityAlternative` type and the operation-keyed security selection argument. These
alternatives retain both scheme sets and required scopes; public overrides remain empty requirements.
An alternative declared for one operation cannot introduce an undeclared alternative on another.

## Servers and security profiles

TypeScript uses `serverId` and Python uses `server_id` to distinguish configurations. TypeScript requires
the discriminator when multiple server configurations are generated; Python supplies it in the frozen
configuration dataclass. Kotlin and Swift dispatch through native overloads.

OpenAPI server inheritance is resolved operation → path → API before service grouping. AsyncAPI HTTP
factories retain authentication for the selected server instead of combining credentials from unrelated
servers. Non-HTTP broker factories and framework-specific clients are outside this feature.

Set `x-sunday-security-profile` on a server to declare its client security profile. RAML uses
`(sunday.security-profile)` on the API. Selection precedence is:

1. The explicit factory security profile.
2. The server's security profile.
3. The generation profile or applicable unprofiled bindings.

Unknown server profile references and unresolved ambiguity are errors. A policy-only generation profile
can coexist with unprofiled security. Choosing server security never changes generation-policy profiles.
An explicit `null`/`nil`/`None` factory override selects unprofiled bindings when available; omitting the
argument uses the precedence above.

Relative server URLs use the source document's HTTP(S) retrieval location, including referenced path
items. Local sources without a usable origin require the configuration's document-base URL. Relative
OAuth/OIDC endpoint URLs resolve against the expanded server URL. Security URL fields do not support
server-variable interpolation.

Client environment projection retains all client security profiles required by factories, while removing
server-role bindings. Policies still project to the selected role and profile. This means projected client
IR can contain endpoint metadata from multiple client security profiles; it is not a single-profile export.

## Opting out

Set `generateClientConfig = false` in programmatic options, pass `-no-client-config` on the CLI, or use
`generateClientConfig.set(false)` in a Gradle generation. This retains direct transport construction
without configuration factories. `-client-config` explicitly enables the default.
