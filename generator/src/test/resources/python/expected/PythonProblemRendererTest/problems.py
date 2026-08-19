from __future__ import annotations

from datetime import datetime
from pydantic import Field
from sunday import Problem as _Problem, ProblemPayload as _ProblemPayload, ProblemRegistry
from typing import ClassVar
from uuid import UUID

__all__ = ["ProblemPayload", "Problem", "register_problems", "ProjectNotFoundProblemPayload", "ProjectNotFoundProblem"]


ProblemPayload = _ProblemPayload
Problem = _Problem


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


def register_problems(registry: ProblemRegistry) -> None:
    """Register generated problem types for client response decoding."""
    registry.register("https://turnpost.example/problems/project-not-found", ProjectNotFoundProblem)
