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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.enumNamingApi
import io.outfoxx.sunday.generator.tools.enumNamingUpperCamelNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@KotlinTest
@Tag("models")
class KotlinEnumNamingTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `maps enum words and preserves wire round trips in both Kotlin targets`(source: String) {
    val api = enumNamingApi(source)
    listOf(GenerationMode.Client, GenerationMode.Server).forEach { mode ->
      val registry =
        KotlinTypeRegistry(
          null,
          mode,
          setOf(KotlinTypeRegistry.Option.JacksonAnnotations),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        )
      if (mode == GenerationMode.Client) {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      } else {
        KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      }
      val compilation = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compilation.exitCode)
      compilation.classLoader.use { loader ->
        for (name in listOf("JobKind", "TolerantKind")) {
          val type = loader.loadClass("io.test.$name")
          val fromValue = type.getMethod("fromValue", String::class.java)
          val mapper = jacksonObjectMapper()
          api.models.single { it.name == name }.values.forEach { wire ->
            val member = fromValue.invoke(null, wire)
            assertEquals(wire, member.toString())
            assertEquals("\"$wire\"", mapper.writeValueAsString(member))
            assertEquals(member, mapper.readValue("\"$wire\"", type))
          }
          if (name == "JobKind") {
            assertEquals(enumNamingUpperCamelNames, type.enumConstants.map { (it as Enum<*>).name })
          } else {
            assertEquals(
              enumNamingUpperCamelNames.toSet(),
              type.declaredClasses
                .map { it.simpleName }
                .filterNot { it == "Companion" }
                .toSet(),
            )
            assertEquals("future-value", fromValue.invoke(null, "future-value").toString())
          }
        }
      }
    }
  }
}
