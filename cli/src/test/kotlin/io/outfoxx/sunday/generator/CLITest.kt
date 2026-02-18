/*
 * Copyright 2020 Outfox, Inc.
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

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import io.outfoxx.sunday.generator.common.ShapeIndex
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.io.path.toPath

class CLITest {

  companion object {
    private val emptyFile = CLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class GenerateCommandTest : CommonGenerateCommand("Test", "testing command") {
    override val typeRegistry: TypeRegistry
      get() = error("should not be called")

    override fun generatorFactory(
      document: Document,
      shapeIndex: ShapeIndex,
    ): Generator {
      error("should not be called")
    }

    override fun run() {}
  }

  @Test
  fun `files options`() {

    val command = GenerateCommandTest()
    assertDoesNotThrow { command.parse(requiredOptions) }
    assertThat(command.files, hasItem(emptyFile.toPath().toFile()))
  }

  @Test
  fun `--out option`() {

    val command = GenerateCommandTest()
    assertDoesNotThrow { command.parse(requiredOptions) }
    assertThat(command.outputDirectory, equalTo(emptyFile.resolve("..").toPath().toFile()))
  }

  @Test
  fun `--service-suffix option`() {

    val command = GenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-service-suffix", "Service", *requiredOptions)) }
    assertThat(command.serviceSuffix, equalTo("Service"))
  }

  @Test
  fun `--media-type options order`() {

    val command1 = GenerateCommandTest()
    assertDoesNotThrow {
      command1.parse(arrayOf("-media-type", "application/json", "-media-type", "application/cbor", *requiredOptions))
    }
    assertThat(command1.mediaTypes, contains("application/json", "application/cbor"))

    val command2 = GenerateCommandTest()
    assertDoesNotThrow {
      command2.parse(arrayOf("-media-type", "application/cbor", "-media-type", "application/json", *requiredOptions))
    }
    assertThat(command2.mediaTypes, contains("application/cbor", "application/json"))
  }

  @Test
  fun `--category options`() {

    val command = GenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-category", "service", "-category", "model", *requiredOptions)) }
    assertThat(command.outputCategories, contains(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
  }

  @Test
  fun `--problem-base option`() {

    val command = GenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-problem-base", "http://example.com/docs", *requiredOptions)) }
    assertThat(command.problemBaseUri, equalTo("http://example.com/docs"))
  }

  @Test
  fun `--version option`() {

    val command = GenerateCommandTest()
    val result = assertDoesNotThrow { command.versionOption().test(arrayOf("--version", *requiredOptions)) }
    assertThat(result.stdout, containsString("Sunday - Generator"))
    assertThat(result.stdout, containsString("ver. unknown"))
    assertThat(result.stdout, containsString("Supports:"))
  }

  @Test
  fun `--help option`() {

    val command = GenerateCommand()
    val result = assertDoesNotThrow { command.test(arrayOf("--help")) }
    assertThat(result.stdout, containsString("RAML definitions"))
  }
}
