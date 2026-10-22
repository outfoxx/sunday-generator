@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@KotlinTest
class KotlinOutputSelectionTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `consumer compiles against packaged owner without owner sources`() {
    val shared =
      Files.writeString(
        directory.resolve("shared.yaml"),
        """
        openapi: 3.1.0
        info: {title: Shared, version: '1'}
        paths: {}
        components:
          schemas:
            SharedImpact:
              type: object
              x-sunday-patchable: true
              required: [kind, reason]
              discriminator:
                propertyName: kind
                mapping: {special: '#/components/schemas/SpecialImpact'}
              properties: {kind: {type: string}, reason: {type: string}}
            SpecialImpact:
              allOf:
                - {${'$'}ref: '#/components/schemas/SharedImpact'}
                - type: object
                  required: [severity]
                  properties: {severity: {type: integer, minimum: 1}}
        """.trimIndent(),
      )
    val service =
      Files.writeString(
        directory.resolve("service.yaml"),
        """
        openapi: 3.1.0
        info: {title: Service, version: '1'}
        paths:
          /item:
            get:
              operationId: getItem
              responses:
                '200':
                  description: Item
                  content:
                    application/json:
                      schema: {${'$'}ref: '#/components/schemas/Item'}
        components:
          schemas:
            Item:
              type: object
              required: [impact]
              properties:
                impact: {${'$'}ref: './shared.yaml#/components/schemas/SharedImpact'}
                label: {type: string, default: local}
        """.trimIndent(),
      )

    fun generate(
      input: Path,
      output: Path,
      owner: Path = input,
    ) {
      val api = GeneratedApiIrExporter().export(input.toUri())
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(
            KotlinTypeRegistry.Option.ImplementModel,
            KotlinTypeRegistry.Option.JacksonAnnotations,
            KotlinTypeRegistry.Option.ValidationConstraints,
          ),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      registry.generateFiles(GeneratedOutputSelection(ownerUris = setOf(owner.toUri())), output)
    }
    val ownerSources = directory.resolve("owner")
    generate(service, ownerSources, shared)
    val owner = compileTypesResult(emptyMap(), sourceDirectory = ownerSources)
    assertEquals(KotlinCompilation.ExitCode.OK, owner.exitCode, owner.messages)
    val artifact = directory.resolve("shared.jar")
    JarOutputStream(Files.newOutputStream(artifact)).use { jar ->
      Files.walk(owner.outputDirectory.toPath()).use { paths ->
        paths.filter(Files::isRegularFile).forEach { path ->
          jar.putNextEntry(
            JarEntry(
              owner.outputDirectory
                .toPath()
                .relativize(path)
                .toString(),
            ),
          )
          Files.copy(path, jar)
          jar.closeEntry()
        }
      }
    }
    ownerSources.toFile().deleteRecursively()
    val consumerSources = directory.resolve("consumer")
    generate(service, consumerSources)
    val consumer =
      compileTypesResult(emptyMap(), sourceDirectory = consumerSources, additionalClasspath = listOf(artifact.toFile()))
    assertEquals(KotlinCompilation.ExitCode.OK, consumer.exitCode, consumer.messages)
    assertTrue(Files.exists(consumerSources.resolve("io/test/Item.kt")))
    assertFalse(Files.exists(consumerSources.resolve("io/test/SharedImpact.kt")))
    val mapper =
      com.fasterxml.jackson.module.kotlin
        .jacksonObjectMapper()
    val itemClass = consumer.classLoader.loadClass("io.test.Item")
    val decoded = mapper.readValue("""{"impact":{"kind":"special","reason":"example","severity":2}}""", itemClass)
    assertEquals(
      mapper.readTree("""{"impact":{"kind":"special","reason":"example","severity":2},"label":"local"}"""),
      mapper.readTree(mapper.writeValueAsString(decoded)),
    )
    assertEquals(
      "io.test.SpecialImpact",
      itemClass
        .getMethod("getImpact")
        .invoke(decoded)
        .javaClass.name,
    )
    assertEquals(
      "io.test.SharedImpact",
      consumer.classLoader
        .loadClass("io.test.Item")
        .getMethod("getImpact")
        .returnType.name,
    )
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `owner filter compiles across frontends`(kind: String) {
    val fixture =
      io.outfoxx.sunday.generator.tools
        .OutputSelectionFixture(directory, kind)
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinJAXRSIrGenerator(fixture.api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    val output = directory.resolve("generated")
    registry.generateFiles(fixture.selection, output)
    val compiled = compileTypesResult(emptyMap(), sourceDirectory = output)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    assertTrue(Files.exists(output.resolve("io/test/Shared.kt")))
    assertFalse(Files.exists(output.resolve("io/test/Item.kt")))
  }

  @Test
  fun `problems form an independent output category`() {
    val fixture =
      io.outfoxx.sunday.generator.tools
        .OutputSelectionFixture(directory, "openapi")
    val api =
      fixture.api.copy(
        problems =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedProblem(
              name = "MissingProblem",
              typeUri = "https://example.test/missing",
              status = 404,
              title = "Missing",
              detail = "Missing resource",
            ),
          ),
      )
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    val output = directory.resolve("problems")
    registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Problem)), output)
    val compiled = compileTypesResult(emptyMap(), sourceDirectory = output)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    assertTrue(Files.walk(output).use { it.anyMatch { path -> path.fileName.toString() == "MissingProblem.kt" } })
    assertFalse(Files.exists(output.resolve("io/test/Item.kt")))
  }

  @Test
  fun `composed service matches either owner and retains every operation`() {
    val inputs =
      listOf("first", "second").map { name ->
        Files.writeString(
          directory.resolve("$name.yaml"),
          """
          openapi: 3.1.0
          x-sunday-apiId: combined
          info: {title: Combined, version: '1'}
          servers: [{url: 'https://example.test'}]
          paths:
            /$name:
              get:
                operationId: $name
                responses:
                  '204': {description: Done}
          """.trimIndent(),
        )
      }
    val api = GeneratedApiIrExporter().export(inputs.map { it.toUri() })
    inputs.forEachIndexed { index, input ->
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(KotlinTypeRegistry.Option.ImplementModel),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      val output = directory.resolve("composed-$index")
      registry.generateFiles(
        GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service), setOf(input.toUri())),
        output,
      )
      val compiled = compileTypesResult(emptyMap(), sourceDirectory = output)
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      val source =
        Files.walk(output).use { paths ->
          paths
            .filter { it.toString().endsWith(".kt") }
            .map(Files::readString)
            .toList()
            .joinToString("\n")
        }
      assertTrue(source.contains("fun first("), source)
      assertTrue(source.contains("fun second("), source)
    }
  }
}
