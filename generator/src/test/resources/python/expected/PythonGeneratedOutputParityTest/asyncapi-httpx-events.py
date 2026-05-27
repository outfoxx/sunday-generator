from __future__ import annotations

from .models import EventEnvelope
from .runtime import EventStream, Transport, path_template
from pydantic import TypeAdapter

__all__ = ["EventsClient"]


class EventsClient:
    """Client operations for the Events service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = transport

    def stream_events(self) -> EventStream[EventEnvelope]:
        """Create the streamEvents event stream."""
        request = self._transport.build_request(
            "GET",
            path_template("/events", {}),
        )
        return EventStream(
            transport=self._transport,
            request=request,
            decode=_decode_stream_events_event,
        )


def _decode_stream_events_event(data: str) -> EventEnvelope:
    return TypeAdapter(EventEnvelope).validate_json(data)
