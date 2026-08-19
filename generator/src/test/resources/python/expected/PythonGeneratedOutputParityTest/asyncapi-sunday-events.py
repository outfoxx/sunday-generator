from __future__ import annotations

from .models import EventEnvelope
from collections.abc import Sequence
from pydantic import TypeAdapter
from sunday import EventStream, MediaType, RequestSpec, ServerSentEvent, Transport

__all__ = ["EventsClient"]


_named_event_envelope_adapter: TypeAdapter[EventEnvelope] = TypeAdapter(EventEnvelope)


def _decode_stream_events_event(event: ServerSentEvent) -> EventEnvelope:
    if event.data is None:
        raise ValueError("Server-sent events must contain data")
    return _named_event_envelope_adapter.validate_json(event.data)


class EventsClient[TransportRequestT, TransportResponseT]:
    """Client operations for the Events service."""

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

    def stream_events(self) -> EventStream[EventEnvelope]:
        """Create the streamEvents operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/events",
            accept_types=(MediaType("application/json"),),
        )
        return self.transport.event_stream(request_spec, _decode_stream_events_event)
