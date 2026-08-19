from __future__ import annotations

from .models import ProjectView, UpdateProjectRequest
from litestar import Router, delete, get, head, post, put, route
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
        x_trace_id: str,
        include_archived: bool | None = False,
    ) -> ProjectView: ...

    async def create_project(
        self,
        body: UpdateProjectRequest,
    ) -> ProjectView: ...

    async def head_project(
        self,
        project_id: str,
    ) -> None: ...

    async def options_projects(self) -> None: ...

    async def delete_project_avatar(
        self,
        project_id: str,
    ) -> None: ...


def create_projects_router(service: ProjectsService) -> Router:
    """Create a Litestar router for the Projects service.

    Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.
    """

    @get("/projects/{project_id:str}", status_code=200)
    async def get_project(
        project_id: FromPath[str],
    ) -> ProjectView:
        return await service.get_project(project_id)

    @put("/projects/{project_id:str}", status_code=200)
    async def update_project(
        project_id: FromPath[str],
        data: UpdateProjectRequest,
        x_trace_id: Annotated[str, HeaderParameter(name="X-Trace-Id")],
        include_archived: Annotated[bool | None, QueryParameter(name="includeArchived")] = False,
    ) -> ProjectView:
        return await service.update_project(project_id, data, x_trace_id, include_archived)

    @post("/projects", status_code=202)
    async def create_project(
        data: UpdateProjectRequest,
    ) -> ProjectView:
        return await service.create_project(data)

    @head("/projects/{project_id:str}", status_code=200)
    async def head_project(
        project_id: FromPath[str],
    ) -> None:
        await service.head_project(project_id)

    @route("/projects", http_method="OPTIONS", status_code=204)
    async def options_projects() -> None:
        await service.options_projects()

    @delete("/projects/{project_id:str}/avatar", status_code=204)
    async def delete_project_avatar(
        project_id: FromPath[str],
    ) -> None:
        await service.delete_project_avatar(project_id)

    return Router(
        path="/",
        route_handlers=[
            get_project,
            update_project,
            create_project,
            head_project,
            options_projects,
            delete_project_avatar,
        ],
    )
