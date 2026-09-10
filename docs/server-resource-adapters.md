# Server resource adapters

Server generation can keep endpoint behavior in generated code while application code implements only operation behavior. The generated transport layer owns route registration, wire parameters, media types, response shapes, validation, and endpoint access policy.

## Kotlin/JAX-RS

Opt in with the CLI:

```sh
sunday kotlin/jaxrs -mode server -resource-adapters -quarkus -pkg example.api -out generated api.yaml
```

Omit `-quarkus` for standard JAX-RS; use `-use-jakarta-packages` for Jakarta packages instead of `javax` packages. In the Gradle plugin, set `framework = JAXRS`, `mode = Server`, and `resourceAdapters.set(true)` on a generation. Quarkus remains a separate option, `quarkus.set(true)`.

For each service, the generator emits:

- `UsersAPI`: an application delegate interface with the operation signatures and any nested operation-local model types. It has no REST, method validation, fault-tolerance, or endpoint security annotations.
- `UsersAPIResource`: a concrete resource that owns the REST and runtime annotations and forwards every operation argument to the delegate. It does not implement the delegate interface, so the delegate cannot be discovered as a duplicate JAX-RS resource and method validation constraints are not redeclared across an inheritance boundary.

Implement `UsersAPI` in application code. For Quarkus, make that implementation a CDI bean. The generated resource is a `@Singleton` with constructor injection. Resource classes and endpoint methods are final in Kotlin; Quarkus's default `quarkus.arc.transform-unproxyable-classes=true` enables CDI interceptors without an all-open compiler plugin. The application must retain that transformation setting when interception is needed.

For other JAX-RS implementations, register `UsersAPIResource(yourDelegate)` with the server's `Application`/resource configuration. Dependency injection and resource lifetime remain application choices.

With `-aggregate-services`, only the aggregate resource has a root `@Path`. It receives the individual service resources and returns them from generated subresource locators. In Quarkus these subresources remain CDI-managed, so security and other interceptors run on the actual endpoints. Delegates receive all operation path parameters, including those bound by an aggregate locator. Outside Quarkus, register the aggregate resource instance and supply its service resource instances.

Coroutine `suspend` methods, reactive return types, asynchronous `AsyncResponse`, SSE context parameters and streaming return shapes retain their existing generator semantics. `201 Created` operations receive `UriInfo`; delegates remain responsible for constructing the declared response and its `Location` header. Quarkus fault-tolerance and Zanzibar annotations remain on endpoint implementations alongside authentication.

Interface-only generation remains the default. Resource adapters are a server-only option. Existing clients and interfaces keep their current output.

## Python/Litestar

Litestar generation already provides an application service `Protocol` and a `create_users_router(service)` factory with concrete generated route handlers. Enable resolved endpoint authentication on those handlers with:

```sh
sunday python/litestar -enforce-endpoint-security -pkg example_api -out generated api.yaml
```

The programmatic option is `PythonGeneratorOptions(enforceEndpointSecurity = true)`. Aggregate routers preserve the per-operation guards. The service protocol and delegate signatures stay unchanged.

Protected routes receive a guard that rejects requests with no authenticated user in `connection.scope["user"]` with HTTP 401. The application configures authentication middleware to validate credentials and populate that user. Explicitly public routes set Litestar's standard `opt={"exclude_from_auth": True}`; the middleware must honor that option key. Higher-level guards remain cumulative and can still restrict these routes.

This option is disabled by default, preserving existing Litestar output.

## Authentication policy and application configuration

The shared IR resolution honors operation, service, then API precedence, including explicit empty overrides preserved by issue #206:

| Effective requirement | Quarkus resource | Standard JAX-RS resource | Litestar route |
| --- | --- | --- | --- |
| Non-empty security, with no anonymous alternative | `@Authenticated` | Check `SecurityContext.userPrincipal`, reject absent principals with 401 | Authentication guard |
| Explicit empty security or an anonymous alternative | `@PermitAll` | `@PermitAll`, no principal check | `exclude_from_auth` route option |
| No security declaration | No generated access restriction | No generated access restriction | No generated access restriction |

For RAML, `securedBy: [null]` and mixed alternatives such as `securedBy: [basic, null]` permit anonymous access. A method's declaration replaces resource and API defaults, so a protected method on a public resource remains protected. Resource security applies to that resource's own methods; nested resources use their own declarations or the API default. See [RAML security inheritance](https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md#applying-security-schemes).

These are generic authentication checks. Scheme names, OAuth scopes, and AND/OR combinations of named schemes do not become role checks or credential validators. Applications must configure their authentication mechanisms, accepted credentials, roles/scopes, and any scheme-specific enforcement. In particular, OpenAPI `http/bearer` or `bearerFormat: JWT` does not select Quarkus OIDC versus SmallRye JWT. Standard JAX-RS applications must populate `SecurityContext` after validating credentials and configure any authentication challenge headers.

Zanzibar authorization remains separate from authentication and still requires its runtime extension and application configuration. No Python or plain JAX-RS Zanzibar/fault-tolerance implementation is implied by these options.

HTTP path-level security can reject a request before the endpoint runs. Quarkus `@PermitAll` cannot override `quarkus.http.auth.permission.*` restrictions. Likewise, Litestar middleware and application/router guards can restrict public endpoints. Generated code does not change those application policies.

References: [Quarkus endpoint security and inheritance](https://quarkus.io/guides/security-authorize-web-endpoints-reference/#endpoint-security-annotations-and-jakarta-rest-inheritance), [Quarkus CDI transformations](https://quarkus.io/guides/cdi-reference/#unproxyable_classes_transformation), [Litestar guards](https://docs.litestar.dev/main/usage/security/guards.html), [Litestar authentication exclusions](https://docs.litestar.dev/main/usage/security/excluding-and-including-endpoints.html).

## Verification

Generator tests compile generated Kotlin and validate generated Python with formatting, lint, type, and import checks before inspecting or snapshotting source. Coverage includes RAML, OpenAPI, AsyncAPI, and composed inputs.

The runtime fixtures generate source from OpenAPI and RAML contracts during the build, compile it, and then exercise endpoint behavior:

```sh
./gradlew :integration-tests:jaxrs:check :integration-tests:quarkus:check
./gradlew :generator:test --tests '*PythonEndpointSecurityTest'
```

Jersey exercises the standard JAX-RS request pipeline. Quarkus runs HTTP requests against the CDI-managed aggregate and verifies public access, anonymous 401 responses, and authenticated delegation. Litestar tests verify middleware exclusions, protected access, and a fail-closed guard when no authentication middleware supplies a user.
