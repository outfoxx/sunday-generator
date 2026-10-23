package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.SharedHelpersFixture
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunGeneratedFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
class TypeScriptSharedHelpersTest {
  @Test
  fun `nested patch helpers inherit the root module over child target defaults`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val fixture = SharedHelpersFixture(directory, "openapi")
    val api =
      fixture.api.copy(
        models =
          fixture.api.models.filter { it.name != "FirstUpdatePatch" }.map { model ->
            if (model.name.startsWith("SecondUpdate")) {
              model.copy(
                targets = model.targets + ("typescript" to GeneratedTarget(moduleName = "other")),
                nested = GeneratedNestedType(enclosedIn = GeneratedTypeRef.named("FirstUpdate"), name = model.name),
              )
            } else {
              model.copy(patchable = false)
            }
          },
      )
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    val paths =
      Files.walk(compiler.srcDir).use { files ->
        files.filter { it.toString().endsWith(".ts") }.toList().map { compiler.srcDir.relativize(it).toString() }
      }
    Files.writeString(
      compiler.srcDir.resolve("check.ts"),
      """
      import {FirstUpdate} from './first-update';
      import {MergePatchSupport} from './merge-patch-support';
      const value: FirstUpdate.SecondUpdate = {value: 'before'};
      if (FirstUpdate.SecondUpdate.merge(value, {value: null}).value !== undefined) throw new Error('deletion');
      if (!MergePatchSupport.runtime) throw new Error('shared export');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "check"))
    val sources = paths.map { CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, it) }
    assertEquals(1, sources.count { it.contains("Shared codec configuration and RFC 7396") })
  }

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
    "openapi,collision",
    "openapi,neither",
  )
  fun `shared exports survive owner selection and placement`(
    kind: String,
    scenario: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val fixture =
      SharedHelpersFixture(directory, kind, split = scenario == "split", collision = scenario == "collision")
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(fixture.api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(fixture.selection(scenario), compiler.srcDir)
    val paths =
      Files.walk(compiler.srcDir).use { files ->
        files.filter { it.toString().endsWith(".ts") }.toList().map { compiler.srcDir.relativize(it).toString() }
      }
    val models =
      listOf("FirstUpdate" to "first-update", "SecondUpdate" to "second-update").filter {
        scenario !=
          "neither" &&
          (scenario != "first" || it.first == "FirstUpdate") &&
          (scenario != "second" || it.first == "SecondUpdate")
      }
    val check =
      models
        .mapIndexed { index, (name, file) ->
          val path = if (scenario == "split" && name == "SecondUpdate") "other/" else ""
          """
          import {$name} from './$path$file';
          import {${name}Patch} from './$path$file-patch';
          ${if (scenario == "collision") "" else "import {MergePatchSupport as Support$index} from './${path}merge-patch-support';"}
          {
            const value: $name = {value: 'before'};
            if ($name.patch(value).value !== ${name}Patch.fromModel(value).value) throw new Error('factory');
            if ($name.merge(value, {}).value !== 'before') throw new Error('omission');
            if ($name.merge(value, $name.patch({value: 'after'}, value)).value !== 'after') throw new Error('difference');
            if ($name.merge(value, {value: null}).value !== undefined) throw new Error('deletion');
            if (value.value !== 'before') throw new Error('mutation');
            ${if (scenario == "collision") "" else "if (!Support$index.equal({a: 1}, {a: 1})) throw new Error('export');"}
          }
          """.trimIndent()
        }.joinToString("\n")
    val barrel =
      if (scenario !in setOf("collision", "split", "neither")) {
        "import {MergePatchSupport as SharedSupport} from './index'; if (!SharedSupport.runtime) throw new Error('barrel export');\n"
      } else {
        ""
      }
    Files.writeString(compiler.srcDir.resolve("check.ts"), barrel + check.ifEmpty { "export {};" })
    assertTrue(compileAndRunGeneratedFiles(compiler, "check"))
    val sources = paths.map { CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, it) }
    assertEquals(
      when (scenario) {
        "neither" -> 0
        "split" -> 2
        else -> 1
      },
      sources.count { it.contains("Shared codec configuration and RFC 7396") },
    )
  }
}
