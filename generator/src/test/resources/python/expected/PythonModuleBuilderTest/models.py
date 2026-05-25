from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

__all__ = ["Project"]


class Project(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    project_id: str = Field(alias="project-id")
    name: str
