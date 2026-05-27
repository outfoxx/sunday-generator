from __future__ import annotations

from .events import EventsClient
from .projects import ProjectsClient
from .runtime import Transport
from .users import UsersClient

__all__ = ["ParityAPI"]


class ParityAPI:
    """Aggregate client for all generated service clients."""

    def __init__(self, transport: Transport) -> None:
        self._transport = transport
        self.projects = ProjectsClient(transport)
        self.users = UsersClient(transport)
        self.events = EventsClient(transport)
