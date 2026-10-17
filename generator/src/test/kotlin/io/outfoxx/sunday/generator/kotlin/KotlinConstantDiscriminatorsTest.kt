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

package io.outfoxx.sunday.generator.kotlin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.constantDiscriminatorsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
@Tag("models")
class KotlinConstantDiscriminatorsTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @CsvSource(
    "sunday, openapi",
    "jaxrs, openapi",
    "sunday, asyncapi",
    "jaxrs, asyncapi",
    "sunday, composed",
    "jaxrs, composed",
  )
  fun `constant tags are constructed implicitly and decoded strictly`(
    target: String,
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = constantDiscriminatorsApi(directory, frontend)
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
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test", "https://example.test/", emptyList(), "API"),
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
          "io.test",
          "https://example.test/",
          emptyList(),
          "API",
          false,
        ),
      ).generateServiceTypes()
    }
    val result =
      compileTypesResult(
        registry.buildTypes(),
        listOf(
          SourceFile.kotlin(
            "Consumer.kt",
            """
              package io.test
              fun newBeat(): BeatExtent = BeatExtent()
            fun newConstants(): OnlyConstants = OnlyConstants()
            """.trimIndent(),
          ),
        ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    assertThrows(ClassNotFoundException::class.java) {
      result.classLoader.loadClass("org.hibernate.validator.HibernateValidator")
    }
    val model = result.classLoader.loadClass("io.test.BeatExtent")
    val instance =
      result.classLoader
        .loadClass("io.test.ConsumerKt")
        .getMethod("newBeat")
        .invoke(null)
    val mapper = jacksonObjectMapper()
    val constants =
      result.classLoader
        .loadClass("io.test.ConsumerKt")
        .getMethod("newConstants")
        .invoke(null)
    assertEquals(
      mapper.readTree("""{"tag-value":"fixed","zero":0,"flag":false,"optional":"fixed"}"""),
      mapper.valueToTree(constants),
    )
    val constantType = result.classLoader.loadClass("io.test.OnlyConstants")
    val decodedConstants = mapper.readValue("""{"tag-value":"fixed","zero":0,"flag":false}""", constantType)
    assertEquals(
      mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(constants),
      mapper.valueToTree(decodedConstants),
    )
    assertThrows(Exception::class.java) {
      mapper.readValue("""{"tag-value":"wrong","zero":0,"flag":false}""", constantType)
    }

    assertEquals("beat", mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(instance)["kind"].asText())
    assertEquals(
      "beat",
      mapper
        .valueToTree<com.fasterxml.jackson.databind.JsonNode>(
          mapper.readValue("""{"kind":"beat"}""", model),
        )["kind"]
        .asText(),
    )
    assertThrows(Exception::class.java) { mapper.readValue("""{"kind":"x"}""", model) }
  }
}
