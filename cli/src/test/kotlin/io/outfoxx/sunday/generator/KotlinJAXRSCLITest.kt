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
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator.Options.BaseUriMode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class KotlinJAXRSCLITest {

  companion object {
    private val emptyFile = KotlinJAXRSCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-pkg",
        "io.test",
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class KotlinJAXRSGenerateCommandTest : KotlinJAXRSGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `--mode option`() {

    val commandWithClient = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithClient.parse(arrayOf("-mode", "client", *requiredOptions)) }
    assertThat(commandWithClient.mode, equalTo(GenerationMode.Client))

    val commandWithServer = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithServer.parse(arrayOf("-mode", "server", *requiredOptions)) }
    assertThat(commandWithServer.mode, equalTo(GenerationMode.Server))
  }

  @Test
  fun `--coroutines flag`() {

    val commandWithTrue = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-coroutines", *requiredOptions)) }
    assertThat(commandWithTrue.coroutineServiceMethods, equalTo(true))

    val commandWithFalse = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.coroutineServiceMethods, equalTo(false))
  }

  @Test
  fun `--flow-coroutines flag`() {

    val commandWithTrue = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-flow-coroutines", *requiredOptions)) }
    assertThat(commandWithTrue.coroutineFlowMethods, equalTo(true))

    val commandWithFalse = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.coroutineServiceMethods, equalTo(false))
  }

  @Test
  fun `--reactive flag`() {

    val command = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-reactive", "io.test.Observable", *requiredOptions)) }
    assertThat(command.reactiveResponseType, equalTo("io.test.Observable"))
  }

  @Test
  fun `--explicit-security-parameters flag`() {

    val commandWithTrue = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-explicit-security-parameters", *requiredOptions)) }
    assertThat(commandWithTrue.explicitSecurityParameters, equalTo(true))

    val commandWithFalse = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.explicitSecurityParameters, equalTo(false))
  }

  @Test
  fun `--always-use-response-return flag`() {

    val commandWithTrue = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-always-use-response-return", *requiredOptions)) }
    assertThat(commandWithTrue.alwaysUseResponseReturn, equalTo(true))

    val commandWithFalse = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.alwaysUseResponseReturn, equalTo(false))
  }

  @Test
  fun `--base-uri-mode option`() {

    val command1 = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { command1.parse(arrayOf("-base-uri-path-mode", "path-only", *requiredOptions)) }
    assertThat(command1.baseUriPathMode, equalTo(BaseUriMode.PATH_ONLY))

    val command2 = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { command2.parse(arrayOf("-base-uri-path-mode", "full", *requiredOptions)) }
    assertThat(command2.baseUriPathMode, equalTo(BaseUriMode.FULL))
  }

  @Test
  fun `--quarkus flag`() {

    val commandWithTrue = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-quarkus", *requiredOptions)) }
    assertThat(commandWithTrue.quarkus, equalTo(true))

    val commandWithFalse = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.quarkus, equalTo(false))
  }
}
