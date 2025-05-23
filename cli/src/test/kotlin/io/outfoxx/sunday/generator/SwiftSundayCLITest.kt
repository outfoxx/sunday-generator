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

import com.github.ajalt.clikt.core.parse
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SwiftSundayCLITest {

  companion object {
    private val emptyFile = SwiftSundayCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out", emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class SwiftSundayGenerateCommandTest : SwiftSundayGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `--use-result-response option`() {

    val commandWithTrue = SwiftSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-use-result-response-return", *requiredOptions)) }
    assertThat(commandWithTrue.useResultResponseReturn, equalTo(true))

    val commandWithFalse = SwiftSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.useResultResponseReturn, equalTo(false))
  }
}
