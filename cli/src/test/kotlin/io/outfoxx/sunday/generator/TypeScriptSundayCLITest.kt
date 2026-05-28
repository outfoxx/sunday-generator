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
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class TypeScriptSundayCLITest {

  companion object {
    private val emptyFile = TypeScriptSundayCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class TypeScriptSundayGenerateCommandTest : TypeScriptSundayGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `--aggregate-services option`() {

    val commandWithTrue = TypeScriptSundayGenerateCommandTest()
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

    val commandWithFalse = TypeScriptSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.aggregateServices, equalTo(false))
    assertThat(commandWithFalse.aggregateServiceName, equalTo(null))
  }

  @Test
  fun `--services-from-tags option`() {

    val commandWithTrue = TypeScriptSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-services-from-tags", *requiredOptions)) }
    assertThat(commandWithTrue.servicesFromTags, equalTo(true))

    val commandWithFalse = TypeScriptSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.servicesFromTags, equalTo(false))
  }

  @Test
  fun `aggregates split TypeScript Sunday services from OpenAPI tags`() {

    val output = createTempDirectory("sunday-typescript-cli")
    val openApi =
      createTempFile("sunday-typescript-cli-openapi", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          paths:
            /users/me:
              get:
                tags: [Users]
                operationId: getCurrentUser
                responses:
                  "204":
                    description: No content.
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
          """.trimIndent(),
        )
      }

    val result =
      TypeScriptSundayGenerateCommand()
        .test(
          arrayOf(
            "-services-from-tags",
            "-aggregate-services",
            "-aggregate-service-name",
            "TurnPostAPI",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("api.ts")), equalTo(false))
    assertThat(Files.exists(output.resolve("turn-post-api.ts")), equalTo(true))
    assertThat(Files.exists(output.resolve("users-api.ts")), equalTo(true))
    assertThat(Files.exists(output.resolve("projects-api.ts")), equalTo(true))

    val aggregateSource = Files.readString(output.resolve("turn-post-api.ts"))
    assertThat(aggregateSource, containsString("export interface TurnPostAPI<Factory extends SundayTransport>"))
    assertThat(aggregateSource, containsString("class TurnPostAPIClient<Factory extends SundayTransport>"))
    assertThat(aggregateSource, containsString("users: UsersAPI"))
    assertThat(aggregateSource, containsString("projects: ProjectsAPI"))
    assertThat(aggregateSource, containsString("options?.defaultContentTypes"))
    assertThat(aggregateSource, containsString("createUsersAPI(transport"))
    assertThat(aggregateSource, containsString("defaultAcceptTypes: this.defaultAcceptTypes"))
  }
}
