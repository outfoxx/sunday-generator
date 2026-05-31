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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class PythonGeneratedOutputParityTest : PythonTest() {

  @Test
  fun `RAML source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("raml/ir/craft-project.raml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.httpxModules(),
      "PythonGeneratedOutputParityTest/raml-httpx-projects.py",
      "parity_api/projects.py",
    )
    compileAndSnapshot(
      compiler,
      api.litestarModules(),
      "PythonGeneratedOutputParityTest/raml-litestar-projects_server.py",
      "parity_api/projects_server.py",
    )
  }

  @Test
  fun `OpenAPI source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/project-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.httpxModules(),
      "PythonGeneratedOutputParityTest/openapi-httpx-projects.py",
      "parity_api/projects.py",
    )
    compileAndSnapshot(
      compiler,
      api.litestarModules(),
      "PythonGeneratedOutputParityTest/openapi-litestar-projects_server.py",
      "parity_api/projects_server.py",
    )
  }

  @Test
  fun `OpenAPI empty schemas emit object typed Python models and clients`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modules = api.httpxModules()

    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = modules.importModuleNames(),
      ),
    )
    val modelSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/models.py")
    val clientSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/any_json.py")

    assertTrue(modelSource.contains("AnyJson = object"), modelSource)
    assertTrue(modelSource.contains("value: object | None = None"), modelSource)
    assertTrue(modelSource.contains("documented: object | None = None"), modelSource)
    assertTrue(modelSource.contains("named: AnyJson | None = None"), modelSource)
    assertTrue(clientSource.contains("body: object"), clientSource)
    assertTrue(clientSource.contains("Operation[AnyJson]"), clientSource)
  }

  @Test
  fun `OpenAPI streaming request bodies emit Python streaming operations`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modules = api.httpxModules()

    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = modules.importModuleNames(),
      ),
    )
    val clientSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/streaming_request.py")

    assertTrue(clientSource.contains("body: StreamingBody"), clientSource)
    assertTrue(clientSource.contains("-> StreamingOperation[ImportAccepted]"), clientSource)
    assertTrue(clientSource.contains("build_request=build_request"), clientSource)
    assertTrue(clientSource.contains("content=body.content()"), clientSource)
  }

  @Test
  fun `AsyncAPI source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.httpxModules(),
      "PythonGeneratedOutputParityTest/asyncapi-httpx-events.py",
      "parity_api/events.py",
    )
    compileAndSnapshot(
      compiler,
      api.litestarModules(),
      "PythonGeneratedOutputParityTest/asyncapi-litestar-events_server.py",
      "parity_api/events_server.py",
    )
  }

  @Test
  fun `composed OpenAPI and AsyncAPI output passes client and server runtime smoke`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/composition-audit-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    compileAndSnapshot(
      compiler,
      api.httpxModules(aggregate = true),
      "PythonGeneratedOutputParityTest/composed-httpx-api.py",
      "parity_api/api.py",
      smokeCode = httpxEventSmokeCode,
    )
    compileAndSnapshot(
      compiler,
      api.litestarModules(aggregate = true),
      "PythonGeneratedOutputParityTest/composed-litestar-api_server.py",
      "parity_api/api_server.py",
      smokeCode = litestarEventSmokeCode,
    )
  }

  @Test
  fun `reserved Python service names compile in aggregate client and server output`(compiler: PythonCompiler) {
    val api =
      GeneratedApi(
        name = "Keyword API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://keyword-api"),
        services =
          listOf(
            GeneratedService(
              name = "ImportService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "listImports",
                    method = "GET",
                    path = "/imports",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "listUsers",
                    method = "GET",
                    path = "/users",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
      )

    assertTrue(
      compileModules(
        compiler,
        api.httpxModules(aggregate = true),
        importModules = listOf("parity_api.api"),
      ),
    )
    val clientSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/api.py")
    assertFalse(clientSource.contains("from .import import"), clientSource)
    assertTrue(clientSource.contains("from .import_ import ImportClient"), clientSource)
    assertTrue(clientSource.contains("self.import_ = ImportClient(transport)"), clientSource)

    assertTrue(
      compileModules(
        compiler,
        api.litestarModules(aggregate = true),
        importModules = listOf("parity_api.api_server"),
      ),
    )
    val serverSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/api_server.py")
    assertTrue(serverSource.contains("from .import_server import ImportService, create_import_router"), serverSource)
    assertTrue(serverSource.contains("import_: ImportService"), serverSource)
    assertTrue(serverSource.contains("create_import_router(import_)"), serverSource)
  }

  private fun GeneratedApi.httpxModules(aggregate: Boolean = false): List<PythonModule> =
    PythonHttpxIrGenerator(
      this,
      PythonGeneratorOptions(
        packageName = "parity_api",
        aggregateServices = aggregate,
        aggregateServiceName = "ParityAPI",
      ),
    ).generateModules(GeneratedTypeCategory.entries.toSet())

  private fun GeneratedApi.litestarModules(aggregate: Boolean = false): List<PythonModule> =
    PythonLitestarIrGenerator(
      this,
      PythonGeneratorOptions(
        packageName = "parity_api",
        aggregateServices = aggregate,
        aggregateServiceName = "ParityAPI",
      ),
    ).generateModules(GeneratedTypeCategory.entries.toSet())

  private fun compileAndSnapshot(
    compiler: PythonCompiler,
    modules: List<PythonModule>,
    snapshotPath: String,
    compiledSourcePath: String,
    smokeCode: String? = null,
  ) {
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = modules.importModuleNames(),
        smokeCode = smokeCode,
      ),
    )
    assertPythonSnapshot(
      snapshotPath,
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, compiledSourcePath),
    )
  }

  private fun List<PythonModule>.importModuleNames(): List<String> =
    mapNotNull { module ->
      module.path
        .takeUnless { path -> path.endsWith("__init__.py") }
        ?.removeSuffix(".py")
        ?.replace('/', '.')
    }

  private val httpxEventSmokeCode: String =
    """
    import asyncio

    import httpx

    from parity_api.api import ParityAPI
    from parity_api.models import ProjectCreatedData


    class EventByteStream(httpx.AsyncByteStream):
        async def __aiter__(self):
            yield b'data: {"id":"event-1","type":"project.created","data":{"projectId":"project-1"}}\n\n'


    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "GET"
        assert request.url.path == "/events"
        return httpx.Response(
            200,
            headers={"content-type": "text/event-stream"},
            stream=EventByteStream(),
        )


    async def main() -> None:
        transport = httpx.MockTransport(handler)
        async with httpx.AsyncClient(base_url="https://api.example.test", transport=transport) as http_client:
            api = ParityAPI(http_client)
            events = [event async for event in api.events.stream_events()]

        assert len(events) == 1
        assert events[0].type == "project.created"
        assert isinstance(events[0].data, ProjectCreatedData)
        assert events[0].data.project_id == "project-1"


    asyncio.run(main())
    """.trimIndent()

  private val litestarEventSmokeCode: String =
    """
    from collections.abc import AsyncIterator

    from litestar import Litestar
    from litestar.plugins.pydantic import PydanticPlugin
    from litestar.testing import TestClient

    from parity_api.api_server import create_parity_api_router
    from parity_api.events_server import EventsService
    from parity_api.models import EventEnvelope, Project, ProjectCreatedData, User
    from parity_api.projects_server import ProjectsService
    from parity_api.users_server import UsersService


    class ProjectsImplementation:
        async def get_project(self, project_id: str) -> Project:
            return Project(id=project_id)


    class UsersImplementation:
        async def get_user(self, user_id: str) -> User:
            return User(id=user_id)


    class EventsImplementation:
        async def stream_events(self) -> AsyncIterator[EventEnvelope]:
            yield EventEnvelope(
                id="event-1",
                type="project.created",
                data=ProjectCreatedData(project_id="project-1"),
            )


    projects: ProjectsService = ProjectsImplementation()
    users: UsersService = UsersImplementation()
    events: EventsService = EventsImplementation()
    app = Litestar(
        route_handlers=[create_parity_api_router(projects, users, events)],
        plugins=[PydanticPlugin(prefer_alias=True)],
    )

    with TestClient(app=app) as client:
        response = client.get("/projects/project-1")

    assert response.status_code == 200
    assert response.json() == {"id": "project-1"}

    with TestClient(app=app) as client:
        response = client.get("/events")

    assert response.status_code == 200
    assert "project.created" in response.text
    assert "project-1" in response.text
    """.trimIndent()
}
