from __future__ import annotations

from .models import EventEnvelope
from collections.abc import AsyncIterable, AsyncIterator
from litestar import Router, get
from litestar.response import ServerSentEvent
from pydantic import BaseModel
from typing import Protocol

__all__ = ["EventsService", "create_events_router"]


class EventsService(Protocol):
    """Application implementation contract for the Events service."""

    def stream_events(self) -> AsyncIterator[EventEnvelope]: ...


def create_events_router(service: EventsService) -> Router:
    """Create a Litestar router for the Events service.

    Configure Litestar with PydanticPlugin(prefer_alias=True) so responses use source wire names.
    """

    @get("/events")
    async def stream_events() -> ServerSentEvent:
        return ServerSentEvent(_server_sent_events(service.stream_events()))

    return Router(
        path="/",
        route_handlers=[
            stream_events,
        ],
    )


async def _server_sent_events(events: AsyncIterable[BaseModel]) -> AsyncIterator[str]:
    async for event in events:
        yield event.model_dump_json(by_alias=True)
