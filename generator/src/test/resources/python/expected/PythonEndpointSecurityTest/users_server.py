from __future__ import annotations

from litestar import Router, get, post
from litestar.connection import ASGIConnection
from litestar.exceptions import NotAuthorizedException, ValidationException
from litestar.handlers import BaseRouteHandler
from litestar.params import FromPath
from litestar.response import Response
from pydantic import TypeAdapter, ValidationError
from sunday.litestar import ServerResponse as _SundayServerResponse
from typing import Any, Protocol, cast

__all__ = ["UsersService", "create_users_router"]


def _decode_request_parameter[T](adapter: TypeAdapter[T], value: object) -> T:
    try:
        return adapter.validate_python(value, context={"mode": "request"})
    except ValidationError as error:
        raise ValidationException(
            detail="Request parameter is invalid",
            extra=error.errors(include_input=False, include_context=False),
        ) from error


_get_user_user_id_adapter: TypeAdapter[str] = TypeAdapter(str)


class UsersService(Protocol):
    """Application implementation contract for the Users service."""

    async def get_user(
        self,
        user_id: str,
    ) -> str | _SundayServerResponse[str, dict[str, object]]: ...

    async def create_user(self) -> str | _SundayServerResponse[str, dict[str, object]]: ...

    async def optional_user(self) -> str | _SundayServerResponse[str, dict[str, object]]: ...

    async def mixed_user(self) -> str | _SundayServerResponse[str, dict[str, object]]: ...


def _require_authenticated(connection: ASGIConnection[Any, Any, Any, Any], _: BaseRouteHandler) -> None:
    if connection.scope.get("user") is None:
        raise NotAuthorizedException()


def create_users_router(service: UsersService) -> Router:
    """Create a Litestar router for the Users service.

    Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.
    """

    @get("/users/{user_id:str}", status_code=200, guards=[_require_authenticated])
    async def get_user(
        user_id: FromPath[str],
    ) -> str | Response[str]:
        result = await service.get_user(
            _decode_request_parameter(_get_user_user_id_adapter, user_id),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                str | Response[str],
                result.to_response(default_status=200, default_media_type="text/plain"),
            )
        return result

    @post("/users", status_code=201, opt={"exclude_from_auth": True})
    async def create_user() -> str | Response[str]:
        result = await service.create_user()
        if isinstance(result, _SundayServerResponse):
            return cast(
                str | Response[str],
                result.to_response(default_status=201, default_media_type="text/plain"),
            )
        return result

    @get("/users/optional", status_code=200, opt={"exclude_from_auth": True})
    async def optional_user() -> str | Response[str]:
        result = await service.optional_user()
        if isinstance(result, _SundayServerResponse):
            return cast(
                str | Response[str],
                result.to_response(default_status=200, default_media_type="text/plain"),
            )
        return result

    @get("/users/mixed", status_code=200, opt={"exclude_from_auth": True})
    async def mixed_user() -> str | Response[str]:
        result = await service.mixed_user()
        if isinstance(result, _SundayServerResponse):
            return cast(
                str | Response[str],
                result.to_response(default_status=200, default_media_type="text/plain"),
            )
        return result

    return Router(
        path="/",
        route_handlers=[
            get_user,
            create_user,
            optional_user,
            mixed_user,
        ],
    )
