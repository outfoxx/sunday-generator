# Python IR Target Design

Date: 2026-05-24

## Purpose

Add Python as a first-class Sunday IR code generation target. The target must cover both generated clients and generated Litestar server stubs because Python is used for both API consumers and server implementations.

The Python target should enter at the current beta design level rather than reproducing older direct-execution client shapes. Generated clients should return typed operations for ordinary HTTP calls, while streaming calls should remain async producers.

## Goals

- Generate Python from `GeneratedApi` IR only.
- Target modern Python only, with Python 3.12 as the minimum supported version.
- Support RAML, OpenAPI 3.1, AsyncAPI, and composed OpenAPI + AsyncAPI sources through the existing source-to-IR pipeline.
- Use Pydantic v2 `BaseModel` as the initial model foundation for both clients and Litestar server stubs.
- Generate an `httpx` async client target.
- Generate Litestar server stubs.
- Preserve the generator testing invariant: generated Python must pass lint, format, type checking, import smoke, and runtime smoke checks before snapshots are written or compared.
- Support API/package naming from `sunday.name`, `x-sunday-apiId`, and CLI overrides.
- Support service grouping from `x-sunday-service` and `-services-from-tags`.
- Support aggregate clients and aggregate Litestar routers.

## Non-Goals

- Do not add a Pydantic dataclass mode in the first implementation. It can be added later as a `--model-style dataclass` option.
- Do not generate broker-specific AsyncAPI server consumers in the first pass. HTTP/event-stream behavior is in scope; non-HTTP broker channels should be skipped or deferred like other HTTP targets.
- Do not generate user-editable server implementation files. Generated files should remain disposable.
- Do not start with sync `httpx.Client` generation. The first target is async-first using `httpx.AsyncClient`.

## Package Layout

Use a grouped package layout. The root package is named from the API identity:

- `sunday.name`
- `x-sunday-apiId`
- CLI package/name override, when added
- deterministic fallback from API title/name

Package and module names are normalized to snake_case.

Example:

```text
turnpost_api/
  __init__.py
  py.typed
  models.py
  problems.py
  client.py
  server.py

  users/
    __init__.py
    models.py
    client.py
    server.py

  repos/
    __init__.py
    models.py
    client.py
    server.py
```

Root modules hold shared models and problems. Service packages hold service-owned operation-local request, response, and event models plus the service client and server router.

This layout minimizes circular imports at the start while still preserving service organization for larger APIs.

## Service Grouping

Service identity follows the same semantics as the existing IR-backed targets:

- Explicit `x-sunday-service` wins.
- With `-services-from-tags`, the first operation tag is used when no explicit service exists.
- Ungrouped operations go into a deterministic default service.
- Service package names are snake_case.
- Aggregate generation composes service clients and service routers without changing the underlying service identity.

## Python Code Emission

There is no existing PythonPoet library. Add a small internal Python emission layer instead of using free-form templates.

Core concepts:

- `PythonSymbol`: importable symbol identity with module, name, and optional alias.
- `PythonTypeRef`: renders type expressions and registers imports while rendering.
- `PythonImportSet`: deterministic import grouping, duplicate removal, and no unused imports by construction where possible.
- `PythonModuleBuilder`: owns module docstring, future imports, definitions, imports, `__all__`, and file path.
- `PythonCodeBlock`: a constrained placeholder renderer for bodies/decorators where a full structured builder would be too heavy.

Generated modules should use:

```python
from __future__ import annotations
```

Formatting and import correctness are enforced by Ruff. Type correctness is enforced by mypy.

## Models

Use Pydantic v2 `BaseModel` for the first implementation.

Mapping rules:

- Object models become `BaseModel` subclasses.
- Wire names use `Field(alias="...")`.
- Model config uses `ConfigDict(populate_by_name=True, extra=...)`.
- Enums use Python `Enum` or `StrEnum` depending on Python baseline and runtime compatibility.
- Nullable and optional fields map to `T | None` and default handling that matches the IR requiredness/nullability semantics.
- Date/time formats map to typed Python standard library values where practical, such as `datetime`, `date`, and `UUID`.
- Union models use Pydantic-compatible discriminated unions where IR discriminator metadata is available.
- External discriminator event envelopes should decode at the envelope level, using the sibling event type to select the payload type.
- Recursive models should be supported with postponed annotations and explicit rebuilds if needed.

Pydantic `TypeAdapter` can be used for non-model response types, union validation, and runtime operation decoding where a `BaseModel` class is not enough.

## Problems

