from __future__ import annotations

from datetime import date, datetime
from enum import StrEnum
from pydantic import AnyUrl, BaseModel, ConfigDict, Field, TypeAdapter, model_validator
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


class ProjectView(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    project_id: str = Field(alias="projectId")
    unique_id: UniqueId = Field(alias="uniqueId")
    resource_id: UUID = Field(alias="resourceId")
    created_at: datetime = Field(alias="createdAt")
    release_date: date | None = Field(default=None, alias="releaseDate")
    home_page: AnyUrl | None = Field(default=None, alias="homePage")
    avatar: bytes | None = None
    display_name: str | None = Field(default=None, alias="display-name")
    status: ProjectStatus
    tags: list[str] | None = None


class UserSummaryResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    user_id: str = Field(alias="userId")
    email: str


class UserSelfResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    user_id: str = Field(alias="userId")
    email: str
    created_at: datetime = Field(alias="createdAt")


type UserResponse = UserSelfResponse | UserSummaryResponse


class UserIdentity(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    kind: Literal["user"]
    user_id: str = Field(alias="userId")


class ServiceIdentity(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    kind: Literal["service"]
    service_id: str = Field(alias="serviceId")


type Identity = Annotated[
    UserIdentity | ServiceIdentity,
    Field(discriminator="kind"),
]


class EventEnvelope(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

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


class ProjectCreatedData(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    project_id: str = Field(alias="projectId")


class ProjectDeletedData(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    project_id: str = Field(alias="projectId")
    reason: str | None = None
