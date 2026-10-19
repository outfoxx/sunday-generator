/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@KotlinTest
@Tag("models")
class KotlinCustomTypeMappingTest {
  @ParameterizedTest
  @CsvSource("Client,none", "Server,none", "Client,javax", "Server,javax", "Server,jakarta")
  fun `external custom scalar mappings compile and retain wire validation`(
    mode: GenerationMode,
    namespace: String,
  ) {
    val api = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    for (jaxrs in if (mode == GenerationMode.Client) listOf(true, false) else listOf(true)) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          mode,
          buildSet {
            add(KotlinTypeRegistry.Option.ImplementModel)
            add(KotlinTypeRegistry.Option.JacksonAnnotations)
            if (namespace != "none") add(KotlinTypeRegistry.Option.ValidationConstraints)
            if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
          },
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (jaxrs) {
        KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes(), listOf(identifierSource("Identifier")))
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      val verify = {
        val record = result.classLoader.loadClass("io.test.Record")
        val identifier = result.classLoader.loadClass("io.example.Identifier")
        assertEquals(identifier, record.getMethod("getId").returnType)
        val mapper = jacksonObjectMapper()
        val value = mapper.readValue("""{"id":"id:abc","optional":"id:def","plain":"plain"}""", record)
        assertEquals("id:abc", record.getMethod("getId").invoke(value).toString())
        assertEquals(
          mapper.readTree("""{"id":"id:abc","optional":"id:def","plain":"plain"}"""),
          mapper.readTree(mapper.writeValueAsString(value)),
        )
        assertThrows(Exception::class.java) { mapper.readValue("""{"id":"invalid"}""", record) }
        assertThrows(Exception::class.java) { mapper.readValue("""{"id":"id:abcdefghijklmnopqrstuvwxyz"}""", record) }
        if (namespace != "none") assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, value, "Request"))
        val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/Record.kt")
        assertTrue(source.contains("Identifier"), source)
        if (jaxrs) {
          val service = result.classLoader.loadClass("io.test.service.API")
          val method = service.methods.single { it.name == "identify" }
          assertEquals(listOf(identifier, identifier), method.parameterTypes.toList())
          assertFalse(
            method.parameterAnnotations.flatten().any {
              it.annotationClass.simpleName in
                setOf("Pattern", "Size", "Email")
            },
          )
        }
      }
      if (namespace == "none") verify() else withNativeBeanValidation(namespace, result.classLoader, verify)
    }
  }

  @Test
  fun `unmapped Kotlin retains string signatures`() {
    val source = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    val api = source.copy(models = source.models.map { it.copy(targets = emptyMap()) })
    for (mode in GenerationMode.entries) {
      val registry = KotlinTypeRegistry("io.test", null, mode, setOf(KotlinTypeRegistry.Option.ImplementModel))
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      assertEquals(
        String::class.java,
        result.classLoader
          .loadClass("io.test.Record")
          .getMethod("getId")
          .returnType,
      )
    }
  }

  @ParameterizedTest
  @CsvSource(
    "Client,client,ClientIdentifier",
    "Server,server,ServerIdentifier",
    "Client,server,Identifier",
    "Server,client,Identifier",
  )
  fun `mode mappings override the common scalar type`(
    mode: GenerationMode,
    target: String,
    type: String,
    @TempDir directory: Path,
  ) {
    val fixture = Path.of("src/test/resources/openapi/ir/custom-types")
    directory.resolve("api.yaml").writeText(fixture.resolve("api.yaml").readText())
    directory.resolve("identifier.yaml").writeText(
      fixture.resolve("identifier.yaml").readText() +
        "\nx-sunday-kotlinType:$target: io.example.${target.replaceFirstChar { it.uppercase() }}Identifier\n",
    )
    val api = OpenApiToGeneratedApi().convert(directory.resolve("api.yaml").toUri())
    val registry = KotlinTypeRegistry("io.test", null, mode, setOf(KotlinTypeRegistry.Option.ImplementModel))
    KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes(), listOf(identifierSource(type)))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertEquals(
      "io.example.$type",
      result.classLoader
        .loadClass("io.test.Record")
        .getMethod("getId")
        .returnType.name,
    )
  }

  private fun identifierSource(name: String): SourceFile =
    SourceFile.kotlin(
      "$name.kt",
      """
      package io.example
      import com.fasterxml.jackson.annotation.JsonCreator
      import com.fasterxml.jackson.annotation.JsonValue
      data class $name @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(@get:JsonValue val value: String) {
        override fun toString(): String = value
        companion object {
          @JvmStatic fun fromString(value: String): $name = $name(value)
        }
      }
      """.trimIndent(),
    )
}
