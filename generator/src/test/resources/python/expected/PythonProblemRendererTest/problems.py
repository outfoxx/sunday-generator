from __future__ import annotations

from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field
from typing import Any, ClassVar, Self
from uuid import UUID

__all__ = ["ProblemPayload", "Problem", "ProjectNotFoundProblemPayload", "ProjectNotFoundProblem"]


class ProblemPayload(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    type: str = "about:blank"
    title: str | None = None
    status: int | None = None
    detail: str | None = None
    instance: str | None = None


class Problem(Exception):
    payload_type: ClassVar[type[ProblemPayload]] = ProblemPayload
    payload: ProblemPayload

    def __init__(self, payload: ProblemPayload) -> None:
        super().__init__(payload.title or payload.type)
        self.payload = payload

    @classmethod
    def model_validate(cls, value: object) -> Self:
        return cls(cls.payload_type.model_validate(value))

    @property
    def type(self) -> str:
        return self.payload.type

    @property
    def title(self) -> str | None:
        return self.payload.title

    @property
    def status(self) -> int | None:
        return self.payload.status

    @property
    def detail(self) -> str | None:
        return self.payload.detail

    @property
    def instance(self) -> str | None:
        return self.payload.instance

    def model_dump(self, **kwargs: Any) -> dict[str, Any]:
        return self.payload.model_dump(**kwargs)


class ProjectNotFoundProblemPayload(ProblemPayload):
    type: str = "https://turnpost.example/problems/project-not-found"
    title: str | None = "Project not found"
    status: int | None = 404
    project_id: UUID = Field(alias="projectId")
    retry_after: datetime | None = Field(default=None, alias="retry-after")


class ProjectNotFoundProblem(Problem):
    payload_type: ClassVar[type[ProjectNotFoundProblemPayload]] = ProjectNotFoundProblemPayload
    payload: ProjectNotFoundProblemPayload

    def __init__(
        self,
        payload: ProjectNotFoundProblemPayload | None = None,
        **values: object,
    ) -> None:
        super().__init__(payload or self.payload_type.model_validate(values))

    @property
    def project_id(self) -> UUID:
        return self.payload.project_id

    @property
    def retry_after(self) -> datetime | None:
        return self.payload.retry_after
