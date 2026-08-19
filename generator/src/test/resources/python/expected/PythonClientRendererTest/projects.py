from __future__ import annotations

from .models import ProjectQuery, ProjectView, UniqueId, UpdateProjectRequest
from .problems import ProjectNotFoundProblem, register_problems
from collections.abc import Sequence
from pydantic import TypeAdapter, ValidationError
from sunday import (
    MediaType,
    MultipartBody,
    NullableOperation,
    NullifySpec,
    Operation,
    OperationSpec,
    ParameterLocation,
    ParameterSpec,
    PatchDocument,
    RequestPayloadSpec,
    RequestSpec,
    ResponseHeaderSpec,
    ResponseSpec,
    StreamingBody,
    StreamingOperation,
    Transport,
    parameter_object,
)

__all__ = ["ProjectsClient"]


_named_project_view_adapter: TypeAdapter[ProjectView] = TypeAdapter(ProjectView)


_scalar_integer_adapter: TypeAdapter[int] = TypeAdapter(int)


_array_project_view_adapter: TypeAdapter[list[ProjectView]] = TypeAdapter(list[ProjectView])


_named_update_project_request_adapter: TypeAdapter[UpdateProjectRequest] = TypeAdapter(UpdateProjectRequest)


_scalar_file_adapter: TypeAdapter[bytes] = TypeAdapter(bytes)


_named_unique_id_adapter: TypeAdapter[UniqueId] = TypeAdapter(UniqueId)


_get_project_responses: tuple[ResponseSpec[ProjectView], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_project_view_adapter.validate_python,
        headers=(
            ResponseHeaderSpec(
                name="X-Revision",
                decoder=_scalar_integer_adapter.validate_python,
                required=True,
            ),
        ),
    ),
)


_find_project_responses: tuple[ResponseSpec[ProjectView], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_project_view_adapter.validate_python,
    ),
)


_list_projects_responses: tuple[ResponseSpec[list[ProjectView]], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_array_project_view_adapter.validate_python,
    ),
)


_update_project_responses: tuple[ResponseSpec[ProjectView], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_project_view_adapter.validate_python,
    ),
)


_put_project_avatar_responses: tuple[ResponseSpec[None], ...] = (ResponseSpec(status=204, body_expected=False),)


_import_project_archive_responses: tuple[ResponseSpec[UniqueId], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_unique_id_adapter.validate_python,
    ),
)


_create_project_revision_responses: tuple[ResponseSpec[UniqueId], ...] = (
    ResponseSpec(
        status=200,
        content_types=(MediaType("application/json"),),
        decoder=_named_unique_id_adapter.validate_python,
    ),
)


_put_payload_responses: tuple[ResponseSpec[None], ...] = (ResponseSpec(status=204, body_expected=False),)


def _request_payload_put_payload(body: ProjectView | bytes) -> RequestPayloadSpec[ProjectView | bytes]:
    validated: ProjectView | bytes
    try:
        validated = _named_project_view_adapter.validate_python(body)
    except ValidationError:
        pass
    else:
        return RequestPayloadSpec(body=validated, content_types=(MediaType("application/json"),))
    try:
        validated = _scalar_file_adapter.validate_python(body)
    except ValidationError:
        pass
    else:
        return RequestPayloadSpec(body=validated, content_types=(MediaType("application/octet-stream"),))
    raise ValueError("Request body does not match a declared payload for operation 'putPayload'")


_upload_multipart_responses: tuple[ResponseSpec[None], ...] = (ResponseSpec(status=204, body_expected=False),)


_patch_project_responses: tuple[ResponseSpec[None], ...] = (ResponseSpec(status=204, body_expected=False),)


