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
import com.github.ajalt.clikt.testing.test
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class KotlinSundayCLITest {

  companion object {
    private val emptyFile = KotlinSundayCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-pkg",
        "io.test",
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class KotlinSundayGenerateCommandTest : KotlinSundayGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `--services-from-tags option`() {

    val commandWithTrue = KotlinSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-services-from-tags", *requiredOptions)) }
    assertThat(commandWithTrue.servicesFromTags, equalTo(true))

    val commandWithFalse = KotlinSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.servicesFromTags, equalTo(false))
  }

  @Test
  fun `--aggregate-services option`() {

    val commandWithTrue = KotlinSundayGenerateCommandTest()
    assertDoesNotThrow {
      commandWithTrue.parse(
        arrayOf(
          "-aggregate-services",
          "-aggregate-service-name",
          "TurnPostAPI",
          *requiredOptions,
        ),
      )
    }
    assertThat(commandWithTrue.aggregateServices, equalTo(true))
    assertThat(commandWithTrue.aggregateServiceName, equalTo("TurnPostAPI"))

    val commandWithFalse = KotlinSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.aggregateServices, equalTo(false))
    assertThat(commandWithFalse.aggregateServiceName, equalTo(null))
  }

  @Test
  fun `uses OpenAPI tags as Kotlin Sunday services when requested`() {

    val output = createTempDirectory("sunday-kotlin-cli")
    val openApi =
      createTempFile("sunday-kotlin-cli-openapi", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          paths:
            /projects/{projectId}:
              get:
                tags: [Projects]
                operationId: getProject
                parameters:
                  - name: projectId
                    in: path
                    required: true
                    schema:
                      type: string
                responses:
                  "204":
                    description: No content.
            /accounts/{accountId}:
              get:
                tags: [Accounts]
                operationId: getAccount
                parameters:
                  - name: accountId
                    in: path
                    required: true
                    schema:
                      type: string
                responses:
                  "204":
                    description: No content.
          """.trimIndent(),
        )
      }

    val result =
      KotlinSundayGenerateCommand()
        .test(
          arrayOf(
            "-pkg",
            "io.test",
            "-services-from-tags",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("io").resolve("test").resolve("API.kt")), equalTo(false))
    assertThat(Files.exists(output.resolve("io").resolve("test").resolve("ProjectsAPI.kt")), equalTo(true))
    assertThat(Files.exists(output.resolve("io").resolve("test").resolve("AccountsAPI.kt")), equalTo(true))
  }

  @Test
  fun `aggregates split Kotlin Sunday services into one root API`() {

    val output = createTempDirectory("sunday-kotlin-cli")
    val openApi =
      createTempFile("sunday-kotlin-cli-openapi", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          paths:
            /users/me:
              x-sunday-service: users
              get:
                tags: [Users]
                operationId: getCurrentUser
                responses:
                  "204":
                    description: No content.
            /projects/{projectId}:
              x-sunday-service: projects
              get:
                tags: [Projects]
                operationId: getProject
                parameters:
                  - name: projectId
                    in: path
                    required: true
                    schema:
                      type: string
                responses:
                  "204":
                    description: No content.
          """.trimIndent(),
        )
      }

    val result =
      KotlinSundayGenerateCommand()
        .test(
          arrayOf(
            "-pkg",
            "io.test",
            "-aggregate-services",
            "-aggregate-service-name",
            "TurnPostAPI",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("io").resolve("test").resolve("API.kt")), equalTo(false))
    assertThat(Files.exists(output.resolve("io").resolve("test").resolve("TurnPostAPI.kt")), equalTo(true))
    val aggregateSource = Files.readString(output.resolve("io").resolve("test").resolve("TurnPostAPI.kt"))
    assertThat(aggregateSource, containsString("public val transport: Transport"))
    assertThat(aggregateSource, containsString("public val defaultContentTypes: List<MediaType>"))
    assertThat(aggregateSource, containsString("public val defaultAcceptTypes: List<MediaType>"))
    assertThat(aggregateSource, containsString("public val users: UsersAPI"))
    assertThat(aggregateSource, containsString("public val projects: ProjectsAPI"))
    assertThat(aggregateSource, containsString("defaultContentTypes: List<MediaType> = listOf()"))
    assertThat(aggregateSource, containsString("defaultAcceptTypes: List<MediaType> = listOf()"))
    assertThat(aggregateSource, containsString("this.users = UsersAPI("))
    assertThat(aggregateSource, containsString("this.projects = ProjectsAPI("))
    assertThat(aggregateSource, containsString("defaultContentTypes = defaultContentTypes"))
    assertThat(aggregateSource, containsString("defaultAcceptTypes = defaultAcceptTypes"))
  }
}
