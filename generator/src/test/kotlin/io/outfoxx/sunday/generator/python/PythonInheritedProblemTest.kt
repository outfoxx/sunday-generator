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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("models")
@Tag("responses")
class PythonInheritedProblemTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "asyncapi", "composed"])
  fun `source-schema exceptions compile for every supported frontend`(
    frontend: String,
    @ResourceUri("raml/ir/inherited-problem.raml") raml: URI,
    @ResourceUri("asyncapi/ir/inherited-problem.yaml") asyncapi: URI,
    @ResourceUri("openapi/ir/inherited-problem.yaml") openapi: URI,
    @TempDir directory: Path,
    compiler: PythonCompiler,
  ) {
    val sources =
      when (frontend) {
        "raml" -> listOf(raml)
        "asyncapi" -> listOf(asyncapi)
        else -> {
          val extra = directory.resolve("extra.raml")
          extra.writeText("#%RAML 1.0\ntitle: Inherited problems\ntypes:\n  Marker: string\n")
          listOf(openapi, extra.toUri())
        }
      }
    val api = GeneratedApiIrExporter().export(sources)
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(api, PythonGeneratorOptions(placement = pythonPlacement("test_api"))).generateModules(),
        smokeCode =
          """
          import json
          from sunday import Problem, ProblemRegistry
          from test_api.models_problems import QueuedProblem, RebuildingProblem, register_problems
          raw = json.loads('''$INHERITED_PROBLEM_JSON''')
          value = QueuedProblem(**raw)
          assert isinstance(value, Problem)
          assert isinstance(value, RebuildingProblem)
          assert value.retry_after_seconds == 1
          registry = ProblemRegistry()
          register_problems(registry)
          if "$frontend" == "raml":
              registry.register_problem(raw['type'], QueuedProblem)
          decoded = registry.decode(raw)
          assert isinstance(decoded, QueuedProblem)
          assert decoded.model_dump(mode='json', by_alias=True) == raw
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inherited problem fields survive construction decoding and encoding`(
    nullableType: Boolean,
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
    compiler: PythonCompiler,
  ) {
    val original = GeneratedApiIrExporter().export(listOf(source))
    val api =
      if (!nullableType) {
        original
      } else {
        original.copy(
          models =
            original.models.map { model ->
              if (model.name != "Problem") {
                model
              } else {
                model.copy(
                  properties =
                    model.properties.map { property ->
                      if (property.name !=
                        "type"
                      ) {
                        property
                      } else {
                        property.copy(required = false, type = property.type.copy(nullable = true))
                      }
                    },
                )
              }
            },
        )
      }
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(
          api.copy(services = listOf(api.services.first().copy(name = "Resources", group = null))),
          PythonGeneratorOptions(placement = pythonPlacement("test_api")),
        ).generateModules(),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          import asyncio
          import httpx
          from sunday.httpx import HttpxTransport
          from test_api.resources import ResourcesClient
          from pydantic import ValidationError
          from sunday import ProblemRegistry, Problem
          from test_api.models import Metadata
          from test_api.models_problems import QueuedProblem, RebuildingProblem, register_problems
          raw = json.loads('''$INHERITED_PROBLEM_JSON''')
          if "$nullableType" == "true":
              from test_api.models_problems import Problem as BaseProblem
              base = BaseProblem(title="Failure", status=400)
              assert base.type == "about:blank"
              assert base.payload.type is None
              assert base.model_dump(mode='json')['type'] is None
          value = QueuedProblem(detail='Resource A is rebuilding', traceId='trace-A',
              resourceKey='A', nullableParent=None, nullableChild=None, metadata=Metadata(region='west'), queuePosition=2)
          assert isinstance(value, RebuildingProblem)
          assert isinstance(value, Problem)
          assert value.resource_key == 'A'
          assert value.metadata.region == 'west'
          registry = ProblemRegistry()
          register_problems(registry)
          try:
              raise registry.decode(raw, response_status=409)
          except RebuildingProblem as caught:
              assert isinstance(caught, QueuedProblem)
              assert caught.model_dump(mode='json', by_alias=True) == raw
          else:
              raise AssertionError('Problem was not raised')
          assert value.model_dump(mode='json', by_alias=True) == raw
          assert QueuedProblem.model_validate(raw).model_dump(mode='json', by_alias=True) == raw
          for invalid in [dict(raw, resourceKey=''), dict(raw, retryAfterSeconds=2), dict(raw, queuePosition=0),
              dict(raw, type='https://example.com/wrong'), {k: v for k, v in raw.items() if k != 'nullableChild'}]:
              try:
                  QueuedProblem.model_validate(invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f'Accepted invalid problem: {invalid}')
          async def http_problem():
              async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(
                  lambda request: httpx.Response(409, json=raw, headers={"Content-Type": "application/problem+json"})
              )) as native:
                  async with HttpxTransport(native) as transport:
                      client = ResourcesClient(transport)
                      try:
                          await client.read_resource().execute()
                      except RebuildingProblem as caught:
                          assert isinstance(caught, QueuedProblem)
                          assert caught.model_dump(mode='json', by_alias=True) == raw
                      else:
                          raise AssertionError('HTTP problem was not raised')
          asyncio.run(http_problem())
          for invalid in [dict(raw, resourceKey=''), dict(raw, queuePosition=0)]:
              try:
                  QueuedProblem(**invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f'Constructed invalid problem: {invalid}')
          """.trimIndent(),
      ),
    )
  }
}
