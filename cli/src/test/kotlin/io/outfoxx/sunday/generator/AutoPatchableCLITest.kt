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
import com.github.ajalt.clikt.testing.test
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import io.outfoxx.sunday.generator.python.PythonLitestarGenerateCommand
import io.outfoxx.sunday.generator.python.PythonSundayGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class AutoPatchableCLITest {
  @Test
  fun `all targets enable automatic promotion by default and accept both flags`() {
    val source = requireNotNull(javaClass.getResource("/empty.raml")).toURI()
    for ((flags, expected) in listOf(
      emptyList<String>() to true,
      listOf("-auto-patchable") to true,
      listOf("-no-auto-patchable") to false,
      listOf("-no-auto-patchable", "-auto-patchable") to true,
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
        assertEquals(expected, command.autoPatchable, command.commandName)
      }
    }
  }

  @Test
  fun `IR export disables inference without disabling explicit patchable annotations`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    val output = directory.resolve("api.ir.yaml")
    for (annotated in listOf(false, true)) {
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Items, version: '1'}
        paths:
          /items:
            post:
              operationId: updateItem
              requestBody:
                content:
                  application/merge-patch+json:
                    schema: {${'$'}ref: '#/components/schemas/Item'}
              responses: {'204': {description: Updated}}
        components:
          schemas:
            Item:
              type: object
              x-sunday-patchable: $annotated
              properties: {name: {type: string}}
        """.trimIndent(),
      )
      for ((flags, automatic) in listOf(emptyList<String>() to true, listOf("-no-auto-patchable") to false)) {
        val result = IrCommand().test((flags + listOf("-out", output.toString(), source.toString())).toTypedArray())
        assertEquals(0, result.statusCode, result.output)
        val api = GeneratedApiYaml.readPath(output)
        assertEquals(
          if (automatic || annotated) "ItemPatch" else "Item",
          api.services
            .single()
            .operations
            .single()
            .requestBody!!
            .type.name,
        )
        assertEquals(automatic || annotated, api.models.any { it.name == "ItemPatch" })
      }
    }
  }
}
