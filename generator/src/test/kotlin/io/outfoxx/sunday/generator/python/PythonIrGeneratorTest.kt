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
import io.outfoxx.sunday.generator.ir.GeneratedMedia
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonIrGeneratorTest : PythonTest() {

  @Test
  fun `generates compileable aggregate Sunday modules from IR`(compiler: PythonCompiler) {
    val generator =
      PythonSundayIrGenerator(
        apiFixture(),
        PythonGeneratorOptions(
          aggregateServices = true,
          aggregateServiceName = "CraftAPI",
        ),
      )
    val modules = generator.generateModules(GeneratedTypeCategory.entries.toSet())

    assertThat(
      modules.map { module -> module.path },
      contains(
        "craft_api/__init__.py",
        "craft_api/models.py",
        "craft_api/problems.py",
        "craft_api/projects.py",
        "craft_api/users.py",
        "craft_api/api.py",
      ),
    )
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("craft_api.api"),
        smokeCode =
          """
          import asyncio
          from collections.abc import AsyncIterator, Callable, Sequence
          from dataclasses import dataclass
          from importlib.metadata import distributions
          from types import TracebackType
          from typing import Any
          from uuid import UUID

          from sunday import (
              BaseTransport,
              EventSource,
              EventSourceState,
              EventStream,
              EventStreamOptions,
              OperationResponse,
              Problem,
              RequestSpec,
              ResponseHeaders,
              ResponseSpec,
              ServerSentEvent,
          )

          from craft_api.api import CraftAPI
          from craft_api.models import ProjectView


          @dataclass(frozen=True)
          class NativeRequest:
              spec: RequestSpec[Any]


          @dataclass(frozen=True)
          class NativeResponse:
              request: NativeRequest


          class EmptyEventStream[EventT]:
              def __aiter__(self) -> AsyncIterator[EventT]:
                  return self.events()

              async def events(self) -> AsyncIterator[EventT]:
                  if False:
                      yield

              async def aclose(self) -> None:
                  pass

              async def __aenter__(self) -> "EmptyEventStream[EventT]":
                  return self

              async def __aexit__(
                  self,
                  exc_type: type[BaseException] | None,
                  exc_value: BaseException | None,
                  traceback: TracebackType | None,
              ) -> None:
                  del exc_type, exc_value, traceback


          class EmptyEventSource:
              ready_state = EventSourceState.CLOSED
              retry_time = 0.5
              on_open: Callable[[], None] | None = None
              on_error: Callable[[BaseException | None], None] | None = None
              on_message: Callable[[ServerSentEvent], None] | None = None

              def add_event_listener(self, event: str, handler: Callable[[ServerSentEvent], None]) -> UUID:
                  del event, handler
                  return UUID(int=0)

              def remove_event_listener(self, event: str, listener: UUID) -> None:
                  del event, listener

              def connect(self) -> None:
                  pass

              def close(self) -> None:
                  pass


          class FakeTransport(BaseTransport[NativeRequest, NativeResponse]):
              def register_problem(self, type_uri: str, problem_type: type[Problem]) -> None:
                  del type_uri, problem_type

              async def _prepare_request(self, spec: RequestSpec[Any]) -> NativeRequest:
                  return NativeRequest(spec)

              async def _send(self, request: NativeRequest, *, stream: bool = False) -> NativeResponse:
                  del stream
                  return NativeResponse(request)

              async def _decode_response(
                  self,
                  response: NativeResponse,
                  responses: Sequence[ResponseSpec[Any]],
              ) -> OperationResponse[Any, NativeResponse]:
                  del responses
                  return OperationResponse(
                      ProjectView(projectId="project-1"),
                      response,
                      200,
                      ResponseHeaders(()),
                  )

              def event_stream[EventT](
                  self,
                  spec: RequestSpec[None],
                  decoder: Callable[[ServerSentEvent], EventT | None],
                  *,
                  options: EventStreamOptions | None = None,
              ) -> EventStream[EventT]:
                  del spec, decoder, options
                  return EmptyEventStream()

              def event_source(
                  self,
                  spec: RequestSpec[None],
                  *,
                  options: EventStreamOptions | None = None,
              ) -> EventSource:
                  del spec, options
                  return EmptyEventSource()

              async def aclose(self) -> None:
                  pass

              async def __aenter__(self) -> "FakeTransport":
                  return self

              async def __aexit__(
                  self,
                  exc_type: type[BaseException] | None,
                  exc_value: BaseException | None,
                  traceback: TracebackType | None,
              ) -> None:
                  del exc_type, exc_value, traceback


          async def main() -> None:
              installed = {distribution.metadata["Name"].lower() for distribution in distributions()}
              assert "httpx" not in installed
              assert "anyio" not in installed

              api = CraftAPI(FakeTransport())
              assert api.projects.transport is api.transport
              assert api.users.transport is api.transport
              assert tuple(str(value) for value in api.default_accept_types) == ("application/json",)
              operation = api.projects.get_project("project-1")
              request = await operation.transport_request()
              response = await operation.transport_response()
              project = await operation.execute()

              assert isinstance(request, NativeRequest)
              assert isinstance(response, NativeResponse)
              assert project.project_id == "project-1"

          asyncio.run(main())
          """.trimIndent(),
      ),
    )
    assertTrue(modules.none { module -> module.path.endsWith("runtime.py") })
    assertTrue(modules.none { module -> module.source.contains(".runtime") || module.source.contains("httpx") })
    assertTrue(
      modules.none { module ->
        module.source.contains("prepare_request") ||
          module.source.contains("decode_response") ||
          module.source.contains("self._transport")
      },
    )
  }

  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `generates compileable aggregate Litestar modules from IR`(compiler: PythonCompiler) {
    val generator =
      PythonLitestarIrGenerator(
        apiFixture(),
        PythonGeneratorOptions(
          aggregateServices = true,
          aggregateServiceName = "CraftAPI",
        ),
      )
    val modules = generator.generateModules(GeneratedTypeCategory.entries.toSet())

    assertThat(
      modules.map { module -> module.path },
      contains(
        "craft_api/__init__.py",
        "craft_api/models.py",
        "craft_api/problems.py",
        "craft_api/projects_server.py",
        "craft_api/users_server.py",
        "craft_api/api_server.py",
      ),
    )
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("craft_api.api_server"),
      ),
    )
  }

  private fun apiFixture(): GeneratedApi =
    GeneratedApi(
      name = "Craft API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "craft.yaml"),
      media = GeneratedMedia(response = listOf("application/json")),
      models =
        listOf(
          GeneratedModel(
            name = "ProjectView",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
          GeneratedModel(
            name = "UserView",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
        ),
      services =
        listOf(
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
                      GeneratedResponse(status = 200, type = GeneratedTypeRef.named("ProjectView")),
                    ),
                ),
              ),
          ),
          GeneratedService(
            name = "Users",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getUser",
                  method = "GET",
                  path = "/users/{userId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(status = 200, type = GeneratedTypeRef.named("UserView")),
                    ),
                ),
              ),
          ),
        ),
    )
}
