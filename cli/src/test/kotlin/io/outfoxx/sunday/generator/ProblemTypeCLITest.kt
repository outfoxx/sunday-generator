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
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ProblemTypeCLITest {
  @TempDir
  lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["kotlin-sunday", "kotlin-jaxrs", "swift", "typescript", "python-sunday", "python-litestar"])
  fun `every target accepts Problem schemas without requiring authored RFC defaults`(target: String) {
    for (schema in listOf(
      "type: string",
      "type: string, default: 'https://example.com/custom'",
      "type: string, default: 'about:blank', enum: ['https://example.com/custom']",
    )) {
      val source = directory.resolve("problems.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Problems, version: '1'}
        paths: {}
        components:
          schemas:
            Problem:
              type: object
              required: [title, status]
              properties:
                type: {$schema}
                title: {type: string}
                status: {type: integer}
        """.trimIndent(),
      )
      for (selection in listOf(emptyList(), listOf("-category", "problems"))) {
        val command =
          when (target) {
            "kotlin-sunday" -> KotlinSundayGenerateCommand()
            "kotlin-jaxrs" -> KotlinJAXRSGenerateCommand()
            "swift" -> SwiftSundayGenerateCommand()
            "typescript" -> TypeScriptSundayGenerateCommand()
            "python-sunday" -> PythonSundayGenerateCommand()
            else -> PythonLitestarGenerateCommand()
          }
        val placement =
          if (target.startsWith("kotlin")) {
            listOf("--placement-config", placementConfig("test.problems"))
          } else {
            emptyList()
          }
        assertDoesNotThrow {
          command.parse(
            (
              selection + placement +
                listOf(
                  "-out",
                  directory.resolve("out").createDirectories().toString(),
                  source.toString(),
                )
            ).toTypedArray(),
          )
        }
      }
    }
  }
}
