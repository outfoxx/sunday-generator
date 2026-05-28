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
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions.BaseUriMode
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.io.path.createTempDirectory

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

  @Test
  fun `non-Quarkus defaults to Zalando problem library`() {

    val command = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { command.parse(requiredOptions) }

    assertThat(command.typeRegistry.problemLibrary, equalTo(KotlinProblemLibrary.ZALANDO))
  }

  @Test
  fun `Quarkus defaults to Quarkus problem library`() {

    val command = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow { command.parse(arrayOf("-quarkus", *requiredOptions)) }

    assertThat(command.typeRegistry.problemLibrary, equalTo(KotlinProblemLibrary.QUARKUS))
  }

  @Test
  fun `aggregate and services from tags options`() {

    val command = KotlinJAXRSGenerateCommandTest()
    assertDoesNotThrow {
      command.parse(
        arrayOf(
          "-services-from-tags",
          "-aggregate-services",
          "-aggregate-service-name",
          "TurnPostAPI",
          *requiredOptions,
        ),
      )
    }

    assertThat(command.servicesFromTags, equalTo(true))
    assertThat(command.aggregateServices, equalTo(true))
    assertThat(command.aggregateServiceName, equalTo("TurnPostAPI"))
  }

  @ParameterizedTest
  @CsvSource(
    "client,false",
    "client,true",
    "server,false",
    "server,true",
  )
  fun `generates composed OpenAPI and AsyncAPI JAX-RS services from CLI`(
    mode: String,
    quarkus: Boolean,
  ) {

    val openApi = KotlinJAXRSCLITest::class.java.getResource("/openapi-composed.yaml")!!.toURI()!!
    val asyncApi = KotlinJAXRSCLITest::class.java.getResource("/asyncapi-composed.yaml")!!.toURI()!!
    val tempDir = createTempDirectory("sunday-jaxrs-cli").toFile()
    tempDir.deleteOnExit()

    val args =
      buildList {
        add("-mode")
        add(mode)
        add("-services-from-tags")
        add("-aggregate-services")
        add("-aggregate-service-name")
        add("TurnPostAPI")
        add("-pkg")
        add("io.test")
        add("-out")
        add(tempDir.absolutePath)
        if (quarkus) {
          add("-quarkus")
        }
        add(openApi.path)
        add(asyncApi.path)
      }

    assertDoesNotThrow { KotlinJAXRSGenerateCommand().parse(args.toTypedArray()) }

    val outputPackageDir = tempDir.resolve("io/test")
    val aggregateApi = outputPackageDir.resolve("TurnPostAPI.kt")
    val projectsApi = outputPackageDir.resolve("ProjectsAPI.kt")
    val eventsApi = outputPackageDir.resolve("EventsAPI.kt")
    assertTrue(aggregateApi.isFile)
    assertTrue(projectsApi.isFile)
    assertTrue(eventsApi.isFile)
    assertTrue(outputPackageDir.resolve("Project.kt").isFile)
    assertTrue(outputPackageDir.resolve("EventEnvelope.kt").isFile)
    assertTrue(outputPackageDir.resolve("ProjectCreatedData.kt").isFile)

    assertTrue(eventsApi.readText().contains("@GET"))
    assertTrue(eventsApi.readText().contains("fun streamEvents"))
    if (quarkus) {
      assertTrue(aggregateApi.readText().contains("RestPath"))
    } else {
      assertTrue(aggregateApi.readText().contains("PathParam"))
    }
  }
}
