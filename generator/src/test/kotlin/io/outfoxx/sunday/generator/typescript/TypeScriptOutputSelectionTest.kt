package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OutputSelectionFixture
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileGeneratedFiles
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
class TypeScriptOutputSelectionTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `only selected owner is emitted`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val fixture = OutputSelectionFixture(directory, kind)
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(fixture.api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(fixture.selection, compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "shared.ts").contains("Shared"))
    assertThrows(AssertionError::class.java) {
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "item.ts")
    }
  }

  @Test
  fun `patch helper name avoids declared models`(compiler: TypeScriptCompiler) {
    val input =
      Files.writeString(
        directory.resolve("models.yaml"),
        """
        openapi: 3.1.0
        info: {title: Example, version: '1'}
        paths: {}
        components:
          schemas:
            Foo:
              type: object
              x-sunday-patchable: true
              properties: {value: {type: string}}
            FooMergePatchSupport:
              type: object
              properties: {other: {type: string}}
        """.trimIndent(),
      )
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(input.toUri()), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "foo-merge-patch-support.ts")
    assertTrue(source.contains("other"), source)
  }

  @Test
  fun `service category retains operation local declarations`(compiler: TypeScriptCompiler) {
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
                '200':
                  description: Item
                  content:
                    application/json:
                      schema:
                        type: object
                        required: [value]
                        properties: {value: {type: string}}
        """.trimIndent(),
      )
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(input.toUri()), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)), compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "api.ts")
    assertTrue(source.contains("getItems"), source)
    assertTrue(source.contains("export type GetItemsResponseBody"), source)
  }
}
