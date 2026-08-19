from __future__ import annotations

from .events import EventsClient
from .projects import ProjectsClient
from .users import UsersClient
from sunday import Transport

__all__ = ["ParityAPI"]


class ParityAPI[TransportRequestT, TransportResponseT]:
    """Aggregate client for all generated service clients."""

    def __init__(self, transport: Transport[TransportRequestT, TransportResponseT]) -> None:
        self._transport = transport
        self.projects = ProjectsClient(transport)
        self.users = UsersClient(transport)
        self.events = EventsClient(transport)
