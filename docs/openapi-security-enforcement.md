# OpenAPI security enforcement

Enable scheme-aware enforcement when the generated server must enforce the OpenAPI security requirements before invoking application delegates:

```sh
sunday kotlin/jaxrs -mode server -resource-adapters -enforce-security-schemes -quarkus \
  -pkg example.api -out generated api.yaml
sunday python/litestar -enforce-security-schemes -pkg example_api -out generated api.yaml
```

Kotlin supports Quarkus, Jakarta JAX-RS, and javax JAX-RS. Omit `-quarkus` for standard JAX-RS; select Jakarta packages with `-use-jakarta-packages`. The Kotlin Gradle option is `enforceSecuritySchemes.set(true)` together with `resourceAdapters.set(true)` and server mode. The programmatic options are `KotlinJAXRSOptions.enforceSecuritySchemes` and `PythonGeneratorOptions.enforceSecuritySchemes`.

This option is disabled by default. When enabled, it supersedes the generic authenticated-user check from [server resource adapters](server-resource-adapters.md). Delegate operation signatures remain unchanged.

Kotlin's `-explicit-security-parameters` cannot be combined with this option: binding all scheme credentials as required delegate parameters would reject valid OR alternatives before policy evaluation. The evaluator extracts credentials from the request context instead.

## What the generated code enforces

