package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.SharedHelpersFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class PythonSharedHelpersTest : PythonTest() {
  @ParameterizedTest
  @CsvSource(
    "raml,all",
    "openapi,all",
    "asyncapi,all",
    "composed,all",
    "raml,second",
    "openapi,first",
    "openapi,second",
    "asyncapi,second",
    "composed,second",
    "openapi,split",
    "openapi,neither",
  )
  fun `module helpers are emitted once for selected models`(
    kind: String,
    scenario: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val fixture = SharedHelpersFixture(directory, kind, split = scenario == "split")
    val modules = PythonSundayIrGenerator(fixture.api).generateModules(fixture.selection(scenario))
    val models =
      listOf("FirstUpdate", "SecondUpdate").filter {
        scenario != "neither" &&
          (scenario != "first" || it == "FirstUpdate") &&
          (scenario != "second" || it == "SecondUpdate")
      }
    val check =
      models.joinToString("\n") { name ->
        val pkg = if (scenario == "split" && name == "SecondUpdate") "other_sdk" else "test_sdk"
        """
        from $pkg.models import $name, ${name}Patch
        value = $name(value="before")
        assert value.patch() == ${name}Patch.from_model(value)
        assert value.merge(${name}Patch()).value == "before"
        assert value.merge($name(value="after").patch(from_model=value)).value == "after"
        assert value.merge(${name}Patch(value=None)).value is None
        assert value.value == "before"
        """.trimIndent()
      }
    assertTrue(compileModules(compiler, modules, smokeCode = check.ifEmpty { "pass" }))
    val sources = modules.map { CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, it.path) }
    assertEquals(
      when (scenario) {
        "neither" -> 0
        "split" -> 2
        else -> 1
      },
      sources.sumOf { Regex("def _patch_merge\\(").findAll(it).count() },
    )
  }
}
