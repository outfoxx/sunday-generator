from __future__ import annotations

from .models import ProjectView, UniqueId, UpdateProjectRequest
from .runtime import (
    Operation,
    StreamingBody,
    StreamingOperation,
    Transport,
    TransportRequest,
    json_body,
    parameter_map,
    path_template,
)
from httpx import Response
from pydantic import TypeAdapter

__all__ = ["ProjectsClient"]


class ProjectsClient:
    """Client operations for the Projects service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = transport

    def get_project(
        self,
        project_id: str,
    ) -> Operation[ProjectView]:
        """Create the getProject operation."""
        request = self._transport.build_request(
            "GET",
            path_template("/projects/{projectId}", {"projectId": project_id}),
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_get_project_response,
        )

    def list_projects(self) -> Operation[list[ProjectView]]:
        """Create the listProjects operation."""
        request = self._transport.build_request(
            "GET",
            path_template("/projects", {}),
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_list_projects_response,
        )

    def update_project(
        self,
        project_id: str,
        body: UpdateProjectRequest,
        include_archived: bool | None = False,
        x_trace_id: str | None = None,
    ) -> Operation[ProjectView]:
        """Create the updateProject operation."""
        request = self._transport.build_request(
            "PUT",
            path_template("/projects/{projectId}", {"projectId": project_id}),
            params=parameter_map({"includeArchived": include_archived}),
            headers=parameter_map({"X-Trace-Id": x_trace_id}),
            json=json_body(body),
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_update_project_response,
        )

    def put_project_avatar(
        self,
        project_id: str,
        body: bytes,
        content_type: str | None = None,
    ) -> Operation[None]:
        """Create the putProjectAvatar operation."""
        request = self._transport.build_request(
            "PUT",
            path_template("/projects/{projectId}/avatar", {"projectId": project_id}),
            headers=parameter_map({"Content-Type": content_type}),
            content=body,
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_put_project_avatar_response,
        )

    def import_project_archive(
        self,
        project_id: str,
        body: StreamingBody,
    ) -> StreamingOperation[UniqueId]:
        """Create the importProjectArchive operation."""

        def build_request() -> TransportRequest:
            return self._transport.build_request(
                "POST",
                path_template("/projects/{projectId}/archive", {"projectId": project_id}),
                headers={"Content-Type": "application/x-tar"},
                content=body.content(),
            )

        return StreamingOperation(
            transport=self._transport,
            build_request=build_request,
            decode=_decode_import_project_archive_response,
        )

    def create_project_revision(
        self,
        project_id: str,
    ) -> Operation[UniqueId]:
        """Create the createProjectRevision operation."""
        request = self._transport.build_request(
            "POST",
            path_template("/projects/{projectId}/revisions", {"projectId": project_id}),
        )
        return Operation(
            transport=self._transport,
            request=request,
            decode=_decode_create_project_revision_response,
        )


def _decode_get_project_response(response: Response) -> ProjectView:
    return TypeAdapter(ProjectView).validate_python(response.json())


def _decode_list_projects_response(response: Response) -> list[ProjectView]:
    return TypeAdapter(list[ProjectView]).validate_python(response.json())


def _decode_update_project_response(response: Response) -> ProjectView:
    return TypeAdapter(ProjectView).validate_python(response.json())


def _decode_put_project_avatar_response(response: Response) -> None:
    return None


def _decode_import_project_archive_response(response: Response) -> UniqueId:
    return TypeAdapter(UniqueId).validate_python(response.json())


def _decode_create_project_revision_response(response: Response) -> UniqueId:
    return TypeAdapter(UniqueId).validate_python(response.json())
