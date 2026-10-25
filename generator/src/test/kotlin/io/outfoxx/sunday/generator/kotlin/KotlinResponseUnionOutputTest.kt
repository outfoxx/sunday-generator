@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.ResponseUnionOutputFixture
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@KotlinTest
class KotlinResponseUnionOutputTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `nested declarations follow their hoisted operation model`(kind: String) {
    val source = PlacementRegressionFixture(directory, kind).inline()
    val parent = source.models.single { it.properties.any { property -> property.name == "child" } }
    val child = source.models.single { it.properties.any { property -> property.name == "value" } }
    val api =
      GeneratedApiYaml.readString(
        GeneratedApiYaml.writeString(
          source.copy(
            models =
              source.models.map { model ->
                if (model == child) {
                  model.copy(
                    nested =
                      GeneratedNestedType(
                        enclosedIn = GeneratedTypeRef.named(parent.name, scope = parent.scope, source = parent.source),
                        name = "Child",
                      ),
                  )
                } else {
                  model
                }
              },
          ),
        ),
      )
    val registry = generate(api, "sunday")
    val compiled = compileTypesResult(registry.buildTypes(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model))))
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val parentType = compiled.classLoader.loadClass("example.operation.${parent.name.toUpperCamelCase()}")
    assertEquals(parentType, parentType.getMethod("getChild").returnType.enclosingClass)
  }

  @ParameterizedTest
  @CsvSource(
    "openapi,true",
    "raml,true",
    "asyncapi2,true",
    "asyncapi,true",
    "composed,true",
    "openapi,false",
    "raml,false",
    "asyncapi2,false",
    "asyncapi,false",
    "composed,false",
  )
  fun `model artifact includes operation models and services only reference it`(
    kind: String,
    unionBody: Boolean,
  ) {
    val api = ResponseUnionOutputFixture(directory, kind, unionBody).api
    for (backend in listOf("server", "client", "sunday")) {
      val registry = generate(api, backend)
      val modelSources = directory.resolve("$backend-models")
      registry.generateFiles(
        GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
        modelSources,
      )
      val models = compileTypesResult(emptyMap(), sourceDirectory = modelSources)
      assertEquals(KotlinCompilation.ExitCode.OK, models.exitCode, models.messages)
      assertFalse(Files.exists(modelSources.resolve("example/api")))
      val mapper =
        com.fasterxml.jackson.module.kotlin
          .jacksonObjectMapper()
      if (unionBody) {
        val first = models.classLoader.loadClass("example.model.First")
        val second = models.classLoader.loadClass("example.model.Second")
        val union = first.interfaces.singleOrNull { it.packageName == "example.model" }
        if (kind in setOf("openapi", "composed")) {
          assertTrue(union != null && union.isAssignableFrom(second))
          assertTrue(union!!.enclosingClass == null)
        }
        for ((wire, type) in listOf("""{"first":"one"}""" to first, """{"second":"two"}""" to second)) {
          val decoded = mapper.readValue(wire, union ?: type)
          assertEquals(type, decoded.javaClass)
          assertEquals(mapper.readTree(wire), mapper.readTree(mapper.writeValueAsString(decoded)))
        }
      } else {
        val payload = api.models.single { it.properties.any { property -> property.name == "value" } }
        val type = models.classLoader.loadClass("example.model.${payload.name.toUpperCamelCase()}")
        assertEquals("value", type.getMethod("getValue").invoke(mapper.readValue("""{"value":"value"}""", type)))
      }

      val artifact = directory.resolve("$backend-models.jar")
      JarOutputStream(Files.newOutputStream(artifact)).use { jar ->
        val root = models.outputDirectory.toPath()
        Files.walk(root).use { paths ->
          paths.filter(Files::isRegularFile).forEach { path ->
            jar.putNextEntry(JarEntry(root.relativize(path).toString()))
            Files.copy(path, jar)
            jar.closeEntry()
          }
        }
      }
      modelSources.toFile().deleteRecursively()
      assertFalse(Files.exists(modelSources))
      val serviceSources = directory.resolve("$backend-services")
      registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)), serviceSources)
      val services =
        compileTypesResult(
          emptyMap(),
          sourceDirectory = serviceSources,
          additionalClasspath = listOf(artifact.toFile()),
        )
      assertEquals(KotlinCompilation.ExitCode.OK, services.exitCode, services.messages)
      assertFalse(Files.exists(serviceSources.resolve("example/model")))
      assertTrue(Files.exists(serviceSources.resolve("example/api")))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["server", "client", "sunday"])
  fun `problem models include inline response unions when sharing the service package`(
    backend: String,
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val registry = generate(GeneratedApiIrExporter().export(source), backend)
    val models =
      compileTypesResult(
        registry.buildTypes(
          GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
        ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, models.exitCode, models.messages)
    val union = models.classLoader.loadClass("io.test.inherited.APIReadMissingResponseBody")
    assertTrue(union.enclosingClass == null)
    for (name in listOf("TeamNotFoundProblem", "UserNotFoundProblem")) {
      assertTrue(union.isAssignableFrom(models.classLoader.loadClass("io.test.inherited.$name")))
    }
  }

  private fun generate(
    api: GeneratedApi,
    backend: String,
  ): KotlinTypeRegistry {
    val registry =
      KotlinTypeRegistry(
        null,
        if (backend == "server") GenerationMode.Server else GenerationMode.Client,
        setOf(
          KotlinTypeRegistry.Option.ImplementModel,
          KotlinTypeRegistry.Option.JacksonAnnotations,
          KotlinTypeRegistry.Option.ValidationConstraints,
          KotlinTypeRegistry.Option.UseJakartaPackages,
        ),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (backend == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          defaultProblemBaseUri = "https://example.test/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
        ),
      ).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(
        api,
        registry,
        KotlinJAXRSOptions(
          false,
          false,
          null,
          false,
          null,
          false,
          defaultProblemBaseUri = "https://example.test/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = true,
        ),
      ).generateServiceTypes()
    }
    return registry
  }
}
