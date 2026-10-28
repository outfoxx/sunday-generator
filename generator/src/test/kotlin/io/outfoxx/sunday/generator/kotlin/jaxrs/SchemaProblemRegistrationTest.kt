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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zalando.problem.Problem
import org.zalando.problem.jackson.ProblemModule
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("responses")
class SchemaProblemRegistrationTest {
  @ParameterizedTest
  @ValueSource(strings = ["client", "server", "schema-only", "no-jackson"])
  fun `schema problems register explicit unique wire types alongside annotated problems`(
    target: String,
    @ResourceUri("openapi/ir/schema-problem-registration.yaml") source: URI,
  ) {
    val original = GeneratedApiIrExporter().export(source)
    val api =
      if (target == "schema-only") {
        original.copy(
          problems = emptyList(),
          services =
            original.services.map { service ->
              service.copy(operations = service.operations.map { it.copy(problems = emptyList()) })
            },
        )
      } else {
        original
      }
    val registry =
      KotlinTypeRegistry(
        null,
        if (target == "server") GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          if (target != "no-jackson") add(KotlinTypeRegistry.Option.JacksonAnnotations)
        },
        problemLibrary = KotlinProblemLibrary.ZALANDO,
      )
    KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/service/API.kt")
    if (target == "no-jackson") {
      assertFalse(service.contains("registerProblems"), service)
      return
    }
    assertTrue(service.contains("NamedType(MissingProblem::class.java"), service)
    assertFalse(service.contains("RepeatedProblem::class.java"), service)
    assertFalse(service.contains("StandardProblem::class.java"), service)
    assertFalse(service.contains("HttpProblem::class.java"), service)
    val apiClass = result.classLoader.loadClass("io.test.service.API")
    val companion = apiClass.getField("Companion").get(null)
    val register = companion.javaClass.getMethod("registerProblems", ObjectMapper::class.java)
    val recording = RecordingMapper()
    register.invoke(companion, recording)
    assertEquals(
      if (target ==
        "schema-only"
      ) {
        emptyList()
      } else {
        listOf("AnnotatedProblem")
      },
      recording.classes.map { it.simpleName },
    )
    assertEquals(
      if (target == "schema-only") listOf("CollisionProblem", "MissingProblem") else listOf("MissingProblem"),
      recording.named.map { it.type.simpleName },
    )
    assertEquals("https://example.com/problems/missing", recording.named.last().name)
    val mapper = jacksonObjectMapper().registerModule(ProblemModule())
    register.invoke(companion, mapper)
    val decoded =
      mapper.readValue(
        """{"type":"https://example.com/problems/missing","title":"Missing","status":404}""",
        Problem::class.java,
      )
    assertEquals("MissingProblem", decoded.javaClass.simpleName)
  }

  private class RecordingMapper : ObjectMapper() {
    val classes = mutableListOf<Class<*>>()
    val named = mutableListOf<NamedType>()

    override fun registerSubtypes(vararg types: Class<*>) {
      classes.addAll(types)
    }

    override fun registerSubtypes(vararg types: NamedType) {
      named.addAll(types)
    }
  }
}
