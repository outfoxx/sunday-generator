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
import io.outfoxx.sunday.generator.typescript.TypeScriptGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TypeScriptCLITest {

  companion object {
    private val emptyFile = TypeScriptCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class TypeScriptGenerateCommandTest : TypeScriptGenerateCommand("Test", "testing command") {
    override fun generatorFactory(
      document: Document,
      shapeIndex: ShapeIndex,
      typeRegistry: TypeScriptTypeRegistry,
    ): TypeScriptGenerator {
      error("should not execute")
    }

    override fun run() {}
  }

  @ParameterizedTest
  @EnumSource(TypeScriptTypeRegistry.Option::class)
  fun `enable type registry options`(option: TypeScriptTypeRegistry.Option) {

    val command = TypeScriptGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.options, hasItems(option))
  }

  @ParameterizedTest
  @EnumSource(TypeScriptTypeRegistry.Option::class)
  fun `disable type registry options`(option: TypeScriptTypeRegistry.Option) {

    val command = TypeScriptGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-no-${option.name.camelCaseToKebabCase()}", *requiredOptions)) }
    assertThat(command.options, not(hasItems(option)))
  }

  @Test
  fun `enable & disable type registry option`() {
    val option = TypeScriptTypeRegistry.Option.AddGenerationHeader
    val optionName = option.name.camelCaseToKebabCase()
    val command = TypeScriptGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-$optionName", "-no-$optionName", *requiredOptions)) }
    assertThat(command.options, not(hasItems(option)))
  }
}
