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
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinAsyncApiCustomTypeMappingTest {
  @ParameterizedTest
  @CsvSource(
    "2,Client,none,Identifier",
    "3,Client,none,Identifier",
    "2,Client,client,ClientIdentifier",
    "3,Client,client,ClientIdentifier",
    "2,Server,server,ServerIdentifier",
    "3,Server,server,ServerIdentifier",
    "2,Client,server,Identifier",
    "3,Server,client,Identifier",
  )
  fun `AsyncAPI models and brokers retain custom scalar signatures`(
    version: String,
    mode: GenerationMode,
    mappingMode: String,
    expectedType: String,
    @TempDir directory: Path,
  ) {
    val fixtures = Path.of("src/test/resources/asyncapi/ir/custom-types")
    directory.resolve("api.yaml").writeText(fixtures.resolve("api-$version.yaml").readText())
    directory.resolve("identifier.yaml").writeText(
      fixtures.resolve("identifier.yaml").readText() +
        if (mappingMode == "none") {
          ""
        } else {
          "\nx-sunday-kotlinType:$mappingMode: io.example.${mappingMode.replaceFirstChar {
            it.uppercase()
          }}Identifier\n"
        },
    )
    val api = AsyncApiToGeneratedApi().convertFragment(directory.resolve("api.yaml").toUri()).api
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        mode,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (mode == GenerationMode.Client) {
      KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(api.copy(services = emptyList()), registry, kotlinJAXRSTestOptions).generateServiceTypes()
    }
    val result =
      compileTypesResult(
        registry.buildTypes(),
        listOf("Identifier", expectedType).distinct().map { identifierSource(it) },
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val record = result.classLoader.loadClass("io.test.Record")
    val identifier = result.classLoader.loadClass("io.example.$expectedType")
    assertEquals(identifier, record.getMethod("getId").returnType)
    assertEquals(identifier, record.getMethod("getAlias").returnType)
    assertEquals("io.example.Identifier", record.getMethod("getLocal").returnType.name)
    assertEquals(String::class.java, record.getMethod("getPlain").returnType)
    if (mode == GenerationMode.Client) {
      val broker = result.classLoader.loadClass("io.test.service.EventsBroker")
      val method = broker.methods.single { it.name == "publishIdentifier" }
      assertEquals(identifier, method.parameterTypes.first())
    }
    val mapper = jacksonObjectMapper()
    val json = """{"id":"id:abc","alias":"id:def","local":"id:ghi","plain":"plain"}"""
    val recordValue = mapper.readValue(json, record)
    assertEquals("id:abc", record.getMethod("getId").invoke(recordValue).toString())
    val encoded = mapper.readTree(mapper.writeValueAsString(recordValue))
    mapper.readTree(json).fields().forEachRemaining { (key, value) -> assertEquals(value, encoded[key]) }
    assertThrows(Exception::class.java) { mapper.readValue("""{"id":"invalid"}""", record) }
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
      }
      """.trimIndent(),
    )
}
