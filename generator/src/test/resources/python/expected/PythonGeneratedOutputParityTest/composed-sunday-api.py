from __future__ import annotations

from dataclasses import dataclass as _dataclass
from enum import Enum as _Enum
from collections.abc import Callable as _Callable, Mapping as _Mapping, Sequence as _Sequence
from sunday import (
    ClientSettings as _ClientSettings,
    UNSET as _UNSET,
    UnsetType as _UnsetType,
    MediaType as _MediaType,
    Credentials as _Credentials,
    SecurityBinding as _SecurityBinding,
)
from .config import CraftHTTPAPIConfig

from .events import EventsClient
from .projects import ProjectsClient
from .users import UsersClient
from collections.abc import Sequence
from sunday import ClientSettings, MediaType, Transport

__all__ = ["ParityAPI"]


class ParityAPI[TransportRequestT, TransportResponseT]:
    """Aggregate client for all generated service clients."""

    def __init__(
        self,
        transport: Transport[TransportRequestT, TransportResponseT],
        *,
        default_content_types: Sequence[MediaType] = (),
        default_accept_types: Sequence[MediaType] = (),
        client_settings: ClientSettings | None = None,
    ) -> None:
        self.transport = transport
        self.default_content_types = tuple(default_content_types)
        self.default_accept_types = tuple(default_accept_types)
        self.projects = ProjectsClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
            client_settings=client_settings,
        )
        self.users = UsersClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
            client_settings=client_settings,
        )
        self.events = EventsClient(
            transport,
            default_content_types=self.default_content_types,
            default_accept_types=self.default_accept_types,
            client_settings=client_settings,
        )


class ParityAPISecurityAlternative(_Enum):
    """Complete security alternatives, retaining each scheme's required scopes."""

    PUBLIC = ()

    def matches(self, bindings: _Sequence[_SecurityBinding]) -> bool:
        """Match a whole alternative without dropping schemes or scopes."""
        requirement = tuple(sorted((binding.scheme, tuple(sorted(binding.scopes))) for binding in bindings))
        return bool(requirement == self.value)


@_dataclass(frozen=True, kw_only=True)
class ParityAPICredentials:
    """Scheme-specific credentials; one complete alternative is required per operation."""

    pass


def create_parity_api[TransportRequestT, TransportResponseT](
    config: CraftHTTPAPIConfig,
    transport_factory: _Callable[[_ClientSettings], Transport[TransportRequestT, TransportResponseT]],
    *,
    credentials: ParityAPICredentials | None = None,
    default_content_types: _Sequence[_MediaType] | None = None,
    default_accept_types: _Sequence[_MediaType] | None = None,
    security_profile: str | None | _UnsetType = _UNSET,
    security_selection: _Mapping[str, ParityAPISecurityAlternative] | None = None,
) -> ParityAPI[TransportRequestT, TransportResponseT]:
    """Construct a service with the application's chosen transport and compatible credentials."""
    credentials = credentials or ParityAPICredentials()
    supplied: dict[str, _Credentials] = {}
    default_profiles: dict[str, str | None] = {
        "server1": None,
    }
    profile = default_profiles[config.server_id] if isinstance(security_profile, _UnsetType) else security_profile
    alternatives: dict[tuple[str, str | None], dict[str, list[list[_SecurityBinding]]]] = {
        ("server1", None): {
            "getProject": [[]],
            "getUser": [[]],
            "streamEvents": [[]],
        },
    }
    key = (config.server_id, profile)
    if key not in alternatives:
        raise ValueError("Unknown client security profile")
    if security_selection and any(operation not in alternatives[key] for operation in security_selection):
        raise ValueError("Unknown operation in security selection")
    selected_alternatives: dict[str, list[list[_SecurityBinding]]] = {}
    for operation, choices in alternatives[key].items():
        selection = (security_selection or {}).get(operation)
        if selection is not None:
            choices = [choice for choice in choices if selection.matches(choice)]
        selected_alternatives[operation] = choices
    settings = _ClientSettings.resolve(config.base_url(), selected_alternatives, supplied)
    transport = transport_factory(settings)
    return ParityAPI(
        transport,
        client_settings=settings,
        default_content_types=() if default_content_types is None else default_content_types,
        default_accept_types=() if default_accept_types is None else default_accept_types,
    )


__all__ += ["create_parity_api", "ParityAPICredentials", "ParityAPISecurityAlternative"]
