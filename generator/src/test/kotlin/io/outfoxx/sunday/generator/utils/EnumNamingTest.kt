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

package io.outfoxx.sunday.generator.utils

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.kotlin.utils.kotlinEnumEntries
import io.outfoxx.sunday.generator.python.pythonEnumMemberName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EnumNamingTest {
  @ParameterizedTest
  @CsvSource(
    "BACKGROUND_TASK, BackgroundTask, background_task",
    "background_task, BackgroundTask, background_task",
    "BackgroundTask, BackgroundTask, background_task",
    "backgroundTask, BackgroundTask, background_task",
    "background-task, BackgroundTask, background_task",
    "background.task, BackgroundTask, background_task",
    "background task, BackgroundTask, background_task",
    "render:character_reference, RenderCharacterReference, render_character_reference",
    "HTTPServer, HttpServer, http_server",
    "parseURLValue, ParseUrlValue, parse_url_value",
    "HTTP2_RESPONSE, Http2Response, http2_response",
    "version2Value, Version2Value, version2_value",
    "USER_2_TASK, User2Task, user_2_task",
    "IMMEDIATE, Immediate, immediate",
    "__BACKGROUND--TASK__, BackgroundTask, background_task",
    "CLASS, Class, class_",
    "123ABC, 123Abc, _123_abc",
  )
  fun `detects words before applying language casing`(
    wire: String,
    upperCamel: String,
    snake: String,
  ) {
    assertEquals(upperCamel, wire.enumUpperCamelCase())
    assertEquals(snake, wire.pythonEnumMemberName)
  }

  @Test
  fun `rejects newly colliding normalized Kotlin members with explicit name guidance`() {
    val model =
      GeneratedModel(
        name = "JobKind",
        kind = GeneratedModel.Kind.ENUM,
        values = listOf("BACKGROUND_TASK", "backgroundTask"),
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
      )
    val error = assertThrows(GenerationException::class.java) { model.kotlinEnumEntries() }
    assertTrue(error.message!!.contains("constant name 'BackgroundTask' is used for multiple values"))
    assertTrue(error.message!!.contains("x-enum-varnames"))
    assertEquals(
      listOf("LegacyTask", "CurrentTask"),
      model.copy(enumValueNames = listOf("LEGACY_TASK", "currentTask")).kotlinEnumEntries().map { it.name },
    )
  }

  @Test
  fun `rejects Companion including explicit names and unknown value sentinels`() {
    for (unknownValue in listOf(null, "UNKNOWN", "COMPANION")) {
      for (explicitNames in listOf(emptyList(), listOf("Companion", "Unknown"))) {
        val model =
          GeneratedModel(
            name = "JobKind",
            kind = GeneratedModel.Kind.ENUM,
            values = listOf("COMPANION", "UNKNOWN"),
            enumValueNames = explicitNames,
            unknownValue = unknownValue,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          )
        val error = assertThrows(GenerationException::class.java) { model.kotlinEnumEntries() }
        assertTrue(error.message!!.contains("constant name 'Companion' is reserved for the generated companion object"))
        assertTrue(error.message!!.contains("x-enum-varnames"))
      }
    }
  }
}
