from __future__ import annotations

from .models import EventEnvelope
from collections.abc import AsyncIterator
from litestar import Router, get
from litestar.response import ServerSentEvent
from sunday.litestar import server_sent_events as _sunday_server_sent_events
from typing import Protocol

__all__ = ["EventsService", "create_events_router"]


class EventsService(Protocol):
    """Application implementation contract for the Events service."""

    def stream_project_events(self) -> AsyncIterator[EventEnvelope]: ...


def create_events_router(service: EventsService) -> Router:
    """Create a Litestar router for the Events service.

    Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.
    """

    @get("/events", status_code=200)
    async def stream_project_events() -> ServerSentEvent:
        return ServerSentEvent(_server_sent_events(service.stream_project_events()))

    return Router(
        path="/",
        route_handlers=[
            stream_project_events,
        ],
    )


_server_sent_events = _sunday_server_sent_events
