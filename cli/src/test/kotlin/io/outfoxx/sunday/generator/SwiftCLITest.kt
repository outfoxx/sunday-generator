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
import io.outfoxx.sunday.generator.swift.SwiftGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftGenerator
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SwiftCLITest {

  companion object {
    private val emptyFile = SwiftCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class SwiftGenerateCommandTest : SwiftGenerateCommand("Test", "testing command") {
    override fun generatorFactory(
      document: Document,
      shapeIndex: ShapeIndex,
      typeRegistry: SwiftTypeRegistry,
    ): SwiftGenerator {
      error("should not execute")
    }

    override fun run() {}
  }

  @ParameterizedTest
  @EnumSource(SwiftTypeRegistry.Option::class)
  fun `enable type registry options`(option: SwiftTypeRegistry.Option) {

    val command = SwiftGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.options, hasItems(option))
  }

  @ParameterizedTest
  @EnumSource(SwiftTypeRegistry.Option::class)
  fun `disable type registry options`(option: SwiftTypeRegistry.Option) {

    val command = SwiftGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-no-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.options, not(hasItems(option)))
  }

  @Test
  fun `enable & disable a type registry option`() {
    val option = SwiftTypeRegistry.Option.AddGeneratedHeader
    val optionName = option.name.camelCaseToKebabCase()
    val command = SwiftGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-$optionName", "-no-$optionName", *requiredOptions)) }
    assertThat(command.options, not(hasItems(option)))
  }
}
