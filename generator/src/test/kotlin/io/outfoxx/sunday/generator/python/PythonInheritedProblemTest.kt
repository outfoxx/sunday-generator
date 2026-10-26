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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.tools.TEAM_NOT_FOUND_JSON
import io.outfoxx.sunday.generator.tools.USER_NOT_FOUND_JSON
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("models")
@Tag("responses")
class PythonInheritedProblemTest : PythonTest() {
  @ParameterizedTest
  @CsvSource("sunday,false", "sunday,true", "litestar,false", "litestar,true")
  fun `problem enum defaults resolve without changing shared enum uses`(
    target: String,
    multipleValues: Boolean,
    @ResourceUri("openapi/ir/problem-type-enum.yaml") source: URI,
    @TempDir directory: Path,
    compiler: PythonCompiler,
  ) {
    val specification = directory.resolve("problems.yaml")
    specification.writeText(
      source.toURL().readText().let {
        if (multipleValues) it.replace("enum: [missing]", "enum: [missing, unavailable]") else it
      },
    )
    val api = GeneratedApiIrExporter().export(specification.toUri())
    val options =
      PythonGeneratorOptions(placement = pythonPlacement("test_api"), defaultProblemBaseUri = "https://example.com/")
    val modules =
      if (target == "litestar") {
        PythonLitestarIrGenerator(api, options).generateModules()
      } else {
        PythonSundayIrGenerator(api, options).generateModules()
      }
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from pydantic import ValidationError
          from test_api.models import Problem, ChildProblem, Ordinary, ProblemType
          from test_api.models_problems import ChildProblem as ChildError
          expected = 'https://example.com/problems/missing'
          for cls in [Problem, ChildProblem]:
              value = cls(type=expected)
              assert value.model_dump(mode='json', by_alias=True)['type'] == expected
              assert value.model_dump(mode='json', by_alias=True)['code'] == 'missing'
              assert cls.model_validate(value.model_dump()).model_dump() == value.model_dump()
              if "$multipleValues" == "true":
                  override = cls(type='https://example.com/problems/unavailable')
                  assert override.model_dump(mode='json')['type'] == 'https://example.com/problems/unavailable'
              for invalid in ['missing', 'https://example.com/problems/unknown']:
                  try:
                      cls(type=invalid)
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError('Invalid problem type was accepted')
          assert Ordinary().model_dump(mode='json')['type'] == 'missing'
          assert ProblemType('missing').value == 'missing'
          try:
              raise ChildError()
          except ChildError as error:
              assert error.model_dump(mode='json', by_alias=True)['type'] == expected
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `problem extensions cannot shadow wrapper payload members`(
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") source: URI,
    compiler: PythonCompiler,
  ) {
    val original = GeneratedApiIrExporter().export(source)
    val api =
      original.copy(
        models =
          original.models.map { model ->
            val extension =
              when (model.name) {
                "RebuildingProblem" -> "payload"
                "QueuedProblem" -> "payloadType"
                else -> null
              }
            if (extension == null) {
              model
            } else {
              model.copy(
                properties =
                  model.properties +
                    GeneratedModelProperty(extension, GeneratedTypeRef.scalar("string"), required = true),
              )
            }
          },
      )
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(
          api,
          PythonGeneratorOptions(
            placement = pythonPlacement("test_api"),
            defaultProblemBaseUri = "https://example.com/",
          ),
        ).generateModules(),
        smokeCode =
          """
          import json
          from sunday import ProblemRegistry
          from test_api.models import QueuedProblem as QueuedPayload
          from test_api.models_problems import QueuedProblem, RebuildingProblem, register_problems
          raw = dict(json.loads('''$INHERITED_PROBLEM_JSON'''), payload='parent extension', payloadType='child extension')
          payload = QueuedPayload.model_validate(raw)
          value = QueuedProblem(payload=payload)
          assert value.payload is payload
          assert value.payload_ == 'parent extension'
          assert value.payload_type_ == 'child extension'
          assert value.payload_type is QueuedPayload
          assert value.resource_key == 'A'
          assert value.model_dump(mode='json', by_alias=True) == raw
          assert QueuedProblem.model_validate(raw).payload_ == 'parent extension'
          parent = RebuildingProblem.model_validate(raw)
          assert parent.payload_ == 'parent extension'
          registry = ProblemRegistry()
          register_problems(registry)
          try:
              raise registry.decode(raw)
          except RebuildingProblem as caught:
              assert isinstance(caught, QueuedProblem)
              assert caught.payload_ == 'parent extension'
              assert caught.payload_type_ == 'child extension'
              assert caught.model_dump(mode='json', by_alias=True) == raw
          else:
              raise AssertionError('Problem was not raised')
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @CsvSource(
    "raml,sunday",
    "asyncapi,sunday",
    "composed,sunday",
    "raml,litestar",
    "asyncapi,litestar",
    "composed,litestar",
  )
  fun `source-schema exceptions compile for every supported frontend`(
    frontend: String,
    target: String,
    @ResourceUri("raml/ir/inherited-problem.raml") raml: URI,
    @ResourceUri("asyncapi/ir/inherited-problem.yaml") asyncapi: URI,
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") openapi: URI,
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
    val options =
      PythonGeneratorOptions(placement = pythonPlacement("test_api"), defaultProblemBaseUri = "https://example.com/")
    val modules =
      if (target == "litestar") {
        PythonLitestarIrGenerator(api, options).generateModules(
          GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
        )
      } else {
        PythonSundayIrGenerator(api, options).generateModules()
      }
    assertTrue(
      compileModules(
        compiler,
        modules,
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
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") source: URI,
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
                        property.copy(
                          required = false,
                          defaultValue = "about:blank",
                          type = property.type.copy(nullable = true),
                        )
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
          PythonGeneratorOptions(
            placement = pythonPlacement("test_api"),
            defaultProblemBaseUri = "https://example.com/",
          ),
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
              base = BaseProblem(type=None, title="Failure", status=400)
              assert base.type == "about:blank"
              assert base.payload.type is None
              assert base.model_dump(mode='json')['type'] is None
          value = QueuedProblem(detail='Resource A is rebuilding', traceId='trace-A',
              resourceKey='A', nullableParent=None, nullableChild=None, metadata=Metadata(region='west'), queuePosition=2)
          assert isinstance(value, RebuildingProblem)
          assert isinstance(value, Problem)
          from test_api.models_problems import DefaultedProblem
          base_defaults = DefaultedProblem()
          assert base_defaults.detail == 'Something bad happened'
          assert base_defaults.enabled is False and base_defaults.count == 0 and base_defaults.note == 'fallback'
          assert str(base_defaults.location) == 'https://example.com/default' and base_defaults.revision == 1
          assert DefaultedProblem(enabled=True, count=3, note=None).note is None
          defaulted = QueuedProblem(nullableParent=None, nullableChild=None, metadata=Metadata(region='west'))
          assert defaulted.model_dump(mode='json', by_alias=True) == raw
          overridden = QueuedProblem(detail='Override', trace_id='trace-B', resource_key='B', queue_position=3,
              nullableChild=None, metadata=Metadata(region='east'))
          assert overridden.detail == 'Override' and overridden.trace_id == 'trace-B'
          assert overridden.resource_key == 'B' and overridden.queue_position == 3
          for field, invalid in [('retry_after_seconds', 2), ('queue_name', 'wrong'), ('type', 'https://example.com/wrong')]:
              try:
                  setattr(value.payload, field, invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f'Mutable constant: {field}')
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
              dict(raw, type='https://example.com/wrong'), dict(raw, detail=None),
              *[{k: v for k, v in raw.items() if k != field} for field in ['type', 'nullableChild', 'resourceKey', 'queuePosition']]]:
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
          from pydantic import TypeAdapter
          from test_api.models import NotFoundProblems
          from test_api.models_problems import TeamNotFoundProblem, UserNotFoundProblem, NotFoundProblem
          union = TypeAdapter(NotFoundProblems)
          async def missing_problems():
              for raw, cls in [(json.loads('''$TEAM_NOT_FOUND_JSON'''), TeamNotFoundProblem),
                               (json.loads('''$USER_NOT_FOUND_JSON'''), UserNotFoundProblem)]:
                  value = cls(nullableParent=None)
                  assert isinstance(value, NotFoundProblem) and isinstance(value, Problem)
                  assert value.model_dump(mode='json', by_alias=True) == raw
                  assert union.dump_python(union.validate_python(raw), mode='json', by_alias=True) == raw
                  overridden = cls(detail='Override', resourceId='123')
                  assert overridden.detail == 'Override' and overridden.resource_id == '123'
                  for invalid in [dict(raw, type='wrong'), dict(raw, status=400), dict(raw, **{'error-code': 'wrong'}),
                                  {k: v for k, v in raw.items() if k != 'resourceId'}]:
                      try:
                          union.validate_python(invalid)
                      except ValidationError:
                          pass
                      else:
                          raise AssertionError(f'Accepted invalid union problem: {invalid}')
                  try:
                      value.payload.error_code = 'changed'
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError('Mutable inherited constant')
                  async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(
                      lambda request: httpx.Response(404, json=raw, headers={"Content-Type": "application/problem+json"})
                  )) as native:
                      async with HttpxTransport(native) as transport:
                          client = ResourcesClient(transport)
                          for request in [client.read_missing('test'), client.read_missing_named('test')]:
                              try:
                                  await request.execute()
                              except NotFoundProblem as caught:
                                  assert isinstance(caught, cls)
                                  assert caught.model_dump(mode='json', by_alias=True) == raw
                              else:
                                  raise AssertionError('404 problem was not raised')
          asyncio.run(missing_problems())
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
