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
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.optionalSerializationApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinOptionalSerializationTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(strings = ["jaxrs-client", "jaxrs-server", "sunday"])
  fun `optional non-nullable properties omit null but preserve all non-null values`(
    target: String,
    @TempDir directory: Path,
  ) {
    val api = optionalSerializationApi(directory)
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target == "jaxrs-server") GenerationMode.Server else GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
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
    val callsName = ClassName("io.test", "Calls")
    val calls =
      TypeSpec
        .objectBuilder(callsName)
        .addFunction(
          FunSpec
            .builder("unset")
            .returns(ClassName("io.test", "Request"))
            .addStatement("return Request(name = %S, requiredNullable = null)", "test")
            .build(),
        ).addFunction(
          FunSpec
            .builder("strict")
            .returns(ClassName("io.test", "StrictRequest"))
            .addStatement("return StrictRequest(name = %S)", "test")
            .build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (callsName to calls))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val mapper = jacksonObjectMapper()
    val aliasType = result.classLoader.loadClass("io.test.AliasRequest")
    val aliasJson = """{"nullableAlias":null,"anyValue":null}"""
    assertEquals(
      mapper.readTree(aliasJson),
      mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(aliasJson, aliasType))),
    )
    val callsType = result.classLoader.loadClass("io.test.Calls")
    val callsInstance = callsType.getField("INSTANCE").get(null)
    val requestType = result.classLoader.loadClass("io.test.Request")
    val unset = callsType.getMethod("unset").invoke(callsInstance)
    val expected = mapper.readTree("""{"name":"test","requiredNullable":null,"optionalNullable":null}""")
    assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(unset)))
    val decoded = mapper.readValue(mapper.writeValueAsBytes(unset), requestType)
    assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(decoded)))
    for (json in listOf(
      """{"name":"test","requiredNullable":null,"optionalNullable":null,"text":"","number":0,"flag":false,"items":[]}""",
      """{"name":"test","requiredNullable":"yes","optionalNullable":"yes","text":"main","number":1,"flag":true,"items":["item"]}""",
    )) {
      val value = mapper.readValue(json, requestType)
      assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsBytes(value)))
    }
    assertThrows(Exception::class.java) { mapper.readValue("{}", requestType) }
    assertThrows(Exception::class.java) { mapper.readValue("""{"name":null}""", requestType) }

    val strict = mapper.copy().enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
    val strictType = result.classLoader.loadClass("io.test.StrictRequest")
    val strictValue = callsType.getMethod("strict").invoke(callsInstance)
    val strictJson = strict.writeValueAsBytes(strictValue)
    assertEquals(mapper.readTree("""{"name":"test"}"""), mapper.readTree(strictJson))
    strict.readValue(strictJson, strictType)
    strict.readValue("""{"name":"test","text":"main"}""", strictType)
    assertThrows(Exception::class.java) { strict.readValue("""{"name":"test","text":null}""", strictType) }
    assertThrows(Exception::class.java) { strict.readValue("{}", strictType) }
  }
}
