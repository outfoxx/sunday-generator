from __future__ import annotations

from .models import Configuration, ProjectQuery, ProjectView, UpdateProjectRequest
from litestar import Request, Router, delete, get, head, post, put, route
from litestar.exceptions import ValidationException
from litestar.params import Body, CookieParameter, FromPath, HeaderParameter, QueryParameter
from litestar.response import Response
from pydantic import TypeAdapter, ValidationError
from sunday.litestar import (
    ServerResponse as _SundayServerResponse,
    query_model as _sunday_query_model,
    request_model as _decode_body,
)
from typing import Annotated, Any, Protocol, Required, TypedDict, cast

__all__ = ["GetProjectResponseHeaders", "ProjectsService", "create_projects_router"]


GetProjectResponseHeaders = TypedDict("GetProjectResponseHeaders", {"X-Revision": Required[int]}, total=False)


def _decode_request_parameter[T](adapter: TypeAdapter[T], value: object) -> T:
    try:
        return adapter.validate_python(value, context={"mode": "request"})
    except ValidationError as error:
        raise ValidationException(
            detail="Request parameter is invalid",
            extra=error.errors(include_input=False, include_context=False),
        ) from error


_get_project_project_id_adapter: TypeAdapter[str] = TypeAdapter(str)


_get_project_session_id_adapter: TypeAdapter[str] = TypeAdapter(str)


_update_project_project_id_adapter: TypeAdapter[str] = TypeAdapter(str)


_update_project_include_archived_adapter: TypeAdapter[bool | None] = TypeAdapter(bool | None)


_update_project_include_archived_default = _update_project_include_archived_adapter.validate_python(False)


_update_project_x_trace_id_adapter: TypeAdapter[str] = TypeAdapter(str)


_head_project_project_id_adapter: TypeAdapter[str] = TypeAdapter(str)


_delete_project_avatar_project_id_adapter: TypeAdapter[str] = TypeAdapter(str)


class ProjectsService(Protocol):
    """Application implementation contract for the Projects service."""

    async def get_project(
        self,
        project_id: str,
        session_id: str,
    ) -> ProjectView | _SundayServerResponse[ProjectView, GetProjectResponseHeaders]: ...

    async def update_project(
        self,
        project_id: str,
        body: UpdateProjectRequest,
        x_trace_id: str,
        include_archived: bool | None = False,
    ) -> ProjectView | _SundayServerResponse[ProjectView, dict[str, object]]: ...

    async def create_project(
        self,
        body: UpdateProjectRequest,
    ) -> ProjectView | _SundayServerResponse[ProjectView, dict[str, object]]: ...

    async def list_projects(
        self,
        query_string: ProjectQuery,
    ) -> list[ProjectView] | _SundayServerResponse[list[ProjectView], dict[str, object]]: ...

    async def put_configuration(
        self,
        body: Configuration,
    ) -> Configuration | _SundayServerResponse[Configuration, dict[str, object]]: ...

    async def head_project(
        self,
        project_id: str,
    ) -> None | _SundayServerResponse[None, dict[str, object]]: ...

    async def options_projects(self) -> None | _SundayServerResponse[None, dict[str, object]]: ...

    async def delete_project_avatar(
        self,
        project_id: str,
    ) -> None | _SundayServerResponse[None, dict[str, object]]: ...


def create_projects_router(service: ProjectsService) -> Router:
    """Create a Litestar router for the Projects service.

    Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.
    """

    @get("/projects/{project_id:str}", status_code=200)
    async def get_project(
        project_id: FromPath[str],
        session_id: Annotated[str, CookieParameter(name="session-id")],
    ) -> ProjectView | Response[ProjectView]:
        result = await service.get_project(
            _decode_request_parameter(_get_project_project_id_adapter, project_id),
            _decode_request_parameter(_get_project_session_id_adapter, session_id),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                ProjectView | Response[ProjectView],
                result.to_response(default_status=200, default_media_type="application/json"),
            )
        return result

    @put("/projects/{project_id:str}", status_code=200)
    async def update_project(
        project_id: FromPath[str],
        data: Annotated[UpdateProjectRequest, Body(media_type="application/json")],
        x_trace_id: Annotated[str, HeaderParameter(name="X-Trace-Id")],
        include_archived: Annotated[bool | None, QueryParameter(name="includeArchived")] = False,
    ) -> ProjectView | Response[ProjectView]:
        result = await service.update_project(
            _decode_request_parameter(_update_project_project_id_adapter, project_id),
            data,
            _decode_request_parameter(_update_project_x_trace_id_adapter, x_trace_id),
            _decode_request_parameter(_update_project_include_archived_adapter, include_archived),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                ProjectView | Response[ProjectView],
                result.to_response(default_status=200, default_media_type="application/json"),
            )
        return result

    @post("/projects", status_code=202)
    async def create_project(
        data: Annotated[UpdateProjectRequest, Body(media_type="application/json")],
    ) -> ProjectView | Response[ProjectView]:
        result = await service.create_project(data)
        if isinstance(result, _SundayServerResponse):
            return cast(
                ProjectView | Response[ProjectView],
                result.to_response(default_status=202, default_media_type="application/json"),
            )
        return result

    @get("/projects", status_code=200)
    async def list_projects(
        request: Request[Any, Any, Any],
    ) -> list[ProjectView] | Response[list[ProjectView]]:
        result = await service.list_projects(_sunday_query_model(ProjectQuery, request))
        if isinstance(result, _SundayServerResponse):
            return cast(
                list[ProjectView] | Response[list[ProjectView]],
                result.to_response(default_status=200, default_media_type="application/json"),
            )
        return result

    @post("/configuration", status_code=200)
    async def put_configuration(
        request: Request[Any, Any, Any],
    ) -> Configuration | Response[Configuration]:
        result = await service.put_configuration(await _decode_body(Configuration, request, "application/yaml"))
        if isinstance(result, _SundayServerResponse):
            return cast(
                Configuration | Response[Configuration],
                result.to_response(default_status=200, default_media_type="application/yaml"),
            )
        return cast(
            Configuration | Response[Configuration],
            _SundayServerResponse(result, {}, media_type="application/yaml").to_response(default_status=200),
        )

    @head("/projects/{project_id:str}", status_code=200)
    async def head_project(
        project_id: FromPath[str],
    ) -> None:
        result = await service.head_project(
            _decode_request_parameter(_head_project_project_id_adapter, project_id),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                None,
                result.to_response(default_status=200, default_media_type="application/json"),
            )
        return None

    @route("/projects", http_method="OPTIONS", status_code=204)
    async def options_projects() -> None:
        result = await service.options_projects()
        if isinstance(result, _SundayServerResponse):
            return cast(
                None,
                result.to_response(default_status=204, default_media_type="application/json"),
            )
        return None

    @delete("/projects/{project_id:str}/avatar", status_code=204)
    async def delete_project_avatar(
        project_id: FromPath[str],
    ) -> None:
        result = await service.delete_project_avatar(
            _decode_request_parameter(_delete_project_avatar_project_id_adapter, project_id),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                None,
                result.to_response(default_status=204, default_media_type="application/json"),
            )
        return None

    return Router(
        path="/",
        route_handlers=[
            get_project,
            update_project,
            create_project,
            list_projects,
            put_configuration,
            head_project,
            options_projects,
            delete_project_avatar,
        ],
    )
