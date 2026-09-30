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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.fieldConstraintsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
class KotlinFieldConstraintsTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @CsvSource(
    "sunday, openapi",
    "sunday, raml",
    "sunday, asyncapi",
    "sunday, composed",
    "jaxrs-client, openapi",
    "jaxrs-client, raml",
    "jaxrs-client, asyncapi",
    "jaxrs-client, composed",
    "jaxrs-server, openapi",
    "jaxrs-server, raml",
    "jaxrs-server, asyncapi",
    "jaxrs-server, composed",
  )
  fun `ordinary constraints validate constructors and decoding`(
    target: String,
    sourceKind: String,
    @TempDir directory: Path,
  ) {
    val api = fieldConstraintsApi(directory, sourceKind)
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target == "jaxrs-server") GenerationMode.Server else GenerationMode.Client,
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
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val mapper = jacksonObjectMapper()
    val model = result.classLoader.loadClass("io.test.Probe")
    val countField = if (target == "sunday") model.getDeclaredField("count") else model.getMethod("getCount")
    assertEquals(0L, countField.getAnnotation(javax.validation.constraints.Min::class.java).value)
    assertEquals(10L, countField.getAnnotation(javax.validation.constraints.Max::class.java).value)
    val constructor = model.constructors.single { it.parameterCount == 4 }
    val valid = listOf("valid", listOf("a"), 5, "ID-ABC")
    val fields = listOf("name", "tags", "count", "id")
    val payload = fields.zip(valid).toMap()
    val constructed = constructor.newInstance(*valid.toTypedArray())
    assertEquals(mapper.valueToTree(payload), mapper.valueToTree(constructed))
    assertEquals(mapper.valueToTree(payload), mapper.valueToTree(mapper.convertValue(payload, model)))
    mapper.readValue("""{"name":"valid"}""", model)
    constructor.newInstance("valid", null, null, null)
    for (field in listOf("tags", "count", "id")) {
      val failure =
        assertThrows(IllegalArgumentException::class.java) {
          mapper.convertValue(payload + (field to null), model)
        }
      assertTrue(failure.message!!.contains(field))
    }
    for ((field, invalid) in listOf(
      "name" to "a",
      "name" to "abcdef",
      "name" to "BAD",
      "tags" to emptyList<String>(),
      "tags" to listOf("a", "b", "c"),
      "count" to -1,
      "count" to 11,
      "id" to "invalid",
    )) {
      val arguments = valid.toMutableList().apply { this[fields.indexOf(field)] = invalid }
      val failure =
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
          constructor.newInstance(*arguments.toTypedArray())
        }
      assertTrue(failure.cause!!.message!!.contains(field))
      val decodeFailure =
        assertThrows(IllegalArgumentException::class.java) {
          mapper.convertValue(payload + (field to invalid), model)
        }
      assertTrue(decodeFailure.message!!.contains(field))
    }
  }
}
