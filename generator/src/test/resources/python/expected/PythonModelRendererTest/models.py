from __future__ import annotations

from datetime import date
from enum import StrEnum
from pydantic import AnyUrl, AwareDatetime, Field, TypeAdapter, model_validator
from sunday import SundayModel
from typing import Annotated, Literal
from uuid import UUID

__all__ = [
    "ProjectStatus",
    "UniqueId",
    "ProjectView",
    "UserSummaryResponse",
    "UserSelfResponse",
    "UserResponse",
    "UserIdentity",
    "ServiceIdentity",
    "Identity",
    "EventEnvelope",
    "EventData",
    "ProjectCreatedData",
    "ProjectDeletedData",
]


class ProjectStatus(StrEnum):
    ACTIVE = "active"
    ARCHIVED = "archived"
    PENDING_REVIEW = "pending-review"


type UniqueId = str


class ProjectView(SundayModel):
    """Generated ProjectView model."""

    project_id: str = Field(alias="projectId")
    unique_id: UniqueId = Field(alias="uniqueId")
    resource_id: UUID = Field(alias="resourceId")
    created_at: AwareDatetime = Field(alias="createdAt")
    release_date: date | None = Field(default=None, alias="releaseDate")
    home_page: AnyUrl | None = Field(default=None, alias="homePage")
    avatar: bytes | None = Field(default=None)
    display_name: str | None = Field(default=None, alias="display-name")
    status: ProjectStatus
    tags: list[str] | None = Field(default=None)

    @model_validator(mode="before")
    @classmethod
    def _validate_wire_values(cls, data: object) -> object:
        if not isinstance(data, dict):
            return data
        data = dict(data)
        if "releaseDate" in data and data["releaseDate"] is None:
            raise ValueError("Property 'releaseDate' is not nullable")
        if "homePage" in data and data["homePage"] is None:
            raise ValueError("Property 'homePage' is not nullable")
        if "avatar" in data and data["avatar"] is None:
            raise ValueError("Property 'avatar' is not nullable")
        if "display-name" in data and data["display-name"] is None:
            raise ValueError("Property 'display-name' is not nullable")
        if "tags" in data and data["tags"] is None:
            raise ValueError("Property 'tags' is not nullable")
        return data


class UserSummaryResponse(SundayModel):
    """Generated UserSummaryResponse model."""

    user_id: str = Field(alias="userId")
    email: str


class UserSelfResponse(SundayModel):
    """Generated UserSelfResponse model."""

    user_id: str = Field(alias="userId")
    email: str
    created_at: AwareDatetime = Field(alias="createdAt")


type UserResponse = UserSelfResponse | UserSummaryResponse


class UserIdentity(SundayModel):
    """Generated UserIdentity model."""

    kind: Literal["user"]
    user_id: str = Field(alias="userId")


class ServiceIdentity(SundayModel):
    """Generated ServiceIdentity model."""

    kind: Literal["service"]
    service_id: str = Field(alias="serviceId")


type Identity = Annotated[
    UserIdentity | ServiceIdentity,
    Field(discriminator="kind"),
]


class EventEnvelope(SundayModel):
    """Generated EventEnvelope model."""

    type: str
    data: EventData

    @model_validator(mode="before")
    @classmethod
    def _validate_external_discriminators(cls, data: object) -> object:
        if not isinstance(data, dict):
            return data
        if data.get("type") == "project.created":
            data = dict(data)
            data["data"] = TypeAdapter(ProjectCreatedData).validate_python(data.get("data"))
        if data.get("type") == "project.deleted":
            data = dict(data)
            data["data"] = TypeAdapter(ProjectDeletedData).validate_python(data.get("data"))
        return data


type EventData = ProjectCreatedData | ProjectDeletedData


class ProjectCreatedData(SundayModel):
    """Generated ProjectCreatedData model."""

    project_id: str = Field(alias="projectId")


class ProjectDeletedData(SundayModel):
    """Generated ProjectDeletedData model."""

    project_id: str = Field(alias="projectId")
    reason: str | None = Field(default=None)

    @model_validator(mode="before")
    @classmethod
    def _validate_wire_values(cls, data: object) -> object:
        if not isinstance(data, dict):
            return data
        data = dict(data)
        if "reason" in data and data["reason"] is None:
            raise ValueError("Property 'reason' is not nullable")
        return data


ProjectView.model_rebuild()
UserSummaryResponse.model_rebuild()
UserSelfResponse.model_rebuild()
UserIdentity.model_rebuild()
ServiceIdentity.model_rebuild()
EventEnvelope.model_rebuild()
ProjectCreatedData.model_rebuild()
ProjectDeletedData.model_rebuild()
