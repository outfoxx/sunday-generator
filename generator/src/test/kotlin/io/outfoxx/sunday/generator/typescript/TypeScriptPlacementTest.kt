package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.tools.PlacementFixture
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileGeneratedFiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
class TypeScriptPlacementTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `identical names remain independently importable across module directories`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val shared = api.models.single { it.name == "Shared" }
    val source = api.source.copy(location = directory.resolve("other.yaml").toUri().toString())
    val other =
      shared.copy(
        source = source,
        declarationUri = source.location + "#/types/Shared",
        placementScopes =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedPlacementScope(
              source.location,
              io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                typescript =
                  io.outfoxx.sunday.generator.ir.GeneratedPlacement
                    .Scope(module = "other"),
              ),
            ),
          ),
      )
    val models =
      api.models.map { model ->
        if (model.name != "Item") {
          model
        } else {
          model.copy(
            properties =
              model.properties +
                model.properties.single().copy(
                  name = "other",
                  type =
                    model.properties
                      .single()
                      .type
                      .copy(source = source),
                ),
          )
        }
      } + other
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api.copy(models = models), registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `cross directory references compile`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `consumer compiles and runs against owner declarations without owner source`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val owner = directory.resolve(if (kind == "raml") "common.raml" else "common.yaml").toUri()
    val consumer = directory.resolve(if (kind == "raml") "api.raml" else "api.yaml").toUri()

    fun generate(ownerUri: java.net.URI) {
      val registry = TypeScriptTypeRegistry(emptySet())
      TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
      registry.generateFiles(GeneratedOutputSelection(ownerUris = setOf(ownerUri)), compiler.srcDir)
    }
    val configPath = compiler.workDir.resolve("tsconfig.json")
    val original =
      java.nio.file.Files
        .readString(configPath)
    val mapper =
      com.fasterxml.jackson.module.kotlin
        .jacksonObjectMapper()
    val config = mapper.readTree(original) as com.fasterxml.jackson.databind.node.ObjectNode
    val options = config.get("compilerOptions") as com.fasterxml.jackson.databind.node.ObjectNode
    options.put("declaration", true)
    options.put("rootDir", "src")
    try {
      mapper.writeValue(configPath.toFile(), config)
      generate(owner)
      assertTrue(
        io.outfoxx.sunday.generator.typescript.tools
          .compileAndRunGeneratedFiles(compiler, "shared/shared"),
      )
      val artifact = directory.resolve("shared-artifact")
      compiler.workDir
        .resolve("dist/shared")
        .toFile()
        .copyRecursively(artifact.toFile())
      generate(consumer)
      artifact.toFile().copyRecursively(compiler.srcDir.resolve("shared").toFile())
      java.nio.file.Files.writeString(
        compiler.srcDir.resolve("smoke.ts"),
        """
        import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
        import {ItemSchema} from './consumer/item.js';
        import {SharedSchema} from './shared/shared.js';
        const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
        const schema = runtime.resolveSchema(ItemSchema);
        const item = schema.parse({shared:'one'});
        if (JSON.stringify(schema.encode(item)) !== JSON.stringify({shared:'one'})) throw new Error('wire value changed');
        if (runtime.forMode('request').resolveSchema(SharedSchema).safeParse('invalid').success) throw new Error('owner validation lost');
        """.trimIndent(),
      )
      assertTrue(
        io.outfoxx.sunday.generator.typescript.tools
          .compileAndRunGeneratedFiles(compiler, "smoke"),
      )
    } finally {
      java.nio.file.Files
        .writeString(configPath, original)
    }
  }
}
