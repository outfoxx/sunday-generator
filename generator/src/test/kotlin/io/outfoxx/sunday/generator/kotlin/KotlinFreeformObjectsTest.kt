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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@KotlinTest
@Tag("models")
@Tag("validation")
class KotlinFreeformObjectsTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(strings = ["jaxrs-client", "jaxrs-server", "sunday"])
  fun `inline and named freeform objects preserve JSON object values`(
    target: String,
    @ResourceUri("openapi/ir/freeform-objects.yaml") uri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(uri)
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target == "jaxrs-server") GenerationMode.Server else GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (target == "sunday") {
      KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    }
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val model = compiled.classLoader.loadClass("io.test.Document")
    val mapper = jacksonObjectMapper()
    val objectValue =
      """{"name":"example","enabled":true,"count":3,"fraction":1.5,"nullValue":null,"nested":{"value":null},"items":[1,"two",null]}"""
    for (field in listOf("properties", "named", "items")) {
      val value = if (field == "items") "[$objectValue,{}]" else objectValue
      val original = mapper.readTree("""{"$field":$value}""")
      val decoded = mapper.treeToValue(original, model)
      if (field != "items") {
        val getter = model.getMethod("get" + field.replaceFirstChar(Char::uppercase))
        assertEquals(Map::class.java, getter.returnType)
      }
      assertEquals(original, mapper.readTree(mapper.writeValueAsBytes(decoded)))
      for (invalid in listOf("\"{}\"", "true", "3")) {
        val invalidValue = if (field == "items") "[$invalid]" else invalid
        assertThrows(Exception::class.java) { mapper.readValue("""{"$field":$invalidValue}""", model) }
      }
    }
    for (json in listOf(
      """{"child":{"id":"one","enabled":true}}""",
      "{}",
      """{"properties":{},"named":{},"items":[]}""",
    )) {
      assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(json, model))))
    }
    for (field in listOf("properties", "named", "items")) {
      assertThrows(Exception::class.java) { mapper.readValue("""{"$field":null}""", model) }
    }
  }
}
