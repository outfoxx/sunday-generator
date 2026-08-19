from __future__ import annotations

from .models import ProjectQuery, ProjectView, UniqueId, UpdateProjectRequest
from .problems import ProjectNotFoundProblem, register_problems
from .runtime import (
    MediaType,
    MultipartBody,
    NullableOperation,
    NullifySpec,
    Operation,
    OperationSpec,
    ParameterLocation,
    ParameterSpec,
    ParameterStyle,
    PatchDocument,
    RequestPayloadSpec,
    RequestSpec,
    ResponseHeaderSpec,
    ResponseSpec,
    StreamingBody,
    StreamingOperation,
    Transport,
    as_transport,
    parameter_object,
)
from pydantic import TypeAdapter, ValidationError

__all__ = ["ProjectsClient"]


class ProjectsClient:
    """Client operations for the Projects service."""

    def __init__(self, transport: Transport) -> None:
        self._transport = as_transport(transport)
        register_problems(self._transport.problem_registry)

    def get_project(
        self,
        project_id: str,
    ) -> Operation[ProjectView]:
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
        operation_spec: OperationSpec[None, ProjectView] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_get_project_200_0_0,
                    body_expected=True,
                    headers=(
                        ResponseHeaderSpec(
                            name="X-Revision",
                            decoder=TypeAdapter(int).validate_python,
                            required=True,
                            repeated=False,
                        ),
                    ),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def find_project(
        self,
        project_id: str,
    ) -> NullableOperation[ProjectView]:
        """Create the findProject operation."""
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
        operation_spec: OperationSpec[None, ProjectView] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_find_project_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return NullableOperation(
            self._transport,
            operation_spec,
            NullifySpec(statuses=(404,), problem_types=(ProjectNotFoundProblem,)),
        )

    def list_projects(
        self,
        query_string: ProjectQuery,
    ) -> Operation[list[ProjectView]]:
        """Create the listProjects operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="GET",
            path_template="/projects",
            parameters=(
                ParameterSpec(
                    name="",
                    value=parameter_object(query_string),
                    location=ParameterLocation.QUERY,
                    style=ParameterStyle.FORM,
                    explode=True,
                ),
            ),
            body=None,
            content_types=(),
            accept_types=(MediaType("application/json"),),
        )
        operation_spec: OperationSpec[None, list[ProjectView]] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_list_projects_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def update_project(
        self,
        project_id: str,
        body: UpdateProjectRequest,
        revision_id: str,
        include_archived: bool | None = False,
        x_trace_id: str | None = None,
    ) -> Operation[ProjectView]:
        """Create the updateProject operation."""
        request_spec: RequestSpec[UpdateProjectRequest] = RequestSpec(
            method="PUT",
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
                ParameterSpec(
                    name="includeArchived",
                    value=include_archived,
                    location=ParameterLocation.QUERY,
                    style=None,
                    explode=None,
                    allow_reserved=False,
                    allow_empty_value=False,
                ),
                ParameterSpec(
                    name="revisionId",
                    value=revision_id,
                    location=ParameterLocation.QUERY,
                    style=None,
                    explode=None,
                    allow_reserved=False,
                    allow_empty_value=False,
                ),
                ParameterSpec(
                    name="X-Trace-Id",
                    value=x_trace_id,
                    location=ParameterLocation.HEADER,
                    style=None,
                    explode=None,
                    allow_reserved=False,
                    allow_empty_value=False,
                ),
            ),
            body=body,
            content_types=(MediaType("application/json"),),
            accept_types=(MediaType("application/json"),),
        )
        operation_spec: OperationSpec[UpdateProjectRequest, ProjectView] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_update_project_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def put_project_avatar(
        self,
        project_id: str,
        body: bytes,
        content_type: str,
    ) -> Operation[None]:
        """Create the putProjectAvatar operation."""
        request_spec: RequestSpec[bytes] = RequestSpec(
            method="PUT",
            path_template="/projects/{projectId}/avatar",
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
                ParameterSpec(
                    name="Content-Type",
                    value=content_type,
                    location=ParameterLocation.HEADER,
                    style=None,
                    explode=None,
                    allow_reserved=False,
                    allow_empty_value=False,
                ),
            ),
            body=body,
            content_types=(MediaType("image/png"),),
            accept_types=(),
        )
        operation_spec: OperationSpec[bytes, None] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=204,
                    content_types=(),
                    decoder=None,
                    body_expected=False,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def import_project_archive(
        self,
        project_id: str,
        body: StreamingBody,
    ) -> StreamingOperation[UniqueId]:
        """Create the importProjectArchive operation."""
        request_spec: RequestSpec[StreamingBody] = RequestSpec(
            method="POST",
            path_template="/projects/{projectId}/archive",
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
            body=body,
            content_types=(MediaType("application/x-tar"),),
            accept_types=(MediaType("application/json"),),
        )
        operation_spec: OperationSpec[StreamingBody, UniqueId] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_import_project_archive_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return StreamingOperation(self._transport, operation_spec)

    def create_project_revision(
        self,
        project_id: str,
    ) -> Operation[UniqueId]:
        """Create the createProjectRevision operation."""
        request_spec: RequestSpec[None] = RequestSpec(
            method="POST",
            path_template="/projects/{projectId}/revisions",
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
        operation_spec: OperationSpec[None, UniqueId] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=200,
                    content_types=(MediaType("application/json"),),
                    decoder=_decode_create_project_revision_200_0_0,
                    body_expected=True,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def put_payload(
        self,
        body: ProjectView | bytes,
    ) -> Operation[None]:
        """Create the putPayload operation."""
        request_spec: RequestSpec[ProjectView | bytes] = RequestSpec(
            method="POST",
            path_template="/payload",
            parameters=(),
            payload=_request_payload_put_payload(body),
            accept_types=(),
        )
        operation_spec: OperationSpec[ProjectView | bytes, None] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=204,
                    content_types=(),
                    decoder=None,
                    body_expected=False,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def upload_multipart(
        self,
        body: MultipartBody,
    ) -> Operation[None]:
        """Create the uploadMultipart operation."""
        request_spec: RequestSpec[MultipartBody] = RequestSpec(
            method="POST",
            path_template="/multipart",
            parameters=(),
            body=body,
            content_types=(MediaType("multipart/form-data"),),
            accept_types=(),
        )
        operation_spec: OperationSpec[MultipartBody, None] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=204,
                    content_types=(),
                    decoder=None,
                    body_expected=False,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)

    def patch_project(
        self,
        body: PatchDocument,
    ) -> Operation[None]:
        """Create the patchProject operation."""
        request_spec: RequestSpec[PatchDocument] = RequestSpec(
            method="PATCH",
            path_template="/patch",
            parameters=(),
            body=body,
            content_types=(MediaType("application/json-patch+json"),),
            accept_types=(),
        )
        operation_spec: OperationSpec[PatchDocument, None] = OperationSpec(
            request=request_spec,
            responses=(
                ResponseSpec(
                    status=204,
                    content_types=(),
                    decoder=None,
                    body_expected=False,
                    headers=(),
                ),
            ),
        )
        return Operation(self._transport, operation_spec)


def _decode_get_project_200_0_0(value: object) -> ProjectView:
    return TypeAdapter(ProjectView).validate_python(value)


def _decode_find_project_200_0_0(value: object) -> ProjectView:
    return TypeAdapter(ProjectView).validate_python(value)


def _decode_list_projects_200_0_0(value: object) -> list[ProjectView]:
    return TypeAdapter(list[ProjectView]).validate_python(value)


def _decode_update_project_200_0_0(value: object) -> ProjectView:
    return TypeAdapter(ProjectView).validate_python(value)


def _decode_import_project_archive_200_0_0(value: object) -> UniqueId:
    return TypeAdapter(UniqueId).validate_python(value)


def _decode_create_project_revision_200_0_0(value: object) -> UniqueId:
    return TypeAdapter(UniqueId).validate_python(value)


def _request_payload_put_payload(body: ProjectView | bytes) -> RequestPayloadSpec[ProjectView | bytes]:
    validated: ProjectView | bytes
    try:
        validated = TypeAdapter(ProjectView).validate_python(body)
    except ValidationError:
        pass
    else:
        return RequestPayloadSpec(body=validated, content_types=(MediaType("application/json"),))
    try:
        validated = TypeAdapter(bytes).validate_python(body)
    except ValidationError:
        pass
    else:
        return RequestPayloadSpec(body=validated, content_types=(MediaType("application/octet-stream"),))
    raise ValueError("Request body does not match a declared payload for operation 'putPayload'")
