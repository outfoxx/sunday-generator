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

## Binding credential validators

The contract describes accepted credentials and permissions, but does not supply passwords, trusted keys, issuer/audience validation policy, TLS trust configuration, or a mapping from scheme names to application identity providers. Those remain application configuration. The generator does not infer OIDC versus SmallRye JWT from `bearerFormat: JWT`, fetch remote discovery metadata at request time, or treat a decoded token as authenticated.

Both runtimes require a validator for every referenced scheme at construction time. Missing bindings fail setup. Validators return a trusted identity and its granted scopes/roles, or null/None when credentials are invalid. Unexpected validator errors propagate and fail the request; they do not grant access. Use maintained authentication libraries or a trusted framework identity whose mechanism matches the named binding.

Kotlin generates `OpenAPISecurity` once per service package. Its nested `Authenticator` receives `Request`, `Scheme`, and the extracted credential. The `Request` provides JAX-RS headers, URI information, and security context. Validators return `Identity(principal, permissions)`. Construct `OpenAPISecurity(mapOf("userToken" to yourTokenAuthenticator, ...))` and pass it to each `UsersAPIResource(delegate, security)`. For Quarkus, provide the security instance as a CDI bean or producer. Aggregate resources continue to receive the managed service resources.

Kotlin validators are synchronous. On coroutine, reactive, or other event-loop endpoints they must use non-blocking local validation or an already validated framework identity. Applications needing remote introspection must arrange asynchronous authentication before endpoint execution, or configure worker-thread execution.

Python generates `_sunday_security.py` containing `ApiSecurity`, `Scheme`, `OAuthFlow`, `Identity`, and the `Authenticator` type. Validators are async callables receiving the Litestar connection, scheme metadata, and credential, and return `Identity(user, permissions=frozenset(...))` or None. Construct `ApiSecurity({"userToken": your_token_authenticator, ...})` and pass it by keyword:

```python
create_users_router(users_service, security=security)
create_api_router(users_service, health_service, security=security)
```

The generated guards perform authentication and permission checks before the delegate runs. They work without generic authentication middleware. Existing middleware can supply trusted identities to validators if configured for the declared schemes.

These evaluators authorize the operation; they do not replace Quarkus SecurityIdentity, JAX-RS SecurityContext, or Litestar request user/auth state. Applications that consume a current identity in delegates or authorization interceptors must establish that identity through their framework authentication integration and bind validators to it. With an AND requirement, correlating identities from different credential types is also an application validator responsibility.

## Framework policies

Quarkus methods with a generated security policy use `@PermitAll` so a generic authenticated-user interceptor does not block custom scheme validators. The generated method still performs the complete policy check before delegation. When application validators own credential processing, configure `quarkus.http.auth.proactive=false` so an unrelated built-in mechanism does not reject credentials before the generated evaluator runs. See [Quarkus proactive authentication](https://quarkus.io/guides/security-proactive-authentication/).

Public Litestar routes retain `opt={"exclude_from_auth": True}`. Protected routes use a [Litestar guard](https://docs.litestar.dev/2/usage/security/guards.html). If middleware is installed, configure it to accept the API's alternatives; its earlier rejection cannot be undone by a guard.

Global HTTP path restrictions, middleware, guards, and Zanzibar interceptors remain additional application policies. Authentication validators do not substitute for Zanzibar's framework identity integration. HTTPS alone does not prove mutual TLS: the application must validate the peer using trusted server TLS information, never an arbitrary client-supplied header.

## Verification

```sh
./gradlew :generator:test --tests '*GeneratedEndpointPolicyTest' \
  --tests '*KotlinSecuritySchemeTest' --tests '*PythonSecuritySchemeTest'
./gradlew :integration-tests:jaxrs:check :integration-tests:quarkus:check
```

Tests compile generated Kotlin and Python for RAML, OpenAPI, AsyncAPI, and composed input. Jersey, Quarkus, and Litestar request tests cover transports, wrong mechanisms, scope and role failures, AND/OR alternatives, public overrides, inherited requirements, repeated credentials, setup failures, and rejection before delegation. TLS tests verify rejection without an accepted peer; configuring and validating a real client-certificate handshake remains the application's TLS integration.
