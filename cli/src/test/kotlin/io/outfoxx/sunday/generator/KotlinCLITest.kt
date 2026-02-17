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
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsStringIgnoringCase
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.core.IsIterableContaining.hasItems
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.createTempDirectory

class KotlinCLITest {

  companion object {
    private val emptyFile = KotlinCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-pkg",
        "io.test",
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class KotlinGenerateCommandTest : KotlinGenerateCommand("Test", "testing command") {
    override val mode: GenerationMode
      get() = error("should not execute")

    override fun generatorFactory(
      document: Document,
      shapeIndex: ShapeIndex,
      typeRegistry: KotlinTypeRegistry,
    ): KotlinGenerator {
      error("should not execute")
    }

    override fun run() {}
  }

  @Test
  fun `--pkg option`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(requiredOptions) }
    assertThat(command.packageName, equalTo("io.test"))
  }

  @Test
  fun `--pkg option not required when annotations present`() {

    val testFile = KotlinCLITest::class.java.getResource("/test-pkgs.raml")!!.toURI()!!

    val tempDir = createTempDirectory("sunday-cli").toFile()
    tempDir.deleteOnExit()

    val command = KotlinJAXRSGenerateCommand()
    val options =
      arrayOf(
        "-out",
        tempDir.absolutePath,
        testFile.path,
      )
    assertDoesNotThrow { command.parse(options) }
  }

  @Test
  fun `--pkg option missing and no annotation, generation fails`() {

    val testFile = KotlinCLITest::class.java.getResource("/test.raml")!!.toURI()!!

    val tempDir = createTempDirectory("sunday-cli").toFile()
    tempDir.deleteOnExit()

    val command = KotlinJAXRSGenerateCommand()
    val options =
      arrayOf(
        "-out",
        tempDir.absolutePath,
        testFile.path,
      )

    val ex = assertThrows<GenerationException> { command.parse(options) }

    assertThat(ex.message, containsStringIgnoringCase("no service package"))
  }

  @Test
  fun `--model-pkg option`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-model-pkg", "io.test.model", *requiredOptions)) }
    assertThat(command.modelPackageName, equalTo("io.test.model"))
  }

  @Test
  fun `--service-pkg option`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-service-pkg", "io.test.service", *requiredOptions)) }
    assertThat(command.servicePackageName, equalTo("io.test.service"))
  }

  @Test
  fun `--service-pkg option and --pkg option missing, generation fails`() {

    val testFile = KotlinCLITest::class.java.getResource("/test.raml")!!.toURI()!!

    val tempDir = createTempDirectory("sunday-cli").toFile()
    tempDir.deleteOnExit()

    val command = KotlinJAXRSGenerateCommand()
    val options =
      arrayOf(
        "-service-pkg",
        "io.test",
        "-out",
        tempDir.absolutePath,
        testFile.path,
      )

    val ex = assertThrows<GenerationException> { command.parse(options) }

    assertThat(ex.message, containsStringIgnoringCase("no model package"))
  }

  @Test
  fun `--generated-annotation option (implies AddGeneratedAnnotation)`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-generated-annotation", "io.test.Generated", *requiredOptions)) }
    assertThat(command.generatedAnnotationName, equalTo("io.test.Generated"))
    assertThat(command.allRegistryOptions(), hasItems(KotlinTypeRegistry.Option.AddGeneratedAnnotation))
  }

  @ParameterizedTest
  @EnumSource(KotlinTypeRegistry.Option::class)
  fun `enable type registry options`(option: KotlinTypeRegistry.Option) {
    // Skip implied options
    if (option in KotlinGenerateCommand.impliedRegistryOptions) {
      return
    }

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.allRegistryOptions(), hasItems(option))
  }

  @ParameterizedTest
  @EnumSource(KotlinTypeRegistry.Option::class)
  fun `disable type registry options`(option: KotlinTypeRegistry.Option) {
    // Skip implied options
    if (option in KotlinGenerateCommand.impliedRegistryOptions) {
      return
    }

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-no-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.allRegistryOptions(), not(hasItems(option)))
  }

  @Test
  fun `enable & disable a type registry option`() {
    val option = KotlinTypeRegistry.Option.UseJakartaPackages
    val optionName = option.name.camelCaseToKebabCase()
    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-$optionName", "-no-$optionName", *requiredOptions)) }
    assertThat(command.allRegistryOptions(), not(hasItems(option)))
  }
}
