from __future__ import annotations

from .models import EventEnvelope
from .runtime import EventStream, MediaType, RequestSpec, ServerSentEvent, Transport, as_transport
from pydantic import TypeAdapter

__all__ = ["EventsClient"]


class EventsClient:
    """Client operations for the Events service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = as_transport(transport)

    def stream_events(self) -> EventStream[EventEnvelope]:
        """Create the streamEvents operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/events",
            parameters=(),
            body=None,
            content_types=(),
            accept_types=(MediaType("application/json"),),
        )
        return self._transport.event_stream(request_spec, _decode_stream_events_event)


def _decode_stream_events_event(event: ServerSentEvent) -> EventEnvelope:
    if event.data is None:
        raise ValueError("Server-sent events must contain data")
    return TypeAdapter(EventEnvelope).validate_json(event.data)
