from __future__ import annotations

from .models import Project
from litestar import Router, get
from litestar.params import FromPath
from typing import Protocol

__all__ = ["ProjectsService", "create_projects_router"]


class ProjectsService(Protocol):
    """Application implementation contract for the Projects service."""

    async def get_project(
        self,
        project_id: str,
    ) -> Project: ...


def create_projects_router(service: ProjectsService) -> Router:
    """Create a Litestar router for the Projects service.

    Configure Litestar with PydanticPlugin(prefer_alias=True) so responses use source wire names.
    """

    @get("/projects/{project_id:str}")
    async def get_project(
        project_id: FromPath[str],
    ) -> Project:
        return await service.get_project(project_id)

    return Router(
        path="/",
        route_handlers=[
            get_project,
        ],
    )
