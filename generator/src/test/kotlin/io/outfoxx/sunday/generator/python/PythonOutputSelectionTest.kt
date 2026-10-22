package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OutputSelectionFixture
import org.junit.jupiter.api.Assertions.assertFalse
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
      PythonSundayIrGenerator(fixture.api, PythonGeneratorOptions(packageName = "test_api"))
        .generateModules(fixture.selection)
    assertTrue(compileModules(compiler, modules, listOf("test_api.models")))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/models.py")
    assertTrue(source.contains("class Shared"))
    assertFalse(source.contains("class Item"))
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
        PythonGeneratorOptions(packageName = "test_api"),
      ).generateModules(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)))
    assertTrue(compileModules(compiler, modules, listOf("test_api.example")))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/example.py")
    assertTrue(source.contains("get_items"), source)
  }
}
