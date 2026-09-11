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
import io.outfoxx.sunday.generator.GenerationException
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
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path

class PythonGeneratedOutputParityTest : PythonTest() {

  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `compiles remote schema resources`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val api = fixture.export(directory)
      for ((server, modules) in listOf(false to api.sundayModules(), true to api.litestarModules())) {
        assertTrue(
          compileModules(
            compiler,
            modules,
            importModules = listOf("parity_api.models"),
            smokeCode =
              """
              from parity_api.models import User, Node, Restrictions, Nullability, Pets, Cat, Cat2, Dog, BooleanValues
              from parity_api.models import BaseRecord, DocumentedRecord, RecordNode
              from parity_api.models import MappedPets, MappedCat, MappedDog
              assert issubclass(DocumentedRecord, BaseRecord)
              record = DocumentedRecord.model_validate({'id': 'one', 'detail': 'detail'})
              assert isinstance(record, BaseRecord) and record.id == 'one'
              assert BaseRecord.model_fields['id'].is_required()
              assert BaseRecord.model_fields['id'].description == 'Parent identifier'
              assert 'id' not in DocumentedRecord.__annotations__
              assert 'payload' not in DocumentedRecord.__annotations__
              assert BaseRecord.model_fields['payload'].annotation == str | None
              for payload in (None, 'value'):
                  assert DocumentedRecord.model_validate({'id': 'one', 'payload': payload}).payload == payload
              assert 'next' not in DocumentedRecord.__annotations__
              assert BaseRecord.model_fields['next'].annotation == RecordNode | None
              for next in (None, {'id': 'two', 'next': {'id': 'three'}}):
                  decoded = DocumentedRecord.model_validate({'id': 'one', 'detail': 'detail', 'next': next})
                  restored = DocumentedRecord.model_validate_json(decoded.model_dump_json())
                  if next is None:
                      assert restored.next is None
                  else:
                      assert type(restored.next) is RecordNode
                      assert restored.next.id == 'two' and restored.next.next.id == 'three'
              assert 'unrelated' in Cat.model_fields and 'lives' in Cat2.model_fields
              assert BooleanValues.model_fields['truth'].annotation.__value__ is object
              assert BooleanValues.model_fields['empty'].annotation.__value__ is object
              for value in (0, False, 'value', {'nested': True}):
                  decoded = BooleanValues.model_validate({'truth': value, 'empty': value})
                  assert decoded.truth == value and decoded.empty == value
              user = User.model_validate({"id": "one", "address": {"street": "Main"}, "node": None, "composedNode": None, "maybeAddress": None})
              assert user.node is None and user.composed_node is None
              assert Node.model_validate({"child": None}).child is None
              assert user.maybe_address is None
              assert not User.model_fields['node'].deprecated
              assert not (User.model_fields['node'].json_schema_extra or {}).get('readOnly', False)
              assert not User.model_fields['node'].description == 'Annotated node'
              assert User.model_validate({"id": "one", "address": {"street": "Main"}, "node": None, "composedNode": None, "maybeAddress": {"street": "Main"}}).maybe_address.street == "Main"
              from pydantic import ValidationError
              for animal, model in (({'kind': 'kitty', 'lives': 9}, MappedCat), ({'kind': 'hound', 'barks': True}, MappedDog)):
                  decoded = MappedPets.model_validate({'animal': animal})
                  assert type(decoded.animal) is model
                  assert decoded.model_dump(mode='json') == {'animal': animal}
                  assert type(MappedPets.model_validate_json(decoded.model_dump_json()).animal) is model
              try:
                  MappedPets.model_validate({'animal': {'kind': 'MappedCat', 'lives': 9}})
                  raise AssertionError('mapped discriminator accepted the generated model name')
              except ValidationError:
                  pass
              try:
                  DocumentedRecord.model_validate({'id': 'one', 'next': 42})
                  raise AssertionError('recursive reference accepted a number')
              except ValidationError:
                  pass
              for kind, model in (("Cat", Cat2), ("Dog", Dog)):
                  pets = Pets.model_validate({"animal": {"kind": kind}})
                  assert isinstance(pets.animal, model)
                  assert pets.model_dump(mode='json')['animal']['kind'] == kind
              try:
                  Pets.model_validate({"animal": {"kind": "Cat2"}})
                  raise AssertionError('generated model name became a wire value')
              except ValidationError:
                  pass
              for values in (None, ["valid"]):
                  assert Nullability.model_validate({"strictText": "valid", "values": values}).values == values
              try:
                  Nullability.model_validate({"strictText": None, "values": None})
                  raise AssertionError('constrained string accepted null')
              except ValidationError:
                  pass
              try:
                  User.model_validate({"id": "one", "address": {"street": "Main"}, "node": None, "composedNode": None, "maybeAddress": 42})
                  raise AssertionError('nullable address accepted a number')
              except ValidationError:
                  pass
              valid = {"address": {"street": "Main"}, "text": "hello", "state": "active"}
              assert Restrictions.model_validate(valid).text == "hello"
              for field in valid:
                  for excluded in (42, None):
                      try:
                          Restrictions.model_validate({**valid, field: excluded})
                          raise AssertionError(f'{field} accepted {excluded}')
                      except ValidationError:
                          pass
              """.trimIndent(),
          ),
        )
        val models = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/models.py")
        assertTrue(models.contains("Address"), models)
        assertTrue(models.contains("UserExtendedAddress"), models)
        assertTrue(models.contains("postal_code"), models)
        assertTrue(models.contains("class Node("), models)
        assertTrue(models.contains("node: Node | None"), models)
        assertTrue(models.contains("composed_node: Node | None"), models)
        assertTrue(models.contains("child: Node | None"), models)
        assertFalse(models.contains("class UserNode("), models)
        assertTrue(models.contains("class UserProfile("), models)
        assertTrue(models.contains("class UserProfile2("), models)
        assertTrue(models.contains("remote_value"), models)
        assertTrue(models.contains("local_value"), models)
        assertFalse(models.contains("class UserArbitrary("), models)
        assertFalse(models.contains("class UserNullableArbitrary("), models)
        assertTrue(models.contains("strict_text: str ="), models)
        assertFalse(models.contains("strict_text: str | None"), models)
        val client =
          CompiledGeneratedSources.source(
            GeneratedCodeLanguage.Python,
            "parity_api/references" +
              if (server) "_server.py" else ".py",
          )
        assertTrue(client.contains("= 20"), client)
      }
    }
  }

  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `RAML source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("raml/ir/craft-project.raml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.sundayModules(),
      "PythonGeneratedOutputParityTest/raml-sunday-projects.py",
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
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `OpenAPI source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/project-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.sundayModules(),
      "PythonGeneratedOutputParityTest/openapi-sunday-projects.py",
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
    val modules = api.sundayModules()

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
    assertTrue(modelSource.contains("value: object | None = Field(default=None)"), modelSource)
    assertTrue(
      modelSource.contains("documented: object | None = Field(default=None, description=\"Any documented value\")"),
      modelSource,
    )
    assertTrue(modelSource.contains("named: AnyJson | None = Field(default=None)"), modelSource)
    assertTrue(clientSource.contains("body: object"), clientSource)
    assertTrue(clientSource.contains("Operation[AnyJson, TransportRequestT, TransportResponseT]"), clientSource)
  }

  @Test
  fun `OpenAPI inline object properties beside conditional allOf compile in Python`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/inline-object-conditional-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modelsModule = PythonModelRenderer("parity_api").renderModels(api.models)
    val initModule = PythonModuleBuilder("parity_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("parity_api.models"),
      ),
    )

    val modelSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/models.py")
    assertTrue(modelSource.contains("class AllOfRequiredData(SundayModel):"), modelSource)
    assertTrue(modelSource.contains("value: str"), modelSource)
    assertTrue(
      modelSource.contains(
        "render_graph: VisualizationRenderGraphTaskSettledDataRenderGraph = Field(alias=\"renderGraph\")",
      ),
      modelSource,
    )
    assertTrue(modelSource.contains("graph_job_id: str = Field(alias=\"graphJobId\")"), modelSource)
    assertTrue(modelSource.contains("task_id: str = Field(alias=\"taskId\")"), modelSource)
    assertTrue(modelSource.contains("state: RenderGraphTaskSettledState"), modelSource)
    assertTrue(modelSource.contains("completed_count: int = Field(alias=\"completedCount\")"), modelSource)
    assertTrue(modelSource.contains("task_count: int = Field(alias=\"taskCount\")"), modelSource)
  }

  @Test
  fun `OpenAPI streaming request bodies emit Python streaming operations`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modules = api.sundayModules()

    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = modules.importModuleNames(),
      ),
    )
    val clientSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/streaming_request.py")

    assertTrue(clientSource.contains("body: StreamingBody"), clientSource)
    assertTrue(
      clientSource.contains("-> StreamingOperation[ImportAccepted, TransportRequestT, TransportResponseT]"),
      clientSource,
    )
    assertTrue(clientSource.contains("request_spec: RequestSpec[StreamingBody]"), clientSource)
    assertTrue(clientSource.contains("body=body"), clientSource)
    assertTrue(clientSource.contains("return StreamingOperation(self.transport, operation_spec)"), clientSource)
  }

  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `AsyncAPI source emits compile-backed client and server snapshots`(
    compiler: PythonCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)

    compileAndSnapshot(
      compiler,
      api.sundayModules(),
      "PythonGeneratedOutputParityTest/asyncapi-sunday-events.py",
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
  @RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
  fun `composed OpenAPI and AsyncAPI output passes client and server runtime smoke`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/composition-audit-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    compileAndSnapshot(
      compiler,
      api.sundayModules(aggregate = true),
      "PythonGeneratedOutputParityTest/composed-sunday-api.py",
      "parity_api/api.py",
      smokeCode = sundayEventSmokeCode,
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
  fun `composed event stream preserves HTTP query and header parameters`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    val modules = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri)).sundayModules()

    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = modules.importModuleNames(),
      ),
    )
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/events.py")

    assertTrue(source.contains("subscriber_id: str"), source)
    assertTrue(source.contains("last_event_id: str | None = None"), source)
    assertTrue(source.contains("name=\"subscriberId\""), source)
    assertTrue(source.contains("value=subscriber_id"), source)
    assertTrue(source.contains("location=ParameterLocation.QUERY"), source)
    assertTrue(source.contains("name=\"Last-Event-ID\""), source)
    assertTrue(source.contains("value=last_event_id"), source)
    assertTrue(source.contains("location=ParameterLocation.HEADER"), source)
  }

  @Test
  fun `Python targets omit broker-only AsyncAPI channels`(
    compiler: PythonCompiler,
    @ResourceUri("asyncapi/ir/http-and-broker-events.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modules = api.sundayModules()

    assertTrue(compileModules(compiler, modules, importModules = modules.importModuleNames()))
    val paths = modules.map { module -> module.path }
    assertTrue(paths.any { path -> path.endsWith("events.py") }, paths.toString())
    assertFalse(paths.any { path -> path.contains("platform") || path.contains("broker") }, paths.toString())
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/events.py")
    assertTrue(source.contains("stream_project_events"), source)
    assertFalse(source.contains("consume_platform_event"), source)

    val error =
      assertThrows<GenerationException> {
        PythonSundayIrGenerator(
          api,
          PythonGeneratorOptions(packageName = "parity_api", generateBrokerServices = true),
        ).generateModules(GeneratedTypeCategory.entries.toSet())
      }
    assertTrue(error.message!!.contains("not supported for Python/Sunday"), error.message)
  }

  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
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
        api.sundayModules(aggregate = true),
        importModules = listOf("parity_api.api"),
      ),
    )
    val clientSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "parity_api/api.py")
    assertFalse(clientSource.contains("from .import import"), clientSource)
    assertTrue(clientSource.contains("from .import_ import ImportClient"), clientSource)
    assertTrue(clientSource.contains("self.import_ = ImportClient("), clientSource)

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

  private fun GeneratedApi.sundayModules(aggregate: Boolean = false): List<PythonModule> =
    PythonSundayIrGenerator(
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
    assertNeutralSundayModules(modules)
    assertPythonSnapshot(
      snapshotPath,
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, compiledSourcePath),
    )
  }

  private fun assertNeutralSundayModules(modules: List<PythonModule>) {
    val clientModules = modules.filter { module -> module.source.contains("TransportRequestT") }
    if (clientModules.isEmpty()) return

    assertFalse(modules.any { module -> module.path.endsWith("runtime.py") }, modules.map { it.path }.toString())
    clientModules.forEach { module ->
      assertTrue(module.source.contains("from sunday import"), module.source)
      listOf("from .runtime", "import httpx", "from sunday.httpx", "from sunday.httpx_compat").forEach { forbidden ->
        assertFalse(
          module.source.contains(forbidden),
          "${module.path} contains forbidden import '$forbidden'\n${module.source}",
        )
      }
    }
  }

  private fun List<PythonModule>.importModuleNames(): List<String> =
    mapNotNull { module ->
      module.path
        .takeUnless { path -> path.endsWith("__init__.py") }
        ?.removeSuffix(".py")
        ?.replace('/', '.')
    }

  private val sundayEventSmokeCode: String =
    """
    import asyncio

    import httpx
    from sunday.httpx import HttpxTransport

    from parity_api.api import ParityAPI
    from parity_api.models import ProjectCreatedData


    class EventByteStream(httpx.AsyncByteStream):
        async def __aiter__(self):
            yield b'data: {"id":"event-1","type":"project.created","data":{"projectId":"project-1"}}\n\n'


    event_requests = 0


    def handler(request: httpx.Request) -> httpx.Response:
        global event_requests
        assert request.method == "GET"
        assert request.url.path == "/events"
        event_requests += 1
        if event_requests > 1:
            return httpx.Response(204)
        return httpx.Response(
            200,
            headers={"content-type": "text/event-stream"},
            stream=EventByteStream(),
        )


    async def main() -> None:
        transport = httpx.MockTransport(handler)
        async with httpx.AsyncClient(base_url="https://api.example.test", transport=transport) as http_client:
            api = ParityAPI(HttpxTransport(http_client))
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
