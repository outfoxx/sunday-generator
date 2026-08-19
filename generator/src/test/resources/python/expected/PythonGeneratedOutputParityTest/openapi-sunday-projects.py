from __future__ import annotations

from .models import Project
from collections.abc import Sequence
from pydantic import TypeAdapter
from sunday import (
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
    ) -> None:
        self.transport = transport
        self.default_content_types = tuple(default_content_types)
        self.default_accept_types = tuple(default_accept_types)

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
        )
        operation_spec: OperationSpec[None, Project] = OperationSpec(
            request=request_spec,
            responses=_get_project_responses,
        )
        return Operation(self.transport, operation_spec)
