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
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.fieldConstraintsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
@Tag("validation")
@Tag("models")
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
    withNativeBeanValidation("javax", result.classLoader) {
      val mapper = jacksonObjectMapper()
      val model = result.classLoader.loadClass("io.test.Probe")
      val countField = if (target == "sunday") model.getDeclaredField("count") else model.getMethod("getCount")
      val constraints = countField.getAnnotation(io.outfoxx.sunday.validation.javax.Schema::class.java)
      assertEquals("0", constraints.minimum)
      assertEquals("10", constraints.maximum)
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
      val invalidValues =
        listOf(
          "name" to "a",
          "name" to "abcdef",
          "name" to "BAD",
          "tags" to emptyList<String>(),
          "tags" to listOf("a", "b", "c"),
          "count" to -1,
          "count" to 11,
          "id" to "invalid",
        ) + if (sourceKind == "asyncapi") listOf("id" to "ID-XYZ", "id" to "bad-ABC") else emptyList()
      for ((field, invalid) in invalidValues) {
        val arguments = valid.toMutableList().apply { this[fields.indexOf(field)] = invalid }
        val failure =
          assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            constructor.newInstance(*arguments.toTypedArray())
          }
        val violations = (failure.cause as javax.validation.ConstraintViolationException).constraintViolations
        assertTrue(
          violations.any { violation ->
            violation.propertyPath.any { node ->
              node.kind == javax.validation.ElementKind.PARAMETER &&
                node.`as`(javax.validation.Path.ParameterNode::class.java).parameterIndex == fields.indexOf(field)
            }
          },
          failure.cause.toString(),
        )
        val decodeFailure =
          assertThrows(IllegalArgumentException::class.java) {
            mapper.convertValue(payload + (field to invalid), model)
          }
        assertTrue(
          generateSequence(decodeFailure as Throwable?) {
            it.cause
          }.any { it is javax.validation.ConstraintViolationException },
          decodeFailure.toString(),
        )
      }
      if (sourceKind == "raml") {
        val arrays = result.classLoader.loadClass("io.test.NumericProbe")
        val arrayConstructor = arrays.constructors.single { it.parameterCount == 2 }
        val validArrays = mapOf("samples" to listOf(0, 5, 10), "optionalSamples" to listOf(1, 9))
        val instance = arrayConstructor.newInstance(validArrays["samples"], validArrays["optionalSamples"])
        assertEquals(mapper.valueToTree(validArrays), mapper.valueToTree(instance))
        assertEquals(mapper.valueToTree(validArrays), mapper.valueToTree(mapper.convertValue(validArrays, arrays)))
        arrayConstructor.newInstance(listOf(0, 10), null)
        mapper.readValue("""{"samples":[0,10]}""", arrays)
        for (invalid in listOf(listOf(-1), listOf(11), listOf(0, 11))) {
          for (field in listOf("samples", "optionalSamples")) {
            val payload = validArrays + (field to invalid)
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
              arrayConstructor.newInstance(payload["samples"], payload["optionalSamples"])
            }
            assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(payload, arrays) }
          }
        }
      }
    }
  }
}
