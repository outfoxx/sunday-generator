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
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedPayloadOption
import io.outfoxx.sunday.generator.ir.GeneratedProblem
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
  fun `generates neutral operations with an explicit HTTPX consumer`(compiler: PythonCompiler) {
    val clientRenderer = PythonClientRenderer("turnpost_api", registerProblems = true)
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
              name = "ProjectQuery",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
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
                      headers =
                        listOf(
                          GeneratedParameter(
                            name = "xRevision",
                            location = GeneratedParameter.Location.HEADER,
                            type = GeneratedTypeRef.scalar("integer"),
                            serializationName = "X-Revision",
                            required = true,
                          ),
                        ),
                    ),
                  ),
              ),
              GeneratedOperation(
                id = "findProject",
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
                nullify =
                  GeneratedNullify(
                    problems = listOf(GeneratedTypeRef.named("ProjectNotFoundProblem")),
                    statuses = listOf(404),
                  ),
              ),
              GeneratedOperation(
                id = "listProjects",
                method = "GET",
                path = "/projects",
                queryString = GeneratedTypeRef.named("ProjectQuery"),
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
                      name = "revisionId",
                      location = GeneratedParameter.Location.QUERY,
                      type = GeneratedTypeRef.scalar("string"),
                      serializationName = "revisionId",
                      required = true,
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
              GeneratedOperation(
                id = "putPayload",
                method = "POST",
                path = "/payload",
                requestBody =
                  GeneratedPayload(
                    type = GeneratedTypeRef.named("ProjectView"),
                    mediaTypes = listOf("application/json"),
                    payloads =
                      listOf(
                        GeneratedPayloadOption(
                          type = GeneratedTypeRef.named("ProjectView"),
                          mediaTypes = listOf("application/json"),
                        ),
                        GeneratedPayloadOption(
                          type = GeneratedTypeRef.scalar("file"),
                          mediaTypes = listOf("application/octet-stream"),
                        ),
                      ),
                  ),
                responses = listOf(GeneratedResponse(status = 204)),
              ),
              GeneratedOperation(
                id = "uploadMultipart",
                method = "POST",
                path = "/multipart",
                requestBody =
                  GeneratedPayload(
                    type = GeneratedTypeRef.scalar("object"),
                    mediaTypes = listOf("multipart/form-data"),
                  ),
                responses = listOf(GeneratedResponse(status = 204)),
              ),
              GeneratedOperation(
                id = "patchProject",
                method = "PATCH",
                path = "/patch",
                requestBody =
                  GeneratedPayload(
                    type = GeneratedTypeRef.scalar("object"),
                    mediaTypes = listOf("application/json-patch+json"),
                  ),
                responses = listOf(GeneratedResponse(status = 204)),
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
    val problemsModule =
      PythonProblemRenderer("turnpost_api")
        .renderProblems(
          listOf(
            GeneratedProblem(
              name = "ProjectNotFoundProblem",
              typeUri = "https://turnpost.example/problems/project-not-found",
              status = 404,
            ),
          ),
        )

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule, problemsModule, serviceModule, eventsModule),
        importModules = listOf("turnpost_api.events", "turnpost_api.projects"),
        smokeCode =
          """
          import asyncio
          import json

          import httpx
          from sunday import (
              MultipartBody,
              MultipartPart,
              PatchDocument,
              PatchOperation,
              PatchOperationKind,
              StreamingBody,
          )
          from sunday.httpx import HttpxTransport

          from turnpost_api.events import EventsClient
          from turnpost_api.models import ProjectCreatedData, ProjectQuery, ProjectView, UpdateProjectRequest
          from turnpost_api.problems import ProjectNotFoundProblem
          from turnpost_api.projects import ProjectsClient


          class EventByteStream(httpx.AsyncByteStream):
              async def __aiter__(self):
                  yield b'data: {"type":"project.created","data":{"projectId":"project-1"}}\n\n'


          event_requests = 0


          def handler(request: httpx.Request) -> httpx.Response:
              global event_requests
              if request.url.path == "/events":
                  assert request.method == "GET"
                  event_requests += 1
                  if event_requests > 1:
                      return httpx.Response(204)
                  return httpx.Response(
                      200,
                      headers={"content-type": "text/event-stream"},
                      stream=EventByteStream(),
                  )

              if request.method == "GET":
                  if request.url.path == "/projects":
                      assert request.url.params.get_list("tags") == ["one", "two"]
                      return httpx.Response(200, json=[{"projectId": "project-1", "name": "Roadmap"}])
                  if request.url.path == "/projects/missing":
                      return httpx.Response(
                          404,
                          headers={"content-type": "application/problem+json"},
                          json={"type": "https://turnpost.example/problems/project-not-found", "status": 404},
                      )
                  assert request.url.path == "/projects/project-1"
                  return httpx.Response(
                      200,
                      headers={"X-Revision": "7"},
                      json={"projectId": "project-1", "name": "Roadmap"},
                  )

              if request.url.path == "/payload":
                  assert request.method == "POST"
                  if request.headers["content-type"] == "application/json":
                      assert json.loads(request.content)["projectId"] == "project-1"
                  else:
                      assert request.headers["content-type"] == "application/octet-stream"
                      assert request.content == b"payload-bytes"
                  return httpx.Response(204)

              if request.url.path == "/multipart":
                  assert request.method == "POST"
                  assert request.headers["content-type"].startswith("multipart/form-data; boundary=")
                  assert b'name="title"' in request.content
                  assert b"Roadmap" in request.content
                  return httpx.Response(204)

              if request.url.path == "/patch":
                  assert request.method == "PATCH"
                  assert request.headers["content-type"] == "application/json-patch+json"
                  assert json.loads(request.content) == [{"op": "replace", "path": "/name", "value": "Updated"}]
                  return httpx.Response(204)

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
              assert request.url.params["revisionId"] == "revision-1"
              assert request.headers["X-Trace-Id"] == "trace-1"
              assert json.loads(request.content) == {"displayName": "Updated", "fromCommitId": None}
              return httpx.Response(200, json={"projectId": "project-1", "name": "Updated"})


          async def main() -> None:
              transport = httpx.MockTransport(handler)
              async with httpx.AsyncClient(base_url="https://api.example.test", transport=transport) as http_client:
                  sunday_transport = HttpxTransport(http_client)
                  projects_client = ProjectsClient(sunday_transport)
                  events_client = EventsClient(sunday_transport)
                  operation = projects_client.get_project("project-1")

                  decoded_problem = operation.transport.problem_registry.decode(
                      {"type": "https://turnpost.example/problems/project-not-found"},
                      response_status=404,
                  )
                  assert isinstance(decoded_problem, ProjectNotFoundProblem)

                  request = operation.transport_request()
                  assert request.method == "GET"
                  assert request.url.path == "/projects/project-1"

                  response = await operation.response()
                  assert response.result.project_id == "project-1"
                  assert response.status == 200
                  assert str(response.content_type) == "application/json"
                  assert response.get_header("content-type") == "application/json"
                  assert response.get_headers("content-type") == ("application/json",)
                  assert response.decoded_header("X-Revision") == 7

                  project = await operation.execute()
                  assert project.project_id == "project-1"
                  assert project.name == "Roadmap"

                  assert await projects_client.find_project("missing").execute_or_none() is None

                  projects = await projects_client.list_projects(
                      ProjectQuery(tags=["one", "two"])
                  ).execute()
                  assert len(projects) == 1
                  assert projects[0].project_id == "project-1"
                  assert projects[0].name == "Roadmap"

                  update_operation = projects_client.update_project(
                      "project-1",
                      UpdateProjectRequest(display_name="Updated", from_commit_id=None),
                      revision_id="revision-1",
                      include_archived=True,
                      x_trace_id="trace-1",
                  )

                  updated_project = await update_operation.execute()
                  assert updated_project.project_id == "project-1"
                  assert updated_project.name == "Updated"

                  try:
                      projects_client.update_project(
                          "project-1",
                          UpdateProjectRequest(display_name="Updated", from_commit_id=None),
                      )
                  except TypeError:
                      pass
                  else:
                      raise AssertionError("required query parameter was accepted as omitted")

                  avatar = await projects_client.put_project_avatar(
                      "project-1",
                      b"avatar-bytes",
                      content_type="image/png",
                  ).execute()
                  assert avatar is None

                  try:
                      projects_client.put_project_avatar("project-1", b"avatar-bytes")
                  except TypeError:
                      pass
                  else:
                      raise AssertionError("required header parameter was accepted as omitted")

                  import_operation = projects_client.import_project_archive(
                      "project-1",
                      StreamingBody.bytes(b"archive-bytes"),
                  )
                  import_request = import_operation.transport_request()
                  assert import_request.method == "POST"
                  assert import_request.url.path == "/projects/project-1/archive"

                  import_id = await import_operation.execute()
                  assert import_id == "import-1"

                  revision_id = await projects_client.create_project_revision("project-1").execute()
                  assert revision_id == "revision-1"

                  await projects_client.put_payload(
                      ProjectView(projectId="project-1", name="Roadmap")
                  ).execute()
                  await projects_client.put_payload(b"payload-bytes").execute()
                  await projects_client.upload_multipart(
                      MultipartBody((MultipartPart("title", "Roadmap"),))
                  ).execute()
                  await projects_client.patch_project(
                      PatchDocument(
                          (PatchOperation(PatchOperationKind.REPLACE, "/name", "Updated"),)
                      )
                  ).execute()

                  stream = events_client.stream_project_events()
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
      "PythonClientRendererTest/projects.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/projects.py"),
    )
    assertPythonSnapshot(
      "PythonClientRendererTest/events.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/events.py"),
    )
  }
}
