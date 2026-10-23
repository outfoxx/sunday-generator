@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.SharedHelpersFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
class KotlinSharedHelpersTest {
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
  fun `shared helpers compile independently for each owner`(
    kind: String,
    scenario: String,
    @TempDir directory: Path,
  ) {
    val fixture =
      SharedHelpersFixture(directory, kind, split = scenario == "split", collision = scenario == "collision")
    for (target in listOf("jaxrs", "sunday")) {
      val registry =
        KotlinTypeRegistry(
          null,
          GenerationMode.Client,
          setOf(
            KotlinTypeRegistry.Option.ImplementModel,
            KotlinTypeRegistry.Option.JacksonAnnotations,
            KotlinTypeRegistry.Option.ValidationConstraints,
          ),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (target ==
        "jaxrs"
      ) {
        KotlinJAXRSIrGenerator(
          fixture.api,
          registry,
          KotlinJAXRSOptions(
            false,
            false,
            null,
            false,
            null,
            false,
            GeneratedPlacementConfiguration(),
            "https://test/",
            listOf("application/json"),
            "API",
            false,
          ),
        ).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(
          fixture.api,
          registry,
          KotlinSundayOptions(GeneratedPlacementConfiguration(), "https://test/", listOf("application/json"), "API"),
        ).generateServiceTypes()
      }
      val types = registry.buildTypes(fixture.selection(scenario))
      val models =
        listOf("FirstUpdate", "SecondUpdate").filter {
          scenario != "neither" &&
            (scenario != "first" || it == "FirstUpdate") &&
            (scenario != "second" || it == "SecondUpdate")
        }
      val check =
        models.joinToString("\n") { name ->
          val pkg = if (scenario == "split" && name == "SecondUpdate") "io.other" else "io.test"
          """
          run {
            val value = $pkg.$name(value = "before")
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val snapshot = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(value.patch())
            val converted = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>($pkg.${name}Patch.fromModel(value))
            check(snapshot == converted)
            check(value.merge($pkg.${name}Patch()).value == "before")
            check(value.merge($pkg.$name(value = "after").patch(value)).value == "after")
            val deleted = mapper.readValue("{\"value\":null}", $pkg.${name}Patch::class.java)
            check(value.merge(deleted).value == null)
            check(value.value == "before")
          }
          """.trimIndent()
        }
      val compiled = compileTypesResult(types, listOf(SourceFile.kotlin("Check.kt", "fun verify() {\n$check\n}")))
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      compiled.classLoader
        .loadClass("CheckKt")
        .getMethod("verify")
        .invoke(null)
      val helpers =
        types.keys.filter {
          it.simpleName.startsWith("MergePatchSupport") &&
            !(scenario == "collision" && it.simpleName == "MergePatchSupport")
        }
      assertEquals(
        when (scenario) {
          "neither" -> 0
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
        assertTrue(source.contains("fun <T : Any> apply"), source)
        assertFalse(source.contains("FirstUpdate::class"), source)
        assertFalse(source.contains("SecondUpdate::class"), source)
      }
    }
  }
}
