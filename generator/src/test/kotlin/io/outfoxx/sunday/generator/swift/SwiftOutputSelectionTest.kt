package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OutputSelectionFixture
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@SwiftTest
class SwiftOutputSelectionTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `only selected owner is emitted`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val fixture = OutputSelectionFixture(directory, kind)
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      fixture.api,
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(fixture.selection, compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Shared.swift").contains("Shared"))
    assertThrows(AssertionError::class.java) {
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Item.swift")
    }
  }
}
