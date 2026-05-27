from __future__ import annotations

from .models import ProjectView, UpdateProjectRequest
from litestar import Router, delete, get, put
from litestar.params import FromPath, HeaderParameter, QueryParameter
from typing import Annotated, Protocol

__all__ = ["ProjectsService", "create_projects_router"]


class ProjectsService(Protocol):
    """Application implementation contract for the Projects service."""

    async def get_project(
        self,
        project_id: str,
    ) -> ProjectView: ...

    async def update_project(
        self,
        project_id: str,
        body: UpdateProjectRequest,
        include_archived: bool | None = False,
        x_trace_id: str | None = None,
    ) -> ProjectView: ...

    async def delete_project_avatar(
        self,
        project_id: str,
    ) -> None: ...


def create_projects_router(service: ProjectsService) -> Router:
    """Create a Litestar router for the Projects service.

    Configure Litestar with PydanticPlugin(prefer_alias=True) so responses use source wire names.
    """

    @get("/projects/{project_id:str}")
    async def get_project(
        project_id: FromPath[str],
    ) -> ProjectView:
        return await service.get_project(project_id)

    @put("/projects/{project_id:str}")
    async def update_project(
        project_id: FromPath[str],
        data: UpdateProjectRequest,
        include_archived: Annotated[bool | None, QueryParameter(name="includeArchived")] = False,
        x_trace_id: Annotated[str | None, HeaderParameter(name="X-Trace-Id")] = None,
    ) -> ProjectView:
        return await service.update_project(project_id, data, include_archived, x_trace_id)

    @delete("/projects/{project_id:str}/avatar")
    async def delete_project_avatar(
        project_id: FromPath[str],
    ) -> None:
        await service.delete_project_avatar(project_id)

    return Router(
        path="/",
        route_handlers=[
            get_project,
            update_project,
            delete_project_avatar,
        ],
    )
