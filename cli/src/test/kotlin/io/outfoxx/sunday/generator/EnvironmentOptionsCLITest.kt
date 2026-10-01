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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.parse
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import io.outfoxx.sunday.generator.python.PythonLitestarGenerateCommand
import io.outfoxx.sunday.generator.python.PythonSundayGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvironmentOptionsCLITest {
  @Test
  fun `all targets expose strict requests and explicit profiles consistently`() {
    val source = requireNotNull(javaClass.getResource("/empty.raml")).toURI()
    for ((flags, expected) in listOf(
      emptyList<String>() to RequestTolerance.Strict,
      listOf("-request-tolerance", "strict", "-profile", "external") to RequestTolerance.Strict,
      listOf("-request-tolerance", "tolerant", "-profile", "external") to RequestTolerance.Tolerant,
    )) {
      val commands =
        listOf(
          object : KotlinSundayGenerateCommand() {
            override fun run() = Unit
          },
          object : KotlinJAXRSGenerateCommand() {
            override fun run() = Unit
          },
          object : SwiftSundayGenerateCommand() {
            override fun run() = Unit
          },
          object : TypeScriptSundayGenerateCommand() {
            override fun run() = Unit
          },
          object : PythonSundayGenerateCommand() {
            override fun run() = Unit
          },
          object : PythonLitestarGenerateCommand() {
            override fun run() = Unit
          },
        )
      commands.forEach { command ->
        command.parse((flags + listOf("-out", source.resolve("..").path, source.path)).toTypedArray())
        assertEquals(expected, command.requestTolerance, command.commandName)
        assertEquals(if (flags.isEmpty()) null else "external", command.profile, command.commandName)
      }
    }
  }
}
