from __future__ import annotations

from .models import Project
from litestar import Router, get
from litestar.exceptions import ValidationException
from litestar.params import FromPath
from litestar.response import Response
from pydantic import TypeAdapter, ValidationError
from sunday.litestar import ServerResponse as _SundayServerResponse
from typing import Protocol, cast

__all__ = ["ProjectsService", "create_projects_router"]


def _decode_request_parameter[T](adapter: TypeAdapter[T], value: object) -> T:
    try:
        return adapter.validate_python(value, context={"mode": "request"})
    except ValidationError as error:
        raise ValidationException(
            detail="Request parameter is invalid",
            extra=error.errors(include_input=False, include_context=False),
        ) from error


_get_project_project_id_adapter: TypeAdapter[str] = TypeAdapter(str)


class ProjectsService(Protocol):
    """Application implementation contract for the Projects service."""

    async def get_project(
        self,
        project_id: str,
    ) -> Project | _SundayServerResponse[Project, dict[str, object]]: ...


def create_projects_router(service: ProjectsService) -> Router:
    """Create a Litestar router for the Projects service.

    Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.
    """

    @get("/projects/{project_id:str}", status_code=200)
    async def get_project(
        project_id: FromPath[str],
    ) -> Project | Response[Project]:
        result = await service.get_project(
            _decode_request_parameter(_get_project_project_id_adapter, project_id),
        )
        if isinstance(result, _SundayServerResponse):
            return cast(
                Project | Response[Project],
                result.to_response(default_status=200, default_media_type="application/json"),
            )
        return result

    return Router(
        path="/",
        route_handlers=[
            get_project,
        ],
    )