class ProjectsClient[TransportRequestT, TransportResponseT]:
    """Client operations for the Projects service."""

    def __init__(
        self,
        transport: Transport[TransportRequestT, TransportResponseT],
        *,
        default_content_types: Sequence[MediaType] = (MediaType("application/json"),),
        default_accept_types: Sequence[MediaType] = (MediaType("application/json"),),
    ) -> None:
        self.transport = transport
        self.default_content_types = tuple(default_content_types)
        self.default_accept_types = tuple(default_accept_types)
        register_problems(self.transport)

    def get_project(
        self,
        project_id: str,
    ) -> Operation[ProjectView, TransportRequestT, TransportResponseT]:
        """Create the getProject operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/projects/{projectId}",
            parameters=(ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),),
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[None, ProjectView] = OperationSpec(
            request=request_spec,
            responses=_get_project_responses,
        )
        return Operation(self.transport, operation_spec)

    def find_project(
        self,
        project_id: str,
    ) -> NullableOperation[ProjectView, TransportRequestT, TransportResponseT]:
        """Create the findProject operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/projects/{projectId}",
            parameters=(ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),),
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[None, ProjectView] = OperationSpec(
            request=request_spec,
            responses=_find_project_responses,
        )
        return NullableOperation(
            self.transport,
            operation_spec,
            NullifySpec(statuses=(404,), problem_types=(ProjectNotFoundProblem,)),
        )

    def list_projects(
        self,
        query_string: ProjectQuery,
    ) -> Operation[list[ProjectView], TransportRequestT, TransportResponseT]:
        """Create the listProjects operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/projects",
            parameters=(
                ParameterSpec(name="", value=parameter_object(query_string), location=ParameterLocation.QUERY),
            ),
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[None, list[ProjectView]] = OperationSpec(
            request=request_spec,
            responses=_list_projects_responses,
        )
        return Operation(self.transport, operation_spec)

    def update_project(
        self,
        project_id: str,
        body: UpdateProjectRequest,
        revision_id: str,
        include_archived: bool | None = False,
        x_trace_id: str | None = None,
    ) -> Operation[ProjectView, TransportRequestT, TransportResponseT]:
        """Create the updateProject operation."""
        request_spec: RequestSpec[UpdateProjectRequest] = RequestSpec(
            method="PUT",
            path_template="/projects/{projectId}",
            parameters=(
                ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),
                ParameterSpec(name="includeArchived", value=include_archived, location=ParameterLocation.QUERY),
                ParameterSpec(name="revisionId", value=revision_id, location=ParameterLocation.QUERY),
                ParameterSpec(name="X-Trace-Id", value=x_trace_id, location=ParameterLocation.HEADER),
            ),
            body=body,
            content_types=self.default_content_types,
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[UpdateProjectRequest, ProjectView] = OperationSpec(
            request=request_spec,
            responses=_update_project_responses,
        )
        return Operation(self.transport, operation_spec)

    def put_project_avatar(
        self,
        project_id: str,
        body: bytes,
        content_type: str,
    ) -> Operation[None, TransportRequestT, TransportResponseT]:
        """Create the putProjectAvatar operation."""
        request_spec: RequestSpec[bytes] = RequestSpec(
            method="PUT",
            path_template="/projects/{projectId}/avatar",
            parameters=(
                ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),
                ParameterSpec(name="Content-Type", value=content_type, location=ParameterLocation.HEADER),
            ),
            body=body,
            content_types=(MediaType("image/png"),),
        )
        operation_spec: OperationSpec[bytes, None] = OperationSpec(
            request=request_spec,
            responses=_put_project_avatar_responses,
        )
        return Operation(self.transport, operation_spec)

    def import_project_archive(
        self,
        project_id: str,
        body: StreamingBody,
    ) -> StreamingOperation[UniqueId, TransportRequestT, TransportResponseT]:
        """Create the importProjectArchive operation."""
        request_spec: RequestSpec[StreamingBody] = RequestSpec(
            method="POST",
            path_template="/projects/{projectId}/archive",
            parameters=(ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),),
            body=body,
            content_types=(MediaType("application/x-tar"),),
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[StreamingBody, UniqueId] = OperationSpec(
            request=request_spec,
            responses=_import_project_archive_responses,
        )
        return StreamingOperation(self.transport, operation_spec)

    def create_project_revision(
        self,
        project_id: str,
    ) -> Operation[UniqueId, TransportRequestT, TransportResponseT]:
        """Create the createProjectRevision operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="POST",
            path_template="/projects/{projectId}/revisions",
            parameters=(ParameterSpec(name="projectId", value=project_id, location=ParameterLocation.PATH),),
            accept_types=self.default_accept_types,
        )
        operation_spec: OperationSpec[None, UniqueId] = OperationSpec(
            request=request_spec,
            responses=_create_project_revision_responses,
        )
        return Operation(self.transport, operation_spec)

    def put_payload(
        self,
        body: ProjectView | bytes,
    ) -> Operation[None, TransportRequestT, TransportResponseT]:
        """Create the putPayload operation."""
        request_spec: RequestSpec[ProjectView | bytes] = RequestSpec(
            method="POST",
            path_template="/payload",
            payload=_request_payload_put_payload(body),
        )
        operation_spec: OperationSpec[ProjectView | bytes, None] = OperationSpec(
            request=request_spec,
            responses=_put_payload_responses,
        )
        return Operation(self.transport, operation_spec)

    def upload_multipart(
        self,
        body: MultipartBody,
    ) -> Operation[None, TransportRequestT, TransportResponseT]:
        """Create the uploadMultipart operation."""
        request_spec: RequestSpec[MultipartBody] = RequestSpec(
            method="POST",
            path_template="/multipart",
            body=body,
            content_types=(MediaType("multipart/form-data"),),
        )
        operation_spec: OperationSpec[MultipartBody, None] = OperationSpec(
            request=request_spec,
            responses=_upload_multipart_responses,
        )
        return Operation(self.transport, operation_spec)

    def patch_project(
        self,
        body: PatchDocument,
    ) -> Operation[None, TransportRequestT, TransportResponseT]:
        """Create the patchProject operation."""
        request_spec: RequestSpec[PatchDocument] = RequestSpec(
            method="PATCH",
            path_template="/patch",
            body=body,
            content_types=(MediaType("application/json-patch+json"),),
        )
        operation_spec: OperationSpec[PatchDocument, None] = OperationSpec(
            request=request_spec,
            responses=_patch_project_responses,
        )
        return Operation(self.transport, operation_spec)
