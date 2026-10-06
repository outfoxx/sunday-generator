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
endpoint, operation-specific security bindings, and a prepared token manager when authentication is
configured. Preparing settings never acquires tokens or performs authentication requests. The returned
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
profile overrides, scopes, public operations, and token caching remain consistent across services.
Credential and alternative validation happens before constructing any child. All services must have
compatible effective server configurations and unique operation security identities.

## Application-owned token persistence

Both per-service and aggregate configuration factories accept an optional runtime `TokenManagerFactory`:
`tokenManagerFactory` in Kotlin and Swift, `options.tokenManagerFactory` in TypeScript, and
`token_manager_factory` in Python. The factory receives the complete resolved provider map and returns
one native manager; consumers do not reconstruct provider names or implement another cache/refresh layer.
There is no unused default manager when a factory is supplied. With no selected providers the hook is
skipped. Without a hook, settings continue to create their default memory-only manager.

For example, use the generated TypeScript entry point with an application-managed store:

```typescript
import { FetchTransport, TokenManager } from '@outfoxx/sunday';

const client = createAPI(config, settings => FetchTransport.fromSettings(settings), {
  credentials,
  tokenManagerFactory: providers => new TokenManager(providers, { store: applicationStore }),
});
```

Equivalent hooks for native manager options are:

```kotlin
tokenManagerFactory = { providers ->
  TokenManager(providers, store = applicationStore, expirySkew = skew, clock = clock, scope = scope)
}
```

```swift
tokenManagerFactory: { providers in
  try TokenManager(providers: providers, store: applicationStore, expirySkew: skew, now: clock)
}
```

```python
token_manager_factory=lambda providers: TokenManager(
    providers, store=application_store, expiry_skew=skew, now=clock,
)
```

Factories must construct managers without token acquisition or storage I/O. Authentication stays lazy,
including on public operations. The application owns storage security, session/environment identity and
manager lifecycle. Retain one client/aggregate for concurrent work: separate managers sharing a store do
not coordinate refreshes. Reopening a session may use a new client with the same store and stable identities.
Distinct sessions/environments need distinct grant/provider/profile/endpoint identities or isolated stores;
the API base URL alone does not create a storage namespace.

Kotlin exposes clock, skew and parent coroutine scope; close the manager when all sharing clients are done.
Swift accepts a Sendable store and clock in its Sendable factory; call `await manager.close()` at shutdown.
TypeScript supports `expirySkewMs` and a millisecond clock; Python supports `expiry_skew` and a seconds clock
on its asyncio loop. Those managers have no close method: cancel/await requests before discarding them.
Transport cleanup does not erase saved tokens. For logout, stop requests, let pending rotation commits
settle, remove the application's session entries and create fresh settings. `invalidate` retains refresh
state and is not a logout operation. Runtime README examples describe each native lifecycle.

The compiler-backed tests pin the compatible runtime releases below. These versions provide the manager
factory hook forwarded by generated clients:

| Target | Runtime version | Distribution |
| --- | --- | --- |
| Kotlin | `2.0.0-beta.14` | Maven Central |
| Swift | `2.0.0-beta.14` | Swift Package Manager Git tag |
| TypeScript | `2.0.0-beta.11` | npm (`@outfoxx/sunday`) |
| Python | `2.0.0-beta.10` | Git tag installed with pip; PyPI publication remains disabled |

Kotlin/Swift beta.13, TypeScript beta.10 and Python beta.9 do not provide this hook. Consumers generating
configuration factories must update their runtime dependencies to the compatible releases above or later.

## Credentials and alternatives

Server configurations contain no credentials. Generated `<Service>Credentials` groupings use the
narrowest runtime credential family for each scheme: bearer, API key, Basic, OAuth, or a custom provider.
Runtime OAuth credentials select client-credentials or authorization-code/PKCE acquisition and carry an
application-selected provider factory. That factory constructs the provider from the installed module,
without acquiring tokens. Existing token managers own refresh, caching, and cancellation behavior.

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
