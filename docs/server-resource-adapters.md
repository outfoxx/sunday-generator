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

Interface-only generation remains the default. Resource adapters are a server-only option and do not affect client generation.

### Explicit Content-Type parameters

An explicitly declared `Content-Type` header is included in server signatures, including interface-only generation and resource delegates. Standard JAX-RS uses `@HeaderParam`; Quarkus uses `@RestHeader`. The parameter keeps its declared type, requiredness, default and validation constraints. Singleton string constants receive a matching validation constraint when validation generation is enabled; configure Bean Validation in the server to enforce those constraints.

The request body's media declarations continue to determine `@Consumes`. A string header does not broaden a body declared as `application/octet-stream`. To accept several image formats, declare `image/*` on the body; to accept arbitrary media, declare `*/*`. Media matching is case-insensitive and accepts parameters such as `image/png; profile=example`, while the injected string preserves the actual header. Existing class-level media defaults still apply when there is no method-level override, including operations without a body.

Server enum models expose a static `fromString` converter using their declared wire values, with or without Jackson annotations. Strict enums accept exact values such as `image/png` and reject undeclared spellings, casing and parameters. Use a string header when those variations should reach application code, or a tolerant enum to retain unknown values. Enum conversion does not change JSON decoding or the allowed-value restrictions on inherited model properties.

Regenerate and update application overrides/delegates to accept the new argument. Client parameter and media behavior is unchanged. Python service protocols already included explicit headers.

OpenAPI specifies that explicit `Content-Type` header parameters are ignored. Sunday retains them as an existing extension for applications that need the header value; `requestBody.content` remains the portable declaration of accepted media. RAML header declarations and HTTP AsyncAPI message headers are retained as well. See the [OpenAPI parameter rules](https://spec.openapis.org/oas/v3.1.1.html#parameter-object), [Jakarta REST header conversion](https://jakarta.ee/specifications/restful-ws/4.0/apidocs/jakarta.ws.rs/jakarta/ws/rs/headerparam), and [Jersey validation configuration](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest31x/bean-validation.html).

## Python/Litestar

Litestar generation already provides an application service `Protocol` and a `create_users_router(service)` factory with concrete generated route handlers. Enable resolved endpoint authentication on those handlers with:

```sh
sunday python/litestar -enforce-endpoint-security -pkg example_api -out generated api.yaml
```

The programmatic option is `PythonGeneratorOptions(enforceEndpointSecurity = true)`. Aggregate routers preserve the per-operation guards. The service protocol and delegate signatures stay unchanged.

Protected routes receive a guard that rejects requests with no authenticated user in `connection.scope["user"]` with HTTP 401. The application configures authentication middleware to validate credentials and populate that user. Explicitly public routes set Litestar's standard `opt={"exclude_from_auth": True}`; the middleware must honor that option key. Higher-level guards remain cumulative and can still restrict these routes.

This option is disabled by default, preserving existing Litestar output.

### Binary request bodies

Whole-body `file`/`binary` schemas, including aliases, use `sunday.litestar.request_bytes` to read the request without JSON/base64 decoding. The helper checks all declared media ranges and rejects unsupported media with HTTP 415 before invoking the service. It uses the runtime's shared `MediaType` parser and matcher, preserves the original bytes, and retains Litestar's body-size limit. Missing `Content-Type` is treated as `application/octet-stream`; an explicitly required header is still independently required. Malformed media types produce HTTP 400. Explicit header arguments retain their declared string, enum or singleton-literal type.

JSON `format: byte` values and binary fields inside structured models continue through structured decoding. Multiple binary media representations are supported. An operation mixing binary and structured representations fails generation with a diagnostic naming the operation, rather than selecting the first representation.

This output requires the companion Sunday Python runtime change providing `request_bytes`; the `2.0.0-beta.1` runtime does not provide it. Before releasing this generator change, release the runtime helper, update the Python compiler's pinned runtime tag and minimum test dependency, and verify the published artifact. Local verification uses the matching checkout:

```sh
SUNDAY_PYTHON_PATH=/path/to/sunday-python ./gradlew :generator:test --tests '*PythonContentTypeTest'
```

## Authentication policy and application configuration

For complete named-scheme, scope/role, and AND/OR enforcement, enable `-enforce-security-schemes` as described in [OpenAPI security enforcement](openapi-security-enforcement.md). The table below describes the generic checks used without that option.

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
SUNDAY_PYTHON_PATH=/path/to/sunday-python ./gradlew :generator:test --tests '*ContentTypeTest'
```

Jersey exercises the standard JAX-RS request pipeline. Quarkus runs HTTP requests against the CDI-managed aggregate and verifies public access, anonymous 401 responses, and authenticated delegation. Litestar tests verify middleware exclusions, protected access, and a fail-closed guard when no authentication middleware supplies a user.

Content-Type fixtures additionally verify raw binary delegation, exact and wildcard media restrictions, strict and tolerant enum conversion, constants, defaults and missing headers. Compiler-backed AsyncAPI and composed cases reuse an enum for header binding and an `allOf` child restriction, preserving the behavior fixed by #217.
