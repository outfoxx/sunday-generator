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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.parse
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import io.outfoxx.sunday.generator.python.PythonLitestarGenerateCommand
import io.outfoxx.sunday.generator.python.PythonSundayGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class BrokerCLITest {

  private val emptyFile = BrokerCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
  private val requiredOptions = arrayOf("-out", emptyFile.resolve("..").path, emptyFile.path)

  @Test
  fun `broker flags preserve target defaults and support explicit overrides`() {
    val kotlinDefault = TestKotlinSundayCommand().apply { parse(requiredOptions) }
    assertTrue(kotlinDefault.generateBrokerServices)

    val kotlinDisabled = TestKotlinSundayCommand().apply { parse(arrayOf("-no-broker", *requiredOptions)) }
    assertFalse(kotlinDisabled.generateBrokerServices)

    unsupportedTestCommands().forEach { command ->
      command.parse(requiredOptions)
      assertFalse(command.generateBrokerServices)
    }

    unsupportedTestCommands().forEach { command ->
      command.parse(arrayOf("-broker", *requiredOptions))
      assertTrue(command.generateBrokerServices)
    }

    unsupportedTestCommands().forEach { command ->
      command.parse(arrayOf("-no-broker", *requiredOptions))
      assertFalse(command.generateBrokerServices)
    }
  }

  @Test
  fun `unsupported CLI targets reject explicit broker generation`() {
    assertUnsupported(KotlinJAXRSGenerateCommand(), "Kotlin/JAX-RS")
    assertUnsupported(SwiftSundayGenerateCommand(), "Swift/Sunday")
    assertUnsupported(TypeScriptSundayGenerateCommand(), "TypeScript/Sunday")
    assertUnsupported(PythonSundayGenerateCommand(), "Python/Sunday")
    assertUnsupported(PythonLitestarGenerateCommand(), "Python/Litestar")
  }

  private fun assertUnsupported(
    command: CliktCommand,
    target: String,
  ) {
    val output = createTempDirectory("sunday-broker-cli")
    val source =
      createTempFile("sunday-broker-cli", ".yaml").apply {
        writeText(
          """
          asyncapi: 3.0.0
          info:
            title: Empty API
            version: 1.0.0
          channels: {}
          """.trimIndent(),
        )
      }
    val error =
      assertThrows(GenerationException::class.java) {
        command.parse(arrayOf("-broker", "-out", output.toString(), source.toString()))
      }
    assertEquals("Broker service generation is not supported for $target", error.message)
  }

  private fun unsupportedTestCommands(): List<CommonGenerateCommand> =
    listOf(
      TestKotlinJaxRsCommand(),
      TestSwiftSundayCommand(),
      TestTypeScriptSundayCommand(),
      TestPythonSundayCommand(),
      TestPythonLitestarCommand(),
    )

  private class TestKotlinSundayCommand : KotlinSundayGenerateCommand() {
    override fun run() = Unit
  }

  private class TestKotlinJaxRsCommand : KotlinJAXRSGenerateCommand() {
    override fun run() = Unit
  }

  private class TestSwiftSundayCommand : SwiftSundayGenerateCommand() {
    override fun run() = Unit
  }

  private class TestTypeScriptSundayCommand : TypeScriptSundayGenerateCommand() {
    override fun run() = Unit
  }

  private class TestPythonSundayCommand : PythonSundayGenerateCommand() {
    override fun run() = Unit
  }

  private class TestPythonLitestarCommand : PythonLitestarGenerateCommand() {
    override fun run() = Unit
  }
}
