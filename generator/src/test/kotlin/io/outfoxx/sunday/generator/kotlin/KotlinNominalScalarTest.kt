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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.nominalScalarApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinNominalScalarTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `nominal scalars validate and preserve union identity`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = nominalScalarApi(frontend, directory)
    for (jaxrs in listOf(false, true)) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          if (jaxrs) GenerationMode.Server else GenerationMode.Client,
          setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (jaxrs) {
        KotlinJAXRSIrGenerator(
          api,
          registry,
          kotlinJAXRSTestOptions,
        ).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      val mapper = jacksonObjectMapper()
      val fact = result.classLoader.loadClass("io.test.BaseFactSid")
      val union = result.classLoader.loadClass("io.test.AnySid")
      val ambiguous = result.classLoader.loadClass("io.test.AmbiguousSid")
      for ((value, branch) in listOf("sid:f:abc" to "BaseFactSid", "sid:l:abc" to "BaseLossSid")) {
        val decoded = mapper.readValue("\"$value\"", union)
        assertEquals(branch, decoded.javaClass.simpleName)
        assertEquals("\"$value\"", mapper.writeValueAsString(decoded))
        assertEquals(value, union.getMethod("fromString", String::class.java).invoke(null, value).toString())
      }
      assertThrows(Exception::class.java) { mapper.readValue("\"invalid\"", union) }
      assertThrows(Exception::class.java) { mapper.readValue("\"invalid\"", fact) }
      assertThrows(Exception::class.java) { fact.getConstructor(String::class.java).newInstance("invalid") }
      assertEquals("sid:f:abc", fact.getMethod("fromString", String::class.java).invoke(null, "sid:f:abc").toString())
      if (frontend != "raml") {
        assertThrows(Exception::class.java) { mapper.readValue("\"sid:f:abc\"", ambiguous) }
      } else {
        assertEquals("BaseFactSid", mapper.readValue("\"sid:f:abc\"", ambiguous).javaClass.simpleName)
      }
      val record = result.classLoader.loadClass("io.test.Record")
      val defaults = result.classLoader.loadClass("io.test.Defaults")
      val defaulted = mapper.readValue("{}", defaults)
      assertEquals(
        if (frontend ==
          "raml"
        ) {
          null
        } else {
          "sid:f:default"
        },
        defaults.getMethod("getFact").invoke(defaulted)?.toString(),
      )
      val wire = """{"fact":"sid:f:abc","identifiers":["sid:l:abc"],"count":2,"ratio":0.5,"enabled":true}"""
      val decoded = mapper.readValue(wire, record)
      assertEquals(fact, record.getMethod("getFact").invoke(decoded).javaClass)
      assertEquals(mapper.readTree(wire), mapper.readTree(mapper.writeValueAsString(decoded)))
      for ((name, invalid) in listOf("PositiveCount" to "0", "Ratio" to "2")) {
        assertThrows(Exception::class.java) { mapper.readValue(invalid, result.classLoader.loadClass("io.test.$name")) }
      }
      if (frontend == "openapi" && !jaxrs) {
        val invalidName = ClassName("io.test", "InvalidAssignments")
        val invalid = TypeSpec.objectBuilder(invalidName)
        for ((method, argument) in listOf("plain" to STRING, "other" to ClassName("io.test", "BaseLossSid"))) {
          invalid.addFunction(
            FunSpec
              .builder(method)
              .addParameter("value", argument)
              .returns(ClassName("io.test", "BaseFactSid"))
              .addStatement("return value")
              .build(),
          )
        }
        val rejected = compileTypesResult(registry.buildTypes() + (invalidName to invalid.build()))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, rejected.exitCode)
        assertTrue(rejected.messages.contains("Return type mismatch"), rejected.messages)
      }
    }
  }
}
