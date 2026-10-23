@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@KotlinTest
class KotlinSharedServiceHelpersTest {
  @ParameterizedTest
  @ValueSource(strings = ["all", "first", "second", "split", "neither", "models", "aggregate"])
  fun `optional body providers share package implementation and retain independent bindings`(scenario: String) {
    val owners = listOf(URI("https://example.test/first.yaml"), URI("https://example.test/second.yaml"))
    val api =
      GeneratedApi(
        name = "Services",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, owners.first().toString()),
        services =
          listOf("First", "Second").mapIndexed { index, name ->
            GeneratedService(
              name,
              ownerUris = setOf(owners[index].toString()),
              targets =
                mapOf(
                  "kotlin" to
                    GeneratedTarget(
                      packageName =
                        if (scenario == "split" &&
                          index == 1
                        ) {
                          "io.other"
                        } else {
                          "io.test"
                        },
                    ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    "update",
                    "POST",
                    "/${name.lowercase()}",
                    requestBody =
                      GeneratedPayload(
                        GeneratedTypeRef.scalar("string"),
                        mediaTypes = listOf("text/plain"),
                        required = false,
                      ),
                  ),
                ),
            )
          },
      )
    val registry =
      KotlinTypeRegistry(null, GenerationMode.Server, emptySet(), problemLibrary = KotlinProblemLibrary.SUNDAY)
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
        kotlinPlacement("io.test"),
        "https://test/",
        listOf("text/plain"),
        "API",
        false,
        aggregateServices = scenario == "aggregate",
        aggregateServiceName = "OptionalBodySupport",
      ),
    ).generateServiceTypes()
    val selection =
      when (scenario) {
        "first" -> GeneratedOutputSelection(ownerUris = setOf(owners[0]))
        "second" -> GeneratedOutputSelection(ownerUris = setOf(owners[1]))
        "neither" -> GeneratedOutputSelection(ownerUris = setOf(URI("https://example.test/excluded.yaml")))
        "models" -> GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model))
        else -> GeneratedOutputSelection()
      }
    val types = registry.buildTypes(selection)
    val compiled = compileTypesResult(types, classpathFilter = { !it.name.contains("jackson", true) })
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val helpers =
      types.keys.filter {
        it.simpleName.startsWith("OptionalBodySupport") &&
          (scenario != "aggregate" || it.simpleName != "OptionalBodySupport")
      }
    assertEquals(
      when (scenario) {
        "models", "neither" -> 0
        "split" -> 2
        else -> 1
      },
      helpers.size,
    )
    helpers.forEach { name ->
      val source =
        CompiledGeneratedSources.source(
          GeneratedCodeLanguage.Kotlin,
          "${name.packageName.replace('.', '/')}/${name.simpleName}.kt",
        )
      assertEquals(1, Regex("PushbackInputStream\\(context.inputStream").findAll(source).count())
    }
  }
}
