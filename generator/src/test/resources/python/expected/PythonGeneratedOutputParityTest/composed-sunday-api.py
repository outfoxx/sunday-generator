from __future__ import annotations

from .events import EventsClient
from .projects import ProjectsClient
from .users import UsersClient
from collections.abc import Sequence
from sunday import MediaType, Transport

__all__ = ["ParityAPI"]


class ParityAPI[TransportRequestT, TransportResponseT]:
    """Aggregate client for all generated service clients."""

    def __init__(
        self,
        transport: Transport[TransportRequestT, TransportResponseT],
        *,
        default_content_types: Sequence[MediaType] = (),
        default_accept_types: Sequence[MediaType] = (),
    ) -> None:
        self.transport = transport
        self.default_content_types = tuple(default_content_types)
        self.default_accept_types = tuple(default_accept_types)
        self.projects = ProjectsClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
        )
        self.users = UsersClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
        )
        self.events = EventsClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
        )
