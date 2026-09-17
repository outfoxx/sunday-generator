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

The Quarkus HTTP suite runs against the default 3.31.2 and CI versions 3.34.5, 3.36.3, 3.38.3, and 3.39.3. Run a particular version locally with:

```sh
./gradlew --dependency-verification strict --no-configuration-cache \
  -PquarkusVersion=3.39.3 :integration-tests:quarkus:check
```

The selection regression uses generated class-level bearer annotations, a separate API-key strategy, and application-provided bindings. It checks valid/missing/invalid credentials, exactly one selected provider call, and no generated provider calls on unrelated fallback or Basic-authenticated routes.
