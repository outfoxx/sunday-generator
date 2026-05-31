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

import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
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

class PythonClientRendererTest : PythonTest() {

  @Test
  fun `generates operation runtime and first async httpx service method`(compiler: PythonCompiler) {
    val clientRenderer = PythonClientRenderer("turnpost_api")
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
              name = "UniqueId",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
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
                  GeneratedModelProperty(
                    "fromCommitId",
                    GeneratedTypeRef.scalar("string"),
                    serializationName = "fromCommitId",
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
    val runtimeModule = clientRenderer.renderRuntime()
    val serviceModule =
      clientRenderer.renderService(
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
                id = "listProjects",
                method = "GET",
                path = "/projects",
                responses =
                  listOf(
                    GeneratedResponse(
                      status = 200,
                      type =
                        GeneratedTypeRef(
                          kind = GeneratedTypeRef.Kind.ARRAY,
                          name = "ProjectViewArray",
                          arguments = listOf(GeneratedTypeRef.named("ProjectView")),
                        ),
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
                id = "putProjectAvatar",
                method = "PUT",
                path = "/projects/{projectId}/avatar",
                parameters =
                  listOf(
                    GeneratedParameter(
                      name = "projectId",
                      location = GeneratedParameter.Location.PATH,
                      type = GeneratedTypeRef.scalar("string"),
                      required = true,
                    ),
                    GeneratedParameter(
                      name = "contentType",
                      location = GeneratedParameter.Location.HEADER,
                      type = GeneratedTypeRef.scalar("string"),
                      serializationName = "Content-Type",
                      required = true,
                    ),
                  ),
                requestBody =
                  GeneratedPayload(
                    type = GeneratedTypeRef.scalar("file"),
                    mediaTypes = listOf("image/png"),
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
              GeneratedOperation(
                id = "importProjectArchive",
                method = "POST",
                path = "/projects/{projectId}/archive",
                parameters =
                  listOf(
                    GeneratedParameter(
                      name = "projectId",
                      location = GeneratedParameter.Location.PATH,
                      type = GeneratedTypeRef.scalar("string"),
                      required = true,
                    ),
                  ),
                requestBody =
                  GeneratedPayload(
                    type = GeneratedTypeRef.scalar("file"),
                    mediaTypes = listOf("application/x-tar"),
                    streaming = GeneratedModeFlag(client = true),
                  ),
                responses =
                  listOf(
                    GeneratedResponse(
                      status = 200,
                      type = GeneratedTypeRef.named("UniqueId"),
                      mediaTypes = listOf("application/json"),
                    ),
                  ),
              ),
              GeneratedOperation(
                id = "createProjectRevision",
                method = "POST",
                path = "/projects/{projectId}/revisions",
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
                      type = GeneratedTypeRef.named("UniqueId"),
                      mediaTypes = listOf("application/json"),
                    ),
                  ),
              ),
            ),
        ),
      )
    val eventsModule =
      clientRenderer.renderService(
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
        listOf(initModule, modelsModule, runtimeModule, serviceModule, eventsModule),
        importModules = listOf("turnpost_api.events", "turnpost_api.projects"),
        smokeCode =
          """
          import asyncio
          import json

          import httpx

          from turnpost_api.events import EventsClient
          from turnpost_api.models import ProjectCreatedData, UpdateProjectRequest
          from turnpost_api.projects import ProjectsClient
          from turnpost_api.runtime import StreamingBody


          class EventByteStream(httpx.AsyncByteStream):
              async def __aiter__(self):
                  yield b'data: {"type":"project.created","data":{"projectId":"project-1"}}\n\n'


          def handler(request: httpx.Request) -> httpx.Response:
              if request.url.path == "/events":
                  assert request.method == "GET"
                  return httpx.Response(
                      200,
                      headers={"content-type": "text/event-stream"},
                      stream=EventByteStream(),
                  )

              if request.method == "GET":
                  if request.url.path == "/projects":
                      return httpx.Response(200, json=[{"projectId": "project-1", "name": "Roadmap"}])
                  assert request.url.path == "/projects/project-1"
                  return httpx.Response(200, json={"projectId": "project-1", "name": "Roadmap"})

              if request.url.path == "/projects/project-1/avatar":
                  assert request.method == "PUT"
                  assert request.headers["Content-Type"] == "image/png"
                  assert request.content == b"avatar-bytes"
                  return httpx.Response(204)

              if request.url.path == "/projects/project-1/archive":
                  assert request.method == "POST"
                  assert request.headers["Content-Type"] == "application/x-tar"
                  assert request.content == b"archive-bytes"
                  return httpx.Response(200, json="import-1")

              if request.url.path == "/projects/project-1/revisions":
                  assert request.method == "POST"
                  return httpx.Response(200, json="revision-1")

              assert request.method == "PUT"
              assert request.url.path == "/projects/project-1"
              assert request.url.params["includeArchived"] == "true"
              assert request.headers["X-Trace-Id"] == "trace-1"
              assert json.loads(request.content) == {"displayName": "Updated"}
              return httpx.Response(200, json={"projectId": "project-1", "name": "Updated"})


          async def main() -> None:
              transport = httpx.MockTransport(handler)
              async with httpx.AsyncClient(base_url="https://api.example.test", transport=transport) as http_client:
                  operation = ProjectsClient(http_client).get_project("project-1")

                  request = operation.transport_request()
                  assert request.method == "GET"
                  assert request.url.path == "/projects/project-1"

                  response = await operation.response()
                  assert response.result.project_id == "project-1"
                  assert response.status == 200
                  assert str(response.content_type) == "application/json"
                  assert response.get_header("content-type") == "application/json"
                  assert response.get_headers("content-type") == ("application/json",)

                  project = await operation.execute()
                  assert project.project_id == "project-1"
                  assert project.name == "Roadmap"

                  projects = await ProjectsClient(http_client).list_projects().execute()
                  assert len(projects) == 1
                  assert projects[0].project_id == "project-1"
                  assert projects[0].name == "Roadmap"

                  update_operation = ProjectsClient(http_client).update_project(
                      "project-1",
                      UpdateProjectRequest(display_name="Updated", from_commit_id=None),
                      include_archived=True,
                      x_trace_id="trace-1",
                  )

                  updated_project = await update_operation.execute()
                  assert updated_project.project_id == "project-1"
                  assert updated_project.name == "Updated"

                  avatar = await ProjectsClient(http_client).put_project_avatar(
                      "project-1",
                      b"avatar-bytes",
                      content_type="image/png",
                  ).execute()
                  assert avatar is None

                  import_operation = ProjectsClient(http_client).import_project_archive(
                      "project-1",
                      StreamingBody.bytes(b"archive-bytes"),
                  )
                  import_request = import_operation.transport_request()
                  assert import_request.method == "POST"
                  assert import_request.url.path == "/projects/project-1/archive"

                  import_id = await import_operation.execute()
                  assert import_id == "import-1"

                  revision_id = await ProjectsClient(http_client).create_project_revision("project-1").execute()
                  assert revision_id == "revision-1"

                  stream = EventsClient(http_client).stream_project_events()
                  events = [event async for event in stream]
                  assert len(events) == 1
                  assert events[0].type == "project.created"
                  assert isinstance(events[0].data, ProjectCreatedData)
                  assert events[0].data.project_id == "project-1"


          asyncio.run(main())
          """.trimIndent(),
      ),
    )

    assertPythonSnapshot(
      "PythonClientRendererTest/runtime.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/runtime.py"),
    )
    assertPythonSnapshot(
      "PythonClientRendererTest/projects.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/projects.py"),
    )
    assertPythonSnapshot(
      "PythonClientRendererTest/events.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/events.py"),
    )
  }
}
