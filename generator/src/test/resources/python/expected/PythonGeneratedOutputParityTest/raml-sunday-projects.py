from __future__ import annotations

from .models import Project
from .problems import register_problems
from pydantic import TypeAdapter
from sunday import (
    MediaType,
    Operation,
    OperationSpec,
    ParameterLocation,
    ParameterSpec,
    RequestSpec,
    ResponseSpec,
    Transport,
)

__all__ = ["ProjectsClient"]


class ProjectsClient[TransportRequestT, TransportResponseT]:
    """Client operations for the Projects service."""

    def __init__(self, transport: Transport[TransportRequestT, TransportResponseT]) -> None:
        self._transport = transport
        register_problems(self._transport)

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
                    style=None,
                    explode=None,
                    allow_reserved=False,
                    allow_empty_value=False,
                ),
            ),
            body=None,
            content_types=(),
            accept_types=(MediaType("application/json"),),
        )
        operation_spec: OperationSpec[None, Project] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_get_project_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)


def _decode_get_project_200_0_0(value: object) -> Project:
    return TypeAdapter(Project).validate_python(value)
