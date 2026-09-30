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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinExtensionCopyTest {
  @Test
  fun `data class copies preserve extras without sharing mutable storage`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.raml")
    source.writeText(
      """
      #%RAML 1.0
      title: Copies
      types:
        Record:
          type: object
          properties:
            name: string
        PatternRecord:
          type: object
          properties:
            name: string
            /^x-/: string
      /records:
        post:
          body:
            application/json: Record
        put:
          body:
            application/json: PatternRecord
      """.trimIndent(),
    )
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(
      GeneratedApiIrExporter().export(source.toUri()),
      registry,
      kotlinSundayTestOptions,
    ).generateServiceTypes()
    val checkType = ClassName("io.test", "CopyCheck")
    val check =
      TypeSpec
        .objectBuilder(checkType)
        .addFunction(
          FunSpec
            .builder("check")
            .returns(ClassName("io.test", "Record"))
            .addCode(
              """
              val original = Record("before")
              original.setAdditionalProperty("future", null)
              val copied = original.copy(name = "after")
              check(copied.additionalProperties.containsKey("future"))
              copied.setAdditionalProperty("future", 1)
              check(original.additionalProperties["future"] == null)
              check(runCatching { copied.setAdditionalProperty("name", "spoofed") }.exceptionOrNull() is IllegalArgumentException)
              return copied
              """.trimIndent(),
            ).build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (checkType to check))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val helper = result.classLoader.loadClass("io.test.CopyCheck")
    val copied = helper.getMethod("check").invoke(helper.getField("INSTANCE").get(null))
    val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    assertEquals(mapper.readTree("""{"name":"after","future":1}"""), mapper.readTree(mapper.writeValueAsBytes(copied)))
    for (name in listOf("Record", "PatternRecord")) {
      val type = result.classLoader.loadClass("io.test.$name")
      val input = mapper.readTree("""{"name":"before","x-value":"okay","future":null,"extensionFields":{"x":true}}""")
      assertEquals(input, mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(input, type))))
    }
    assertThrows(Exception::class.java) {
      mapper.readValue("""{"name":"before","x-value":null}""", result.classLoader.loadClass("io.test.PatternRecord"))
    }
  }
}
