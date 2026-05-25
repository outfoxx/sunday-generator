from __future__ import annotations

from collections.abc import AsyncIterator, Callable, Mapping
from dataclasses import dataclass
from httpx import AsyncClient, Headers, Request, Response
from pydantic import BaseModel
from urllib.parse import quote

__all__ = [
    "Operation",
    "OperationResponse",
    "EventStream",
    "MediaType",
    "ResponseHeaders",
    "Transport",
    "TransportRequest",
    "TransportResponse",
    "json_body",
    "parameter_map",
    "path_template",
]


type Transport = AsyncClient
type TransportRequest = Request
type TransportResponse = Response


@dataclass(frozen=True, slots=True)
class MediaType:
    """A parsed media type header value."""

    value: str

    @property
    def type(self) -> str:
        """Return the top-level media type."""
        return self.value.split(";", 1)[0].partition("/")[0].strip().lower()

    @property
    def subtype(self) -> str:
        """Return the media subtype."""
        return self.value.split(";", 1)[0].partition("/")[2].strip().lower()

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class ResponseHeaders:
    """Case-insensitive access to response headers."""

    entries: tuple[tuple[str, str], ...]

    @classmethod
    def from_headers(cls, headers: Headers) -> ResponseHeaders:
        """Create a response header view from httpx headers."""
        return cls(tuple(headers.multi_items()))

    def get_all(self, name: str) -> tuple[str, ...]:
        """Return all response header values with the requested name."""
        lower_name = name.lower()
        return tuple(value for header_name, value in self.entries if header_name.lower() == lower_name)

    def get(self, name: str) -> str | None:
        """Return the first response header value with the requested name."""
        values = self.get_all(name)
        return values[0] if values else None

    @property
    def content_type(self) -> MediaType | None:
        """Return the parsed Content-Type header when present."""
        value = self.get("Content-Type")
        return MediaType(value) if value is not None else None


@dataclass(frozen=True, slots=True)
class OperationResponse[ResponseT]:
    """A typed operation result with its transport response metadata."""

    result: ResponseT
    transport_response: TransportResponse

    @property
    def status(self) -> int:
        """Return the HTTP response status code."""
        return self.transport_response.status_code

    @property
    def headers(self) -> ResponseHeaders:
        """Return a case-insensitive response header view."""
        return ResponseHeaders.from_headers(self.transport_response.headers)

    def get_headers(self, name: str) -> tuple[str, ...]:
        """Return all response header values with the requested name."""
        return self.headers.get_all(name)

    def get_header(self, name: str) -> str | None:
        """Return the first response header value with the requested name."""
        return self.headers.get(name)

    @property
    def content_type(self) -> MediaType | None:
        """Return the parsed Content-Type header when present."""
        return self.headers.content_type


@dataclass(frozen=True, slots=True)
class Operation[ResponseT]:
    """A prepared HTTP operation that can produce transport values or execute."""

    transport: Transport
    request: TransportRequest
    decode: Callable[[Response], ResponseT]

    async def execute(self) -> ResponseT:
        """Send the request and decode the successful response body."""
        return (await self.response()).result

    async def response(self) -> OperationResponse[ResponseT]:
        """Send the request and return the decoded response with metadata."""
        response = await self.transport_response()
        response.raise_for_status()
        return OperationResponse(result=self.decode(response), transport_response=response)

    def transport_request(self) -> TransportRequest:
        """Return the transport-specific request value."""
        return self.request

    async def transport_response(self) -> TransportResponse:
        """Send the request and return the transport-specific response value."""
        return await self.transport.send(self.request)


@dataclass(frozen=True, slots=True)
class EventStream[EventT]:
    """A prepared HTTP SSE stream that decodes events as they arrive."""

    transport: Transport
    request: TransportRequest
    decode: Callable[[str], EventT]

    def __aiter__(self) -> AsyncIterator[EventT]:
        return self.events()

    async def events(self) -> AsyncIterator[EventT]:
        """Send the request and decode server-sent event payloads."""
        response = await self.transport.send(self.request, stream=True)
        try:
            response.raise_for_status()
            async for payload in event_payloads(response):
                yield self.decode(payload)
        finally:
            await response.aclose()


def json_body(body: object | None) -> object | None:
    """Convert a generated model into a JSON request body."""
    if isinstance(body, BaseModel):
        return body.model_dump(mode="json", by_alias=True, exclude_none=True)
    return body


def parameter_map(parameters: Mapping[str, object | None]) -> dict[str, str]:
    """Format request parameters while omitting absent values."""
    return {name: parameter_value(value) for name, value in parameters.items() if value is not None}


def path_template(path: str, path_parameters: Mapping[str, object]) -> str:
    """Expand a URI path template using URL-encoded path parameter values."""
    for name, value in path_parameters.items():
        path = path.replace("{" + name + "}", quote(parameter_value(value), safe=""))
    return path


def parameter_value(value: object) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


async def event_payloads(response: Response) -> AsyncIterator[str]:
    data_lines: list[str] = []
    async for line in response.aiter_lines():
        if line == "":
            if data_lines:
                yield "\n".join(data_lines)
                data_lines = []
            continue
        if line.startswith(":"):
            continue
        field, _, value = line.partition(":")
        if value.startswith(" "):
            value = value[1:]
        if field == "data":
            data_lines.append(value)

    if data_lines:
        yield "\n".join(data_lines)
