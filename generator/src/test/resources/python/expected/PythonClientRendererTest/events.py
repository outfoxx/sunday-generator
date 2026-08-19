from __future__ import annotations

from .models import EventEnvelope
from .problems import register_problems
from .runtime import EventStream, MediaType, RequestSpec, ServerSentEvent, Transport, as_transport
from pydantic import TypeAdapter

__all__ = ["EventsClient"]


class EventsClient:
    """Client operations for the Events service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = as_transport(transport)
        register_problems(self._transport.problem_registry)

    def stream_project_events(self) -> EventStream[EventEnvelope]:
        """Create the streamProjectEvents operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/events",
            parameters=(),
            body=None,
            content_types=(),
            accept_types=(MediaType("text/event-stream"),),
        )
        return self._transport.event_stream(request_spec, _decode_stream_project_events_event)


def _decode_stream_project_events_event(event: ServerSentEvent) -> EventEnvelope:
    if event.data is None:
        raise ValueError("Server-sent events must contain data")
    return TypeAdapter(EventEnvelope).validate_json(event.data)
