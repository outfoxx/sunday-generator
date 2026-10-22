package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OutputSelectionFixture
import io.outfoxx.sunday.generator.tools.pythonPlacement
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class PythonOutputSelectionTest : PythonTest() {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `only selected owner is emitted`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val fixture = OutputSelectionFixture(directory, kind)
    val modules =
      PythonSundayIrGenerator(fixture.api, PythonGeneratorOptions(placement = pythonPlacement("test_api")))
        .generateModules(fixture.selection)
    assertTrue(compileModules(compiler, modules, listOf("test_api.models")))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/models.py")
    assertTrue(source.contains("class Shared"))
    assertFalse(source.contains("class Item"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["client-models", "server-models", "client-problems", "server-problems"])
  fun `excluded services do not enforce unsupported policies`(
    mode: String,
    compiler: PythonCompiler,
  ) {
    val input =
      Files.writeString(
        directory.resolve("policy.yaml"),
        """
        openapi: 3.1.0
        info: {title: Example, version: '1'}
        paths:
          /items:
            x-sunday-policy:
              all: {timeout: PT5S}
            get:
              operationId: getItems
              responses:
                '204': {description: Done}
        components:
          schemas:
            Item:
              type: object
              properties: {value: {type: string}}
        """.trimIndent(),
      )
    val api = GeneratedApiIrExporter().export(input.toUri())
    val options = PythonGeneratorOptions(placement = pythonPlacement("test_api"))
    val category = if (mode.endsWith("models")) GeneratedTypeCategory.Model else GeneratedTypeCategory.Problem
    val selection = GeneratedOutputSelection(setOf(category))
    val modules =
      if (mode.startsWith("client")) {
        PythonSundayIrGenerator(api, options).generateModules(selection)
      } else {
        PythonLitestarIrGenerator(api, options).generateModules(selection)
      }
    val moduleName = if (category == GeneratedTypeCategory.Model) "models" else "problems"
    assertTrue(compileModules(compiler, modules, listOf("test_api.$moduleName")))
    assertFalse(modules.any { it.path.endsWith("/example.py") })
    assertThrows(GenerationException::class.java) {
      if (mode.startsWith("client")) {
        PythonSundayIrGenerator(api, options).generateModules()
      } else {
        PythonLitestarIrGenerator(api, options).generateModules()
      }
    }
  }

  @Test
  fun `service category retains operation local declarations`(compiler: PythonCompiler) {
    val input =
      Files.writeString(
        directory.resolve("service.yaml"),
        """
        openapi: 3.1.0
        info: {title: Example, version: '1'}
        paths:
          /items:
            get:
              operationId: getItems
              parameters:
                - name: state
                  in: query
                  required: true
                  schema: {type: string, enum: [active, inactive]}
              responses:
                '204': {description: Done}
        """.trimIndent(),
      )
    val modules =
      PythonSundayIrGenerator(
        GeneratedApiIrExporter().export(input.toUri()),
        PythonGeneratorOptions(placement = pythonPlacement("test_api")),
      ).generateModules(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)))
    assertTrue(compileModules(compiler, modules, listOf("test_api.example")))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/example.py")
    assertTrue(source.contains("get_items"), source)
  }
}
