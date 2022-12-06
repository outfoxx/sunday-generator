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
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class KotlinCLITest {

  companion object {
    private val emptyFile = KotlinCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-pkg", "io.test",
        "-out", emptyFile.resolve("..").path,
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
  fun `--generated-annotation option`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-generated-annotation", "io.test.Generated", *requiredOptions)) }
    assertThat(command.generatedAnnotationName, equalTo("io.test.Generated"))
  }

  @ParameterizedTest
  @EnumSource(KotlinTypeRegistry.Option::class)
  fun `--enable options`(option: KotlinTypeRegistry.Option) {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-enable", option.name.camelCaseToKebabCase(), *requiredOptions)) }
    assertThat(command.enabledOptions, hasItems(option))
    assertThat(command.options, hasItems(option))
  }

  @ParameterizedTest
  @EnumSource(KotlinTypeRegistry.Option::class)
  fun `--disable options`(option: KotlinTypeRegistry.Option) {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-disable", option.name.camelCaseToKebabCase(), *requiredOptions)) }
    assertThat(command.disabledOptions, hasItems(option))
    assertThat(command.options, not(hasItems(option)))
  }

  @Test
  fun `--enable & --disable option`() {

    val command = KotlinGenerateCommandTest()
    assertDoesNotThrow {
      command.parse(
        arrayOf("-disable", "add-generated-annotation", "-enable", "add-generated-annotation", *requiredOptions)
      )
    }
    assertThat(command.options, not(hasItems(KotlinTypeRegistry.Option.AddGeneratedAnnotation)))
  }
}
