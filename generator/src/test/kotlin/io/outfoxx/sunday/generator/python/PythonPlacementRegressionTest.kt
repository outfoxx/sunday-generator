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
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.PlacementFixture
import io.outfoxx.sunday.generator.tools.PlacementIdentityFixture
import io.outfoxx.sunday.generator.tools.PlacementNativeFixture
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.PlacementScopeFixture
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonPlacementRegressionTest : PythonTest() {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `native problem relocations compile with direct placement`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementScopeFixture.problems(directory, kind)
    val smoke = "from problem_sdk.errors import FailureProblem\nassert FailureProblem().status == 400"
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native relocation scopes compile with service defaults and exact model overrides`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementScopeFixture(directory, kind).api
    val child =
      api.models
        .single { model -> model.properties.any { it.name == "value" } }
        .name.pythonTypeName
    val smoke =
      """
      from exact_sdk.models import $child
      value = $child.model_validate({"value": "relocated"})
      assert $child.model_validate_json(value.model_dump_json()).value == "relocated"
      """.trimIndent()
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate operations preserve behavior through composition`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementNativeFixture(directory).duplicateOperations(kind, payloads = true)
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules()))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `recomposed IR preserves contribution reference identities`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val modules = PythonSundayIrGenerator(PlacementIdentityFixture(directory, kind).recomposed()).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from first.models import Foo, Wrapper as First
          from second.models import Wrapper as Second
          for wrapper in (First, Second):
              value = wrapper.model_validate({"foo": {"value": "original"}})
              assert isinstance(value.foo, Foo)
              assert value.model_dump(mode="json") == {"foo": {"value": "original"}}
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  @RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
  fun `service imports distinguish flattened module names and problem classes`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementIdentityFixture(directory, kind).pythonImports()
    val modules = PythonSundayIrGenerator(api).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import httpx
          from sunday.httpx import HttpxTransport
          from client.api import ExampleClient
          from a.b_c import Record as First
          from a_b.c import Record as Second
          from first.errors import Missing as FirstMissing
          from second.errors import Missing as SecondMissing

          async def main():
              failure = False
              swapped = False
              def handler(request):
                  first = request.url.path == '/first'
                  if failure:
                      owner = 'first' if first != swapped else 'second'
                      return httpx.Response(404, headers={'content-type': 'application/problem+json'},
                          json={'type': f'https://example.test/{owner}/missing', 'status': 404})
                  return httpx.Response(200, json={'value': 'text' if first else 42})
              async with httpx.AsyncClient(base_url='https://example.test', transport=httpx.MockTransport(handler)) as http:
                  client = ExampleClient(HttpxTransport(http))
                  first = await client.read_first().execute()
                  second = await client.read_second().execute()
                  assert isinstance(first, First) and first.value == 'text'
                  assert isinstance(second, Second) and second.value == 42
                  failure = True
                  assert await client.read_first().execute_or_none() is None
                  assert await client.read_second().execute_or_none() is None
                  swapped = True
                  for operation, problem in [(client.read_first(), SecondMissing), (client.read_second(), FirstMissing)]:
                      try:
                          await operation.execute_or_none()
                      except problem:
                          pass
                      else:
                          raise AssertionError('nullified another declaration')
          asyncio.run(main())
          """.trimIndent(),
      ),
    )
    val server =
      api.copy(
        services =
          api.services.map { service ->
            service.copy(operations = service.operations.map { it.copy(nullify = null) })
          },
      )
    assertTrue(
      compileModules(
        compiler,
        PythonLitestarIrGenerator(server).generateModules(),
        smokeCode =
          """
          from typing import get_type_hints, get_args
          from client.api import ExampleService
          from a.b_c import Record as First
          from a_b.c import Record as Second
          assert get_args(get_type_hints(ExampleService.read_first)['return'])[0] is First
          assert get_args(get_type_hints(ExampleService.read_second)['return'])[0] is Second
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `same named patch pairs retain source bindings`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val modules = PythonSundayIrGenerator(PlacementRegressionFixture(directory, kind).duplicates()).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from first.models import Record as First, RecordPatch as FirstPatch, Detail as FirstDetail
          from second.models import Record as Second, RecordPatch as SecondPatch, Detail as SecondDetail
          for model, patch, detail in [(First, FirstPatch, FirstDetail), (Second, SecondPatch, SecondDetail)]:
              value = model.model_validate({"value": "before", "detail": {"label": "nested"}})
              changed = value.merge(patch.model_validate({"value": "after"}))
              assert changed.value == "after"
              assert isinstance(changed.detail, detail)
              assert isinstance(value.patch(), patch)
              assert changed.model_dump(mode="json") == {"value": "after", "detail": {"label": "nested"}}
          assert First is not Second
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `initializer module retains definitions and canonical import identity`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val owner = directory.resolve(if (kind == "raml") "common.raml" else "common.yaml").toUri().toString()
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              owner,
              placement = GeneratedPlacement(python = GeneratedPlacement.Scope(module = "__init__")),
            ),
          ),
      )
    val modules = PythonSundayIrGenerator(api.copy(placementConfiguration = config)).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from example_shared import Shared
          from example_consumer.models import Item
          value = Item.model_validate({"shared": "one"})
          assert value.shared is Shared.ONE
          assert value.model_dump(mode="json") == {"shared": "one"}
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `local and imported declarations keep separate identities`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val modules =
      PythonSundayIrGenerator(
        PlacementRegressionFixture(directory, kind).localAndImported(),
      ).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from first.models import ${if (kind == "raml") "Record" else "Record2"} as Imported
          from second.models import Record as Local, Holder
          value = Holder.model_validate({"local": {"value": "local"}, "imported": {"value": "external"}})
          assert type(value.local) is Local
          assert type(value.imported) is Imported
          assert Local is not Imported
          assert value.model_dump(mode="json") == {"local": {"value": "local"}, "imported": {"value": "external"}}
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `imported base classes are available when ordering local declarations`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).inheritance()
    val smoke =
      """
      from example.base import Base
      from example.child import Child
      value = Child.model_validate({"next": {}, "child": {"next": {}}})
      assert isinstance(value, Base)
      assert type(value.next) is Base
      assert type(value.child) is Child
      assert Child.model_validate_json(value.model_dump_json()) == value
      """.trimIndent()
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "composed"])
  fun `service selection includes anonymous payload descendants`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).inline()
    val selection = GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service))
    val child = api.models.single { it.properties.any { property -> property.name == "value" } }
    val smoke =
      """
      from placement.models import ${child.name}
      assert ${child.name}.model_validate({"value": "selected"}).value == "selected"
      """.trimIndent()
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(selection), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(selection), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `placement permits soft keywords and rejects hard keywords`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    fun fixture(module: String) =
      PlacementRegressionFixture(directory, kind).models(
        "Record: {type: object, properties: {value: string}}",
        "Record: {type: object, required: [value], properties: {value: {type: string}}}",
        "python: {package: sdk, module: $module}",
      )
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(fixture("match.case.type")).generateModules(),
        smokeCode =
          """
          from sdk.match.case.type import Record
          assert Record.model_validate({"value": "ok"}).value == "ok"
          """.trimIndent(),
      ),
    )
    for (keyword in listOf("from", "class", "async", "outer.None")) {
      val failure = assertThrows(IllegalArgumentException::class.java) { fixture(keyword) }
      assertTrue(failure.message!!.contains("not a valid python placement"))
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacement(python = GeneratedPlacement.Scope(packageName = "sdk.$keyword"))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `module package shadowing is rejected before writing output`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    fun fixture(second: String) =
      PlacementRegressionFixture(directory, kind).models(
        "First: {type: string, enum: [one, two], (sunday.placement): {python: {module: models}}}\nSecond: {type: string, enum: [three, four], (sunday.placement): {python: {module: $second}}}",
        "First: {type: string, enum: [one, two], x-sunday-placement: {python: {module: models}}}\nSecond: {type: string, enum: [three, four], x-sunday-placement: {python: {module: $second}}}",
        "python: {package: sdk}",
      )
    val valid = fixture("other.models")
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(valid).generateModules(),
        smokeCode =
          """
          from sdk.models import First
          from sdk.other.models import Second
          assert First.ONE.value == 'one'
          assert Second.THREE.value == 'three'
          """.trimIndent(),
      ),
    )
    val invalid = fixture("models.other")
    val generators =
      listOf(
        { PythonSundayIrGenerator(invalid).generateModules() },
        { PythonLitestarIrGenerator(invalid).generateModules() },
      )
    for (generate in generators) {
      val failure = assertThrows(IllegalArgumentException::class.java) { generate() }
      assertTrue(failure.message!!.contains("sdk/models.py"))
      assertTrue(failure.message!!.contains("sdk/models/other.py"))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `services can share the configuration module`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api =
      PlacementRegressionFixture(directory, kind)
        .service("{python: {package: sdk, module: config}}")
        .copy(
          placementConfiguration =
            GeneratedPlacementConfiguration(
              defaults = GeneratedPlacement(python = GeneratedPlacement.Scope(packageName = "sdk")),
            ),
        )
    assertTrue(
      compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), importModules = listOf("sdk.config")),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced composed declarations use invocation package defaults`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).distinctDefaults()
    val smoke =
      """
      from first.models import First
      from defaults.models import Second
      assert First(value='a').value == 'a'
      assert Second(value='b').value == 'b'
      """.trimIndent()
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced foreign declarations use the API derived package`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api =
      PlacementRegressionFixture(
        directory,
        kind,
      ).distinctDefaults().copy(placementConfiguration = GeneratedPlacementConfiguration())
    val smoke =
      """
      from first.models import First
      from placement.models import Second
      assert First(value='a').value == 'a'
      assert Second(value='b').value == 'b'
      """.trimIndent()
    assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(), smokeCode = smoke))
    assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(), smokeCode = smoke))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `excluding any container removes nested declaration output`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).nestedSelection()
    for (excluded in listOf<String?>(null, "Root", "Middle")) {
      val owners =
        api.models
          .filter { it.name != excluded }
          .map { java.net.URI(it.source.location) }
          .toSet()
      val selection =
        io.outfoxx.sunday.generator
          .GeneratedOutputSelection(ownerUris = owners)
      val smoke =
        """
        import selection.models as models
        assert models.Survivor(value='ok').value == 'ok'
        assert hasattr(models, 'Leaf') == ${if (excluded == null) "True" else "False"}
        assert hasattr(models, 'Middle') == ${if (excluded == null) "True" else "False"}
        assert hasattr(models, 'Root') == ${if (excluded == "Root") "False" else "True"}
        """.trimIndent()
      assertTrue(compileModules(compiler, PythonSundayIrGenerator(api).generateModules(selection), smokeCode = smoke))
      assertTrue(compileModules(compiler, PythonLitestarIrGenerator(api).generateModules(selection), smokeCode = smoke))
    }
  }
}
