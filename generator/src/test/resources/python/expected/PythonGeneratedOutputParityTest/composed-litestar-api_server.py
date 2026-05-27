from __future__ import annotations

from .events_server import EventsService, create_events_router
from .projects_server import ProjectsService, create_projects_router
from .users_server import UsersService, create_users_router
from litestar import Router

__all__ = ["create_parity_api_router"]


def create_parity_api_router(
    projects: ProjectsService,
    users: UsersService,
    events: EventsService,
) -> Router:
    """Create an aggregate Litestar router for all generated service routers.

    Configure Litestar with PydanticPlugin(prefer_alias=True) so responses use source wire names.
    """
    return Router(
        path="/",
        route_handlers=[
            create_projects_router(projects),
            create_users_router(users),
            create_events_router(events),
        ],
    )
