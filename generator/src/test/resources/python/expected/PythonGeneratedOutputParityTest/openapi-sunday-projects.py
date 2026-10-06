from __future__ import annotations

from dataclasses import dataclass as _dataclass
from enum import Enum as _Enum
from collections.abc import Callable as _Callable, Mapping as _Mapping, Sequence as _Sequence
from sunday import (
    ClientSettings as _ClientSettings,
    TokenManagerFactory as _TokenManagerFactory,
    UNSET as _UNSET,
    UnsetType as _UnsetType,
    MediaType as _MediaType,
    Credentials as _Credentials,
    SecurityBinding as _SecurityBinding,
)
from .config import ProjectsAPIConfig

from .models import Project
from collections.abc import Sequence
from pydantic import TypeAdapter
from sunday import (
    ClientSettings,
    MediaType,
    Operation,
    OperationSpec,
    ParameterLocation,
    ParameterSpec,
    ParameterStyle,
    RequestSpec,
    ResponseSpec,
    Transport,
)

__all__ = ["ProjectsClient"]


_named_project_adapter: TypeAdapter[Project] = TypeAdapter(Project)


_get_project_responses: tuple[ResponseSpec[Project], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_project_adapter.validate_python,
    ),
)


class ProjectsClient[TransportRequestT, TransportResponseT]:
    """Client operations for the Projects service."""

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
        self._client_settings = client_settings

    def get_project(
        self,
        project_id: str,
    ) -> Operation[Project, TransportRequestT, TransportResponseT]:
        """Create the getProject operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/projects/{projectId}",
            parameters=(
                ParameterSpec(
                    name="projectId",
                    value=project_id,
                    location=ParameterLocation.PATH,
                    style=ParameterStyle.SIMPLE,
                    explode=False,
                ),
            ),
            accept_types=(MediaType("application/json"),),
            security=self._client_settings.bindings.get("getProject", ()) if self._client_settings is not None else (),
        )
        operation_spec: OperationSpec[None, Project] = OperationSpec(
            request=request_spec,
            responses=_get_project_responses,
        )
        return Operation(self.transport, operation_spec)


class ProjectsSecurityAlternative(_Enum):
    """Complete security alternatives, retaining each scheme's required scopes."""

    PUBLIC = ()

    def matches(self, bindings: _Sequence[_SecurityBinding]) -> bool:
        """Match a whole alternative without dropping schemes or scopes."""
        requirement = tuple(sorted((binding.scheme, tuple(sorted(binding.scopes))) for binding in bindings))
        return bool(requirement == self.value)


@_dataclass(frozen=True, kw_only=True)
class ProjectsCredentials:
    """Scheme-specific credentials; one complete alternative is required per operation."""

    pass


def create_projects[TransportRequestT, TransportResponseT](
    config: ProjectsAPIConfig,
    transport_factory: _Callable[[_ClientSettings], Transport[TransportRequestT, TransportResponseT]],
    *,
    credentials: ProjectsCredentials | None = None,
    default_content_types: _Sequence[_MediaType] | None = None,
    default_accept_types: _Sequence[_MediaType] | None = None,
    security_profile: str | None | _UnsetType = _UNSET,
    security_selection: _Mapping[str, ProjectsSecurityAlternative] | None = None,
    token_manager_factory: _TokenManagerFactory | None = None,
) -> ProjectsClient[TransportRequestT, TransportResponseT]:
    """Construct a service with the application's chosen transport and compatible credentials."""
    credentials = credentials or ProjectsCredentials()
    supplied: dict[str, _Credentials] = {}
    default_profiles: dict[str, str | None] = {
        "server1": None,
    }
    profile = default_profiles[config.server_id] if isinstance(security_profile, _UnsetType) else security_profile
    alternatives: dict[tuple[str, str | None], dict[str, list[list[_SecurityBinding]]]] = {
        ("server1", None): {
            "getProject": [[]],
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
    settings = _ClientSettings.resolve(
        config.base_url(), selected_alternatives, supplied, token_manager_factory=token_manager_factory
    )
    transport = transport_factory(settings)
    return ProjectsClient(
        transport,
        client_settings=settings,
        default_content_types=() if default_content_types is None else default_content_types,
        default_accept_types=() if default_accept_types is None else default_accept_types,
    )


__all__ += ["create_projects", "ProjectsCredentials", "ProjectsSecurityAlternative"]
