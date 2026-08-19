from __future__ import annotations

from .models import Project
from .problems import register_problems
from .runtime import Operation, Transport, as_transport, path_template
from httpx import Response
from pydantic import TypeAdapter

__all__ = ["ProjectsClient"]


class ProjectsClient:
    """Client operations for the Projects service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = as_transport(transport)
        register_problems(self._transport.problem_registry)

    def get_project(
        self,
        project_id: str,
    ) -> Operation[Project]:
        """Create the getProject operation."""
        request = self._transport.client.build_request(
            "GET",
            path_template("/projects/{projectId}", {"projectId": project_id}),
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_get_project_response,
        )


def _decode_get_project_response(response: Response) -> Project:
    return TypeAdapter(Project).validate_python(response.json())