Requirements follow [OpenAPI security semantics](https://spec.openapis.org/oas/v3.1.0#security-requirement-object):

- An omitted operation declaration inherits security. An explicit declaration replaces inherited requirements.
- An empty array, or any empty alternative object, permits anonymous access.
- Every scheme in one requirement object must authenticate successfully, with all permissions listed for that scheme.
- Any complete alternative in the requirement array may authorize the request.
- Permissions belong to the identity returned by that exact scheme's validator. A generic framework principal or permissions from a different scheme cannot satisfy them.
- OAuth2 and OpenID Connect lists are scopes. Other scheme types can carry role names in OpenAPI 3.1.
- Missing or invalid credentials produce HTTP 401. Complete authentication without the required permissions produces HTTP 403, unless another alternative succeeds.

For example:

```yaml
security:
  - userToken: [documents:read]
    tenantKey: []
  - administratorToken: [documents:admin]
```

The first alternative requires both a validated `userToken` granting `documents:read` and a validated `tenantKey`. The second accepts a validated `administratorToken` granting `documents:admin`. A validator runs at most once per named scheme per authorization call, even when alternatives repeat that scheme.

| Scheme | Generated credential extraction | Application validator responsibility |
| --- | --- | --- |
| HTTP Basic | Exactly one Authorization header using Basic; passes the encoded credential | Decode and verify the username/password through the application's credential store |
| HTTP bearer | Exactly one Authorization header using Bearer | Validate the token with the configured provider |
| Other HTTP schemes | Exactly one Authorization header with the declared scheme | Validate the scheme-specific credential and supply any custom challenges |
| API key | Exact declared header, query, or cookie name | Verify the key and resolve its identity and permissions |
| OAuth2 / OpenID Connect | Bearer token from Authorization | Validate the access token and return its trusted scopes |
| Mutual TLS | Requires a secure request and calls the named validator with no string credential | Verify the trusted peer certificate through the server's TLS integration |

Basic and bearer failures include WWW-Authenticate challenges. Insufficient bearer permissions include `error="insufficient_scope"`. Ambiguous repeated Authorization headers, API-key headers, and API-key query parameters are rejected.

The OpenAPI reader preserves required permissions, OAuth flow endpoint/scopes metadata, and the OpenID Connect discovery URL. Security scheme references resolve local and external JSON pointers while retaining the name used in the requirement. Invalid security arrays, permission lists, and API-key parameter metadata fail generation. The evaluator rejects undefined, unsupported, or conflicting scheme definitions. Existing RAML and AsyncAPI requirements also retain their scope lists in IR.

## AsyncAPI security

AsyncAPI 2.x uses named requirement maps (`security: [{token: [read]}]`). AsyncAPI 3.x uses inline Security Scheme Objects or local Reference Objects (`security: [{$ref: '#/components/securitySchemes/token'}]`); required OAuth/OIDC permissions come from the scheme's `scopes`, separately from the available scopes advertised by OAuth flows. Advertised scopes come from flow `scopes` in 2.x and `availableScopes` in 3.x; both populate the existing IR and binding `OAuthFlow.scopes` map without becoming required permissions.

Local references support chains and escaped JSON Pointer segments. The referenced component name remains the application binding key, including when that component aliases another scheme. External security references are unsupported and fail generation with a source URI and security location; missing targets, invalid targets, and cycles also fail explicitly.

Inline schemes receive `inline_<sha256>` binding names computed from their canonical authentication fields. Object field order and required scopes do not change the name. Equivalent inline schemes share a binding; explicitly named components remain separate. Use the generated `schemes` registry to discover inline names, or use component references when applications need human-readable binding keys. Name collisions fail generation.

For 3.x operations, applicable server policies are resolved during generation. An omitted or empty channel `servers` list selects all declared servers; a non-empty list selects only its referenced servers. Alternatives within a server or operation remain ordered OR choices; the applicable server requirements and operation requirements are combined with AND. Repeated schemes share authentication while their required permissions are combined. No request-time server or path lookup is added.

Both versions preserve `httpApiKey` transport metadata: `name` is the exact wire name and `in` must be `header`, `query`, or `cookie`. This differs from AsyncAPI's non-HTTP `apiKey` type, whose user/password transports are not supported by HTTP server enforcement.

## Binding credential validators

The contract describes accepted credentials and permissions, but does not supply passwords, trusted keys, issuer/audience validation policy, TLS trust configuration, or a mapping from scheme names to application identity providers. Those remain application configuration. The generator does not infer OIDC versus SmallRye JWT from `bearerFormat: JWT`, fetch remote discovery metadata at request time, or treat a decoded token as authenticated.

All runtimes require a validator for every referenced scheme at construction time. Missing bindings fail setup. Validators return a trusted identity and its granted scopes/roles, or null/None when credentials are invalid. Unexpected validator errors propagate and fail the request; they do not grant access. Use maintained authentication libraries or a trusted framework identity whose mechanism matches the named binding.

Plain JAX-RS generates `OpenAPISecurity` once per service package. Its nested `Authenticator` receives `Request`, `Scheme`, and the extracted credential. The `Request` provides JAX-RS headers, URI information, and security context. Validators return `Identity(principal, permissions)`. Construct `OpenAPISecurity(mapOf("userToken" to yourTokenAuthenticator, ...))` and pass it to each `UsersAPIResource(delegate, security)`. Aggregate resources continue to receive the managed service resources.

Plain JAX-RS validators are synchronous. On coroutine, reactive, or other event-loop endpoints they must use non-blocking local validation or an already validated framework identity. Applications needing remote introspection must arrange asynchronous authentication before endpoint execution, or configure worker-thread execution.

Python generates `_sunday_security.py` containing `ApiSecurity`, `Scheme`, `OAuthFlow`, `Identity`, and the `Authenticator` type. Validators are async callables receiving the Litestar connection, scheme metadata, and credential, and return `Identity(user, permissions=frozenset(...))` or None. Construct `ApiSecurity({"userToken": your_token_authenticator, ...})` and pass it by keyword:

```python
create_users_router(users_service, security=security)
create_api_router(users_service, health_service, security=security)
```

The generated guards perform authentication and permission checks before the delegate runs. They work without generic authentication middleware. Existing middleware can supply trusted identities to validators if configured for the declared schemes.

The plain JAX-RS and Litestar evaluators authorize the operation; they do not replace JAX-RS SecurityContext or Litestar request user/auth state. Applications that consume a current identity in delegates or authorization interceptors must establish that identity through their framework authentication integration and bind validators to it. With an AND requirement, correlating identities from different credential types is also an application validator responsibility.

## Native Quarkus bindings

Quarkus generates a native `OpenAPISecurity` binding API for protected operations. Its `Authenticator.authenticate(Request, Scheme, credential)` returns `Uni<SecurityIdentity?>`. The request exposes `context: RoutingContext` and `identityProviderManager: IdentityProviderManager`. Bindings can call configured Quarkus identity providers, including JWT/OIDC providers, or asynchronously validate application-owned credentials. Return null for invalid credentials; Quarkus `AuthenticationFailedException` is also treated as a rejected credential so another OpenAPI alternative can succeed. Unexpected provider failures propagate when evaluation reaches an alternative requiring that scheme. Composite authentication retains failures as request-local evidence, so a later unused alternative cannot override an earlier authorization success. If no scheme authenticates, any unexpected provider failure still fails the request.

Provide one `OpenAPISecurity` CDI bean or producer per generated package. Each `SchemeBinding` pairs an authenticator with a permission reader. Permission readers are required only for schemes whose protected operations request scopes or roles. They read trusted permissions from the identity returned by that exact authenticator; OAuth scopes are never inferred from Quarkus roles.

For example, given application-provided `userTokenAuthenticator`, `tenantKeyAuthenticator`, and `trustedScopes`:

```kotlin
@Produces
@Singleton
fun apiSecurity() = OpenAPISecurity(
  bindings = mapOf(
    "userToken" to OpenAPISecurity.SchemeBinding(userTokenAuthenticator, permissions = ::trustedScopes),
    "tenantKey" to OpenAPISecurity.SchemeBinding(tenantKeyAuthenticator),
  ),
  subjectSchemes = mapOf(setOf("userToken", "tenantKey") to "userToken"),
)
```

A single-scheme requirement automatically selects its identity. Every multi-scheme requirement group needs an explicit `subjectSchemes` entry naming the scheme whose identity becomes the request user. `OpenAPISecurity.subjectRequirements` exposes the canonical sets of scheme names; equivalent groups share one binding regardless of operation or scope list. Missing authenticators, required permission readers, missing/extra subject groups, and subject names outside their group fail initialization. Generated authentication beans initialize at startup.

The selected provider identity retains its principal, credentials, attributes, roles, and permission behavior. Quarkus publishes it before Zanzibar's request filter, so the generated JWT user extractor reads the validated framework JWT. Zanzibar annotations remain on concrete resource endpoints. Public OpenAPI overrides do not add `@FGAIgnore`; a public operation may still be restricted by Zanzibar and use the application's ordinary framework authentication.

### Generated specialization

The generator resolves effective policies before emitting code and uses canonical constants for endpoint bindings. Uniform API-level requirements and equivalent explicit operation requirements share the same implementation:

- A single scheme without required permissions uses one native mechanism and Quarkus's authenticated gate. No custom policy evaluator is generated.
- Composite requirements or required permissions use a fixed named HTTP security policy. Equivalent policies reuse the same bean; different scopes can reuse one authentication strategy.
- Uniform resource classes receive shared security annotations. Mixed resources bind only the necessary strategy/policy to each endpoint. Aggregate subresource locators do not receive another generated check.
- Public-only/unspecified APIs generate no security runtime. No generated strategy performs path matching or an operation lookup.

Credentials are validated once per needed scheme per request. Policies reuse those identities and read each needed permission set at most once. Scheme evidence is stored only for multi-scheme strategies. Method bodies only invoke application delegates; native authentication and authorization happen earlier.

## Framework policies

Set `quarkus.http.auth.proactive=false`: Quarkus selects generated authentication mechanisms through resource annotations after matching the request. The generated mechanisms decline requests for which Quarkus has not selected them, preserving unrelated application routes. This scopes uniform policies to the generated API rather than creating a global HTTP restriction. Where HTTP path policies overlap these endpoints, use `quarkus.http.auth.permission.<name>.applies-to=jaxrs` and avoid selecting a competing authentication mechanism before endpoint selection. See [Quarkus authentication selection](https://quarkus.io/guides/security-authentication-mechanisms/) and [HTTP security policies](https://quarkus.io/guides/security-authorize-web-endpoints-reference/).

Generated mechanisms recognize Quarkus's selected mechanism instance on 3.31.2 and its selected-instance list on 3.34.5, 3.36.3, 3.38.3, and 3.39.3. This compatibility check reads framework selection evidence without writing it, authenticating during credential-transport discovery, or matching request paths. Quarkus does not expose this distinction through the `HttpAuthenticationMechanism` interface, so the integration suite exercises both internal layouts. Existing beta.30 generated sources must be regenerated to receive this fix.

Simple authenticated endpoints use Quarkus's normal authenticated gate. Endpoints with a generated named policy use that policy as their sole OpenAPI authorization gate; it performs the full decision before request filters execute. Quarkus 3.31 treats `@AuthorizationPolicy` as a security annotation, so these methods do not also receive `@Authenticated` or `@PermitAll`. This does not bypass Zanzibar.

Public Litestar routes retain `opt={"exclude_from_auth": True}`. Protected routes use a [Litestar guard](https://docs.litestar.dev/2/usage/security/guards.html). If middleware is installed, configure it to accept the API's alternatives; its earlier rejection cannot be undone by a guard.

Global HTTP path restrictions, middleware, guards, and Zanzibar interceptors remain additional application policies. HTTPS alone does not prove mutual TLS: the application must validate the peer using trusted server TLS information, never an arbitrary client-supplied header.

## Verification

```sh
./gradlew :generator:test --tests '*GeneratedEndpointPolicyTest' \
  --tests '*KotlinSecuritySchemeTest' --tests '*PythonSecuritySchemeTest'
./gradlew :integration-tests:jaxrs:check :integration-tests:quarkus:check
```

Tests compile generated Kotlin and Python for RAML, OpenAPI, AsyncAPI, and composed input. Jersey, Quarkus, and Litestar request tests cover transports, wrong mechanisms, scope and role failures, AND/OR alternatives, public overrides, inherited requirements, repeated credentials, setup failures, and rejection before delegation. TLS tests verify rejection without an accepted peer; configuring and validating a real client-certificate handshake remains the application's TLS integration.

Quarkus tests additionally use the real Zanzibar extension, a deterministic relationship backend, and signed JWTs validated by the native provider. They cover identity propagation, explicit subject selection, FGA denial, concurrent-request isolation, validation/permission-reader counts, and native authorization event counts. They verify that proactive authentication fails startup and conflicting early HTTP authentication fails before Zanzibar or delegation. Compile-backed specialization tests verify that uniform policies do not generate per-operation dispatch or redundant evaluators.

The Quarkus HTTP suite runs against the default 3.39.4 and CI versions 3.34.5, 3.36.3, 3.38.3, and 3.39.3. Run a particular version locally with:

```sh
./gradlew --dependency-verification strict --no-configuration-cache \
  -PquarkusVersion=3.39.3 :integration-tests:quarkus:check
```

The selection regression uses generated class-level bearer annotations, a separate API-key strategy, and application-provided bindings. It checks valid/missing/invalid credentials, exactly one selected provider call, and no generated provider calls on unrelated fallback or Basic-authenticated routes.

## Explicit Quarkus integration

Quarkus 3.39.4 or later can use an explicit `quarkus` binding under the existing
client/server/profile metadata. Omitting it preserves the generated-binding path
and its existing client recovery behavior. Generic JAX-RS and Sunday SDK bindings
continue to use their application providers.

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      x-sunday-security:
        server:
          provider: identity
          quarkus: {mode: oidc}
        client:
          provider: service
          flow: clientCredentials
          quarkus: {mode: acquire}
```

### Native server

`oidc` selects native bearer authentication; `webApp` explicitly selects a host
application's browser login. Neither is inferred from `bearerFormat`, discovery
metadata, or an advertised authorization-code flow. Add `quarkus-oidc` and retain
the generated `META-INF` resources in the contract jar. Generated defaults have
configuration ordinal 100, below application configuration, and do not write
`application.properties`.

Declare trusted `token.issuer`, `token.audience`, and `auth-server-url` (or `public-key`)
in the selected server binding's `quarkus.properties`, or supply genuinely deployment-specific
values in application configuration. An issuer of `any`, a disabled tenant, or a mismatched
application type fails startup. Server trust is never inferred from client acquisition URLs.
Client IDs and secret-bearing values in contract metadata must use runtime property expressions.

An optional `tenant` selects a named OIDC tenant, not a business tenant or FGA
object. Tenant selection emits `@Tenant` and requires
`quarkus.http.auth.proactive=false`; the generated default is checked at startup.
A default-tenant bearer binding leaves proactive authentication configurable.
With proactive authentication enabled, invalid supplied credentials can reject
public requests; `@PermitAll` still allows credential-free public requests.

A native policy checks the authenticated OIDC credential, the selected tenant,
and only the scopes explicitly required by the operation. It uses Quarkus
`SecurityIdentity.checkPermission(StringPermission(scope))`; it does not reinterpret
roles or Zanzibar relations as scopes. Quarkus does not allow combining
`@AuthorizationPolicy` with `@PermissionsAllowed` on one endpoint, so these checks
share one policy. Existing Zanzibar annotations remain on the generated resource.
Native mode currently requires one bearer scheme and one alternative per
operation. Composite policies fail generation and can use shared providers.

### Native clients

Add `quarkus-rest-client-oidc-filter` for `acquire`, or
`quarkus-rest-client-oidc-token-propagation` for `propagate` and `exchange`.
Generated startup checks diagnose missing native filter classes and required
client settings. Native annotations are method-specific; public operations do
not receive a credential annotation.

- `acquire` requires `flow: clientCredentials` and emits `@OidcClientFilter`.
  Configure the named provider under `quarkus.oidc-client.<provider>`, including
  its client ID and credentials. Contract token/discovery URLs provide endpoint
  defaults; deployment configuration can override them. Startup checks validate
  these effective settings on each generated client. Generated clients isolate
  different contracts, scopes, and acquisition settings.
- `propagate` requires `flow: external` and emits `@AccessToken`. It forwards the
  current authenticated access token. It does not acquire a service-account token
  when no user token exists. Audience/resource changes require exchange.
- `exchange` requires `flow: external` and emits
  `@AccessToken(exchangeTokenClient = ...)`. Its generated grant is `exchange`; an
  explicitly configured provider grant must agree. Exchange clients retain separate scope/audience settings.

Native filters own expiry checks, pre-expiry skew, token acquisition, and refresh.
Declare `refresh-token-time-skew` in the binding properties, or explicitly alias it from the provider, to leave a transit margin.
Native `refresh-on-unauthorized` renews credentials on the next invocation of the affected client method;
it does not replay the rejected HTTP request. Native mode introduces no automatic
same-invocation retry. Existing Sunday recovery remains available by omitting the
native binding. Explicit fault-tolerance policies remain application choices.

### Application metadata output

Explicit native Quarkus bindings emit ordinary properties resources, with no generated
`ConfigSource`, `ConfigSourceFactory`, or configuration service registration. Each client
or server-stub artifact defaults to `META-INF/microprofile-config.properties` and includes
`config_ordinal=100`. The generator never writes `application.properties` or `beans.xml`.
Non-native client integration retains its existing configuration factory.

| CLI option | Gradle generation property | Default |
| --- | --- | --- |
| `-application-metadata` / `-no-application-metadata` | `generateApplicationMetadata` | `true` |
| `-server-configuration` / `-no-server-configuration` | `generateServerConfiguration` | `true` |
| `-client-configuration` / `-no-client-configuration` | `generateClientConfiguration` | `true` |
| `-server-configuration-file` | `serverConfigurationFileName` | `META-INF/microprofile-config.properties` |
| `-client-configuration-file` | `clientConfigurationFileName` | `META-INF/microprofile-config.properties` |

The master switch overrides the individual switches. Disabling metadata retains all
security annotations, policy enforcement, and startup requirements. The application must
then supply equivalent configuration. Paths must be relative `.properties` paths; absolute
paths, traversal, symlink escapes, and `application.properties` are rejected.

Custom paths such as `config/client.properties` are included by the application:

```properties
quarkus.config.locations=config/client.properties,config/server.properties
```

The property is `quarkus.config.locations`, not `quarkus.config.sources`. The application
owns one effective inclusion list; lists from dependencies do not concatenate. Explicit
`config_ordinal=100` prevents custom defaults inheriting the higher ordinal of the source
that declares their location. Application properties (250), environment variables (300),
and system properties (400) override library defaults for runtime-configurable settings.
Build-time settings must be supplied before augmentation and require a rebuild to change.
See [Quarkus configuration](https://quarkus.io/guides/config-reference/).

Matching paths in separate dependency JARs are valid. Within one generated artifact,
disjoint properties merge, equal values deduplicate, and conflicting values fail with
source diagnostics. Use separate generation directories; independent CLI invocations
refuse to overwrite an existing properties resource. Different filenames do not resolve
conflicting property keys across dependency JARs: align host-wide policy or supply an
intentional application override at the appropriate build/runtime phase.

### Scoped native policy

OpenAPI and AsyncAPI accept root `x-sunday-quarkus-config`; RAML uses
`(sunday.quarkus-config)`. All frontends and durable IR retain the existing `all`, `client`,
`server`, and `profiles` layering. More-local layers override inherited property keys;
conflicting peer declarations in composed inputs fail.

```yaml
x-sunday-quarkus-config:
  server:
    properties:
      quarkus.zanzibar.filter.deny-unannotated-resource-methods: true
      quarkus.zanzibar.filter.unauthenticated-user: anonymous
  profiles:
    internal:
      client:
        server: 0
        properties:
          quarkus.rest-client-oidc-filter.refresh-on-unauthorized: true
          quarkus.rest-client."accounts".read-timeout: 10000
```

`server` is a client-only declared server name or zero-based index. Without it, generation
uses the first applicable server. Selection/profile conflicts fail. The selected server
supplies the REST-client URL under its existing config key (or interface name), and its
security profile when no explicit profile is selected. Server-variable defaults expand;
unresolved variables use `${sunday.server.<service>.<variable>}` runtime inputs. URL paths
belong to the configured base URL rather than being duplicated in the interface path.

OIDC settings belong to the selected security binding, using native suffixes:

```yaml
x-sunday-security:
  profiles:
    internal:
      client:
        provider: service
        flow: clientCredentials
        tokenUrl: https://identity.example/token
        quarkus:
          mode: acquire
          properties:
            refresh-token-time-skew: 30S
          providerProperties: [tls.tls-configuration-name]
      server:
        provider: identity
        quarkus:
          mode: oidc
          properties:
            auth-server-url: ${identity.url}
            token.issuer: https://identity.example
            token.audience: accounts
            token.principal-claim: sub
```

Native configuration rejects unsupported keys, server-only policy in client output,
incompatible grants/scopes, and literal caller IDs/secrets. Supported areas include OIDC
trust and client acquisition, REST-client URL/timeouts, native renewal after unauthorized
responses, and Zanzibar filter policy. Database and business-service configuration is not
inferred or accepted as API policy.

### Provider aliases and runtime inputs

Native clients retain separate deterministic OIDC client IDs for contract, profile, scheme,
scopes, and acquisition settings. They use property expressions to reference named providers;
they no longer copy arbitrary provider subtrees at runtime. Basic application inputs are:

```properties
quarkus.oidc-client.service.client-id=${CALLER_CLIENT_ID}
quarkus.oidc-client.service.credentials.secret=${CALLER_CLIENT_SECRET}
```

Built-in aliases cover `client-id`, `credentials.secret`, `auth-server-url`,
`discovery-enabled`, `discovery-path`, and `token-path`. Endpoints fall back to contract
values. Optional secret/URL strings can be absent; required client identity cannot.
Quoted and unquoted provider spellings are supported (quoted wins if both are configured).
A direct higher-priority override of the generated isolated-client property wins.

Declare additional suffixes in `providerProperties`, including supported JWT credentials,
credential-provider, TLS, proxy, custom token headers (`headers.<name>`), and grant options
(`grant-options.<grant>.<name>`). These aliases are required unless a contract value supplies
a fallback. Do not use aliases to replace isolated-client IDs, scopes, grants, or acquisition
timing. Startup fails for unforwarded provider settings with a diagnostic naming the setting.
For dynamic named configuration, keep the provider/property declaration in application
properties and reference an environment variable there; this avoids ambiguous environment
normalization of punctuation in names. For example, a contract using provider
`accounts.worker` and alias `tls.tls-configuration-name` can use:

```properties
quarkus.oidc-client."accounts.worker".client-id=${ACCOUNTS_WORKER_CLIENT_ID}
quarkus.oidc-client."accounts.worker".credentials.secret=${ACCOUNTS_WORKER_CLIENT_SECRET}
quarkus.oidc-client."accounts.worker".tls.tls-configuration-name=${CALLER_TLS_CONFIGURATION}
```

No secret value is embedded in a generated artifact. Generated isolated-client keys use
unquoted `sunday-<digest>` names consistently, including the grant key read by native
exchange filters. These names are derived from contract identity, profile, scheme and
acquisition settings; configure reusable caller inputs under provider names instead of
hard-coding a generated digest.

### Configuration ownership and migration

| Configuration | Owner |
| --- | --- |
| Base URL, token/discovery endpoint, grant, scopes, declared audience/resource | Selected contract |
| Refresh skew, optional next-invocation renewal, REST timeouts | Explicit contract policy, otherwise framework defaults |
| OIDC application type, tenant-selection timing, browser PKCE/nonce/access-token checks | Native binding defaults and retained checks |
| Issuer/audience/principal claim | Explicit server policy or documented deployment input |
| Caller ID, secret, test/deployment addresses | Runtime inputs |
| Zanzibar enabled, deny-unannotated, timeout | Explicit policy, otherwise Zanzibar 2.15.0 defaults: true, true, 5S |
| Zanzibar unauthenticated identity | Explicit contract policy; no assumed anonymous identity |
| Test ports, test keys, Dev Services | Test infrastructure |

Zanzibar 2.15.0 is the tested baseline. All four filter settings are build-time, host-wide
settings. Generated server resources must be packaged before augmentation. Their effects
include unrelated resource methods in the same application. Explicit FGA annotations and
`ignore` operations remain generated from `x-sunday-zanzibar`; `security: []` does not
implicitly disable Zanzibar. Client artifacts contain no server filter policy.

Native acquisition remains lazy (`early-tokens-acquisition=false`) unless explicitly
configured in binding policy. Web login retains PKCE, nonce, access-token verification,
and access-token roles. There are no new retries or authorization semantics.

When upgrading from beta.40, change `.kt` filename overrides to `.properties` paths.
Gradle replaces its owned output and removes obsolete sources/descriptors, renamed files,
and disabled metadata. Rebuild the artifact to remove stale compiled classes. For unmanaged
CLI output, remove only previously generated output or regenerate into a fresh directory;
never delete application-authored resources. Replace reliance on wildcard provider copying
with explicit aliases. Generic/shared-provider bindings and Sunday SDK configuration retain
their existing behavior.

Minimal inputs by native mode:

- `oidc`: supply only trust values/keys not explicitly declared in the server binding.
- `webApp`: additionally supply the host application's client identity and credentials.
- `acquire`: supply caller credentials under the named provider and any unresolved endpoint inputs.
- `propagate`: supply an authenticated incoming identity; no acquisition credentials are generated.
- `exchange`: supply the exchange provider's runtime credentials and unresolved endpoint inputs.

For example, if issuer/audience/endpoints are already declared by the contract, the
remaining application inputs for a bearer server using an offline key and a client using
provider `service` are:

```properties
quarkus.oidc.public-key=${TRUSTED_PUBLIC_KEY}
quarkus.oidc-client.service.client-id=${CALLER_CLIENT_ID}
quarkus.oidc-client.service.credentials.secret=${CALLER_CLIENT_SECRET}
```

For `webApp`, supply `quarkus.oidc.client-id=${LOGIN_CLIENT_ID}` and
`quarkus.oidc.credentials.secret=${LOGIN_CLIENT_SECRET}` instead of an offline key, and
supply `auth-server-url`, issuer, and audience only when the server contract leaves them
unresolved. Prefix these keys with the selected tenant when the contract names one.
`exchange` uses the same provider inputs as `acquire`, under its selected provider name;
`propagate` needs no OIDC-client credential properties. Neither mode introduces request
replay after a rejected request.

### Shared provider SPI

`server.quarkus.mode: provider` emits an overridable `@DefaultBean` producer for
the package-specific `OpenAPISecurity`. The companion Kotlin runtime provides
`ServerSecurityProvider`, including its request, scheme, authenticator, and binding
contracts. A shared library implements this interface once, exposes a CDI bean,
and, when packaged as a dependency jar, supplies a Jandex index or a zero-byte
`META-INF/beans.xml` marker. It must not depend on generated service packages.
Contract library builds must likewise supply an index or marker; the generator
does not emit `beans.xml`. Sources compiled directly into the application need
neither because Quarkus indexes application classes automatically.

The generated producer resolves exactly one provider for each declared provider
name. Missing or duplicate names fail startup. The provider's `binding()` resolves
its required deployment configuration; its authenticator validates the supplied
credential and returns a trusted native identity. It must never accept an
unrelated existing identity as evidence for a scheme. A permissions reader is
required whenever the contract declares permissions. Composite requirements use
one shared `ServerSecuritySubjectSelector` to choose a member of each AND group.
Existing scheme evidence, alternative evaluation, and Zanzibar ordering are
retained. An explicit application `OpenAPISecurity` producer overrides the
generated default without creating ambiguous beans.

The shared SPI requires Sunday Kotlin `2.0.0-beta.15` or later, which includes
[sunday-kt #67](https://github.com/outfoxx/sunday-kt/pull/67). Published artifacts
include the provider contracts; a local runtime checkout is only needed when
developing runtime changes.