Generated problem types should be throwable Python exceptions and serializable/validatable Pydantic models.

Initial strategy:

- Generate a base generated problem exception compatible with RFC problem fields.
- Generate concrete problem classes for `GeneratedProblem`.
- Preserve type URI, status, title/detail defaults, and custom fields.
- Support decoding registered problem responses in the HTTP client operation executor.
- Litestar server stubs should make it easy to raise generated problem exceptions and convert them to problem responses.

The exact exception/Pydantic inheritance shape should be proven in the first implementation slice because Python multiple inheritance between `Exception` and `BaseModel` may be awkward. If direct inheritance is not clean, use an exception wrapper that owns a Pydantic problem payload.

## HTTPX Client

Generate an async client target backed by `httpx.AsyncClient`.

Non-streaming methods return typed operations:

```python
op = api.users.get_current_user()
user = await op.execute()
request = op.to_request()
```

Operation shape:

- Generic over request body and response body.
- Owns method, path template, path/query/header parameters, request body, media selection, response adapter, problem adapters, and request factory/client reference.
- `execute()` sends the request and decodes success or problem responses.
- `to_request()` builds an `httpx.Request` without executing it.

SSE/event-stream methods should return async iterators directly because they have stream-specific behavior:

```python
async for event in api.events.stream_platform_events():
    ...
```

Aggregate client generation:

```python
api = TurnPostAPI(base_url="https://api.example.com")
op = api.users.get_current_user()
```

The aggregate client owns shared runtime configuration and service clients.

## Litestar Server Stubs

Generate Litestar route modules that delegate to user-supplied implementations rather than expecting users to edit generated files.

Example shape:

```python
class UsersService(Protocol):
    async def get_current_user(self) -> UserResponse: ...


def create_users_router(service: UsersService) -> Router:
    @get("/users/me")
    async def get_current_user() -> UserResponse:
        return await service.get_current_user()

    return Router(path="/", route_handlers=[get_current_user])
```

Aggregate server generation should expose a root router factory that includes service routers:

```python
router = create_turnpost_router(users_service=users, repos_service=repos)
```

Server stubs should support:

- Path/query/header parameters.
- Request bodies.
- Response models.
- Problem exception mapping.
- SSE/event-stream handlers where the IR represents HTTP event streams.
- Service grouping and aggregate router composition.

Non-HTTP AsyncAPI broker channels are deferred.

## CLI Shape

Add separate targets:

- `python/httpx`
- `python/litestar`

Shared options:

- `-out`
- package/API name override
- `-services-from-tags`
- `-aggregate`

The exact option names should follow existing CLI naming conventions. If the existing ecosystem uses `-aggregate-services`, Python should use the same option for consistency unless we intentionally rename across all targets.

## Testing And Verification

Python generated code must follow the same compile-backed snapshot invariant as other targets.

Required gate before snapshot write/compare:

1. Generate a temporary Python package.
2. Run `ruff format --check`.
3. Run `ruff check`.
4. Run `mypy`.
5. Run import smoke tests.
6. Run focused runtime smoke tests for Pydantic validation, problem decoding, operation execution shape, and SSE iterator shape.
7. Only then snapshot generated files.

Test helpers should mirror the existing generated-source compiler infrastructure and make it difficult to snapshot unverified Python files.

Initial fixtures:

- Simple object model.
- Enum model.
- Path/query/header parameters.
- Request body and response body.
- One problem response.
- One discriminated union.
- One external-discriminator event envelope.
- One composed OpenAPI + AsyncAPI HTTP event service.
- One grouped service fixture using explicit service identity.
- One grouped service fixture using `-services-from-tags`.
- One aggregate client/router fixture.

## Implementation Slices

1. Add Python emitter infrastructure and compile-backed test helper.
2. Generate Pydantic models for a narrow model fixture.
3. Generate problem payload/exception types for one problem fixture.
4. Generate `python/httpx` client vertical slice for one operation.
5. Generate `python/litestar` server stub vertical slice for the same operation.
6. Add service grouping and package naming.
7. Add aggregate client and aggregate router generation.
8. Add union/discriminator and external event envelope coverage.
9. Add composed OpenAPI + AsyncAPI smoke coverage.
10. Expand parity coverage across request/response media, parameters, validation, streaming, and problems.

## Open Questions

- Decide whether to use mypy strict mode immediately or a generated-code-specific strict profile that handles Pydantic ergonomics better.
- Decide the final generated problem exception/Pydantic relationship after a proof slice.
- Decide whether sync `httpx.Client` support is useful enough to add after async generation is stable.
