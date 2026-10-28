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
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
@Tag("models")
@Tag("responses")
class PythonAboutBlankProblemTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["sunday", "sunday-models", "sunday-owner", "litestar", "litestar-models", "litestar-owner"])
  fun `custom URI conflicts fail regardless of generation mode or output selection`(
    target: String,
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
  ) {
    val original = GeneratedApiIrExporter().export(source)
    val api =
      original.copy(
        models =
          original.models.map { model ->
            model.copy(
              properties =
                model.properties.map { property ->
                  if (property.name == "type" && property.allowedValues != null) {
                    property.copy(required = true, defaultValue = null, allowedValues = listOf("urn:example:shared"))
                  } else {
                    property
                  }
                },
            )
          },
      )
    val options = PythonGeneratorOptions(placement = pythonPlacement("test_api"))
    val selection =
      when {
        target.endsWith(
          "models",
        ) -> GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem))
        target.endsWith("owner") -> GeneratedOutputSelection(ownerUris = setOf(URI("file:///other.yaml")))
        else -> GeneratedOutputSelection()
      }
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        if (target.startsWith("litestar")) {
          PythonLitestarIrGenerator(api, options).generateModules(selection)
        } else {
          PythonSundayIrGenerator(api, options).generateModules(selection)
        }
      }
    for (part in listOf("urn:example:shared", "BadRequestProblem", "NotFoundProblem", "distinct fixed type URIs")) {
      assertTrue(error.message.orEmpty().contains(part), error.message)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "litestar"])
  fun `standard status models retain constraints while HTTP errors use generic problems`(
    target: String,
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
    compiler: PythonCompiler,
  ) {
    val original = GeneratedApiIrExporter().export(source)
    val api = original.copy(services = original.services.map { it.copy(name = "Example", group = null) })
    assertTrue(
      compileModules(
        compiler,
        if (target == "litestar") {
          PythonLitestarIrGenerator(
            api,
            PythonGeneratorOptions(placement = pythonPlacement("test_api")),
          ).generateModules()
        } else {
          PythonSundayIrGenerator(
            api,
            PythonGeneratorOptions(placement = pythonPlacement("test_api")),
          ).generateModules()
        },
        smokeCode =
          """
          import asyncio
          import httpx
          from pydantic import ValidationError
          from sunday import Problem
          from sunday.httpx import HttpxTransport
          from test_api.models_problems import BadRequestProblem, NotFoundProblem, FlexibleProblem, CustomProblem, RequiredProblem
          for cls, status, title in [(BadRequestProblem, 400, 'Bad Request'), (NotFoundProblem, 404, 'Not Found')]:
              value = cls(detail='Details', traceId='trace')
              assert value.status == status and value.title == title and value.type == 'about:blank'
              raw = value.model_dump(mode='json', by_alias=True)
              omitted = {key: val for key, val in raw.items() if key != 'type'}
              assert cls.model_validate(omitted).type == 'about:blank'
              for field, invalid in [('status', 500), ('title', 'Wrong'), ('type', 'https://example.com/wrong')]:
                  try:
                      cls.model_validate(dict(raw, **{field: invalid}))
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError(f'Accepted invalid {field}')
          omitted = dict(title='Error', status=400)
          assert FlexibleProblem.model_validate(omitted).type == 'about:blank'
          custom = dict(omitted, type='https://example.com/problems/custom')
          assert FlexibleProblem.model_validate(custom).type == custom['type']
          assert CustomProblem.model_validate(custom).type == custom['type']
          assert CustomProblem(title='Error', status=400).type == custom['type']
          assert RequiredProblem.model_validate(custom).type == custom['type']
          assert RequiredProblem.model_validate(omitted).type == 'about:blank'
          assert FlexibleProblem.model_validate(dict(omitted, type=None)).type == 'about:blank'
          for cls, raw in [(CustomProblem, omitted)]:
              try:
                  cls.model_validate(raw)
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f'Accepted invalid problem type: {cls.__name__} {raw}')
          async def check():
              from test_api.example import ExampleClient
              for status, title in [(400, 'Bad Request'), (404, 'Not Found'), (503, 'Service Unavailable')]:
                  for explicit in [False, True]:
                      raw = dict(status=status, title=title, detail='Details', traceId='trace', extra={'nested': [1, 2]})
                      if explicit:
                          raw['type'] = 'about:blank'
                      async with httpx.AsyncClient(base_url='https://example.test', transport=httpx.MockTransport(
                          lambda request: httpx.Response(status, json=raw, headers={'Content-Type': 'application/problem+json'})
                      )) as native:
                          async with HttpxTransport(native) as transport:
                              client = ExampleClient(transport)
                              try:
                                  await client.get_example().execute()
                              except Problem as caught:
                                  assert not isinstance(caught, (BadRequestProblem, NotFoundProblem))
                                  assert caught.type == 'about:blank'
                                  assert caught.status == status and caught.title == title and caught.detail == 'Details'
                                  encoded = caught.model_dump(mode='json', by_alias=True)
                                  assert encoded['traceId'] == 'trace' and encoded['extra'] == {'nested': [1, 2]}
                              else:
                                  raise AssertionError('Expected generic HTTP problem')
          if "$target" == "sunday":
              asyncio.run(check())
          """.trimIndent(),
      ),
    )
  }
}
