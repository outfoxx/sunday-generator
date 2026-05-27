/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonLitestarRendererTest : PythonTest() {

  @Test
  fun `generates litestar server stub for first operation`(compiler: PythonCompiler) {
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()
    val modelsModule =
      PythonModelRenderer("turnpost_api")
        .renderModels(
          listOf(
            GeneratedModel(
              name = "ProjectView",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UpdateProjectRequest",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    serializationName = "displayName",
                  ),
                ),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorMappings = mapOf("project.created" to GeneratedTypeRef.named("ProjectCreatedData")),
            ),
            GeneratedModel(
              name = "ProjectCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "project.created",
              properties =
                listOf(
                  GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
        )
    val serviceModule =
      PythonLitestarRenderer("turnpost_api")
        .renderService(
          GeneratedService(
            name = "Projects",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getProject",
                  method = "GET",
                  path = "/projects/{projectId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.named("ProjectView"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                ),
                GeneratedOperation(
                  id = "updateProject",
                  method = "PUT",
                  path = "/projects/{projectId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "includeArchived",
                        location = GeneratedParameter.Location.QUERY,
                        type = GeneratedTypeRef.scalar("boolean"),
                        serializationName = "includeArchived",
                        defaultValue = false,
                      ),
                      GeneratedParameter(
                        name = "xTraceId",
                        location = GeneratedParameter.Location.HEADER,
                        type = GeneratedTypeRef.scalar("string"),
                        serializationName = "X-Trace-Id",
                      ),
                    ),
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.named("UpdateProjectRequest"),
                      mediaTypes = listOf("application/json"),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.named("ProjectView"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                ),
                GeneratedOperation(
                  id = "deleteProjectAvatar",
                  method = "DELETE",
                  path = "/projects/{projectId}/avatar",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(status = 204),
                      GeneratedResponse(
                        status = 400,
                        type = GeneratedTypeRef.named("ProjectView"),
                        mediaTypes = listOf("application/problem+json"),
                      ),
                    ),
                ),
              ),
          ),
        )
    val eventsModule =
      PythonLitestarRenderer("turnpost_api")
        .renderService(
          GeneratedService(
            name = "Events",
            operations =
              listOf(
                GeneratedOperation(
                  id = "streamProjectEvents",
                  method = "GET",
                  path = "/events",
                  streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.named("EventEnvelope"),
                        mediaTypes = listOf("text/event-stream"),
                      ),
                    ),
                ),
              ),
          ),
        )

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule, serviceModule, eventsModule),
        importModules = listOf("turnpost_api.events_server", "turnpost_api.projects_server"),
        smokeCode =
          """
          from collections.abc import AsyncIterator

          from litestar import Litestar
          from litestar.plugins.pydantic import PydanticPlugin
          from litestar.testing import TestClient

          from turnpost_api.events_server import EventsService, create_events_router
          from turnpost_api.models import EventEnvelope, ProjectCreatedData, ProjectView, UpdateProjectRequest
          from turnpost_api.projects_server import ProjectsService, create_projects_router


          class ProjectsImplementation:
              async def get_project(self, project_id: str) -> ProjectView:
                  return ProjectView(projectId=project_id, name="Roadmap")

              async def update_project(
                  self,
                  project_id: str,
                  body: UpdateProjectRequest,
                  include_archived: bool | None = False,
                  x_trace_id: str | None = None,
              ) -> ProjectView:
                  assert include_archived is True
                  assert x_trace_id == "trace-1"
                  return ProjectView(projectId=project_id, name=body.display_name)

              async def delete_project_avatar(self, project_id: str) -> None:
                  assert project_id == "project-1"


          class EventsImplementation:
              async def stream_project_events(self) -> AsyncIterator[EventEnvelope]:
                  yield EventEnvelope(type="project.created", data=ProjectCreatedData(project_id="project-1"))


          service: ProjectsService = ProjectsImplementation()
          events_service: EventsService = EventsImplementation()
          app = Litestar(
              route_handlers=[create_projects_router(service), create_events_router(events_service)],
              plugins=[PydanticPlugin(prefer_alias=True)],
          )

          with TestClient(app=app) as client:
              response = client.get("/projects/project-1")

          assert response.status_code == 200
          assert response.json() == {"projectId": "project-1", "name": "Roadmap"}

          with TestClient(app=app) as client:
              response = client.put(
                  "/projects/project-1?includeArchived=true",
                  headers={"X-Trace-Id": "trace-1"},
                  json={"displayName": "Updated"},
              )

          assert response.status_code == 200
          assert response.json() == {"projectId": "project-1", "name": "Updated"}

          with TestClient(app=app) as client:
              response = client.delete("/projects/project-1/avatar")

          assert response.status_code == 204
          assert response.content == b""

          with TestClient(app=app) as client:
              response = client.get("/events")

          assert response.status_code == 200
          assert "project.created" in response.text
          assert "project-1" in response.text
          """.trimIndent(),
      ),
    )

    assertPythonSnapshot(
      "PythonLitestarRendererTest/projects_server.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/projects_server.py"),
    )
    assertPythonSnapshot(
      "PythonLitestarRendererTest/events_server.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/events_server.py"),
    )
  }
}
