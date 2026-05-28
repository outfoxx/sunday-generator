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
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class SwiftSundayCLITest {

  companion object {
    private val emptyFile = SwiftSundayCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class SwiftSundayGenerateCommandTest : SwiftSundayGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `--aggregate-services option`() {

    val commandWithTrue = SwiftSundayGenerateCommandTest()
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

    val commandWithFalse = SwiftSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.aggregateServices, equalTo(false))
    assertThat(commandWithFalse.aggregateServiceName, equalTo(null))
  }

  @Test
  fun `--services-from-tags option`() {

    val commandWithTrue = SwiftSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithTrue.parse(arrayOf("-services-from-tags", *requiredOptions)) }
    assertThat(commandWithTrue.servicesFromTags, equalTo(true))

    val commandWithFalse = SwiftSundayGenerateCommandTest()
    assertDoesNotThrow { commandWithFalse.parse(requiredOptions) }
    assertThat(commandWithFalse.servicesFromTags, equalTo(false))
  }

  @Test
  fun `uses OpenAPI tags to organize Swift files without splitting service APIs`() {

    val output = createTempDirectory("sunday-swift-cli")
    val openApi =
      createTempFile("sunday-swift-cli-openapi", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          x-sunday-apiId: craft
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
                  "200":
                    description: Project response.
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: "#/components/schemas/Project"
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
                  "200":
                    description: Account response.
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: "#/components/schemas/Account"
          components:
            schemas:
              Project:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: string
              Account:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: string
          """.trimIndent(),
        )
      }

    val result =
      SwiftSundayGenerateCommand()
        .test(
          arrayOf(
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    val source = Files.readString(output.resolve("API.swift"))
    assertThat(source, containsString("public func getProject("))
    assertThat(source, containsString("public func getAccount("))
    assertThat(
      Files.exists(output.resolve("Projects").resolve("Models").resolve("Project.swift")),
      equalTo(true),
    )
    assertThat(
      Files.exists(output.resolve("Accounts").resolve("Models").resolve("Account.swift")),
      equalTo(true),
    )
  }

  @Test
  fun `uses OpenAPI tags as Swift Sunday services when requested`() {

    val output = createTempDirectory("sunday-swift-cli")
    val openApi =
      createTempFile("sunday-swift-cli-openapi", ".yaml").apply {
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
      SwiftSundayGenerateCommand()
        .test(
          arrayOf(
            "-services-from-tags",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("API.swift")), equalTo(false))
    assertThat(Files.exists(output.resolve("ProjectsAPI.swift")), equalTo(true))
    assertThat(Files.exists(output.resolve("AccountsAPI.swift")), equalTo(true))
  }

  @Test
  fun `aggregates split Swift Sunday services into one root API`() {

    val output = createTempDirectory("sunday-swift-cli")
    val openApi =
      createTempFile("sunday-swift-cli-openapi", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          x-sunday-apiId: craft
          paths:
            /users/me:
              x-sunday-service: users
              get:
                tags: [Users]
                operationId: getCurrentUser
                responses:
                  "200":
                    description: Current user response.
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: "#/components/schemas/UserSelfResponse"
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
                  "200":
                    description: Project response.
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: "#/components/schemas/ProjectResponse"
          components:
            schemas:
              UserSelfResponse:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: string
              ProjectResponse:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: string
          """.trimIndent(),
        )
      }

    val result =
      SwiftSundayGenerateCommand()
        .test(
          arrayOf(
            "-aggregate-services",
            "-aggregate-service-name",
            "TurnPostAPI",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("API.swift")), equalTo(false))
    assertThat(Files.exists(output.resolve("TurnPostAPI.swift")), equalTo(true))
    val aggregateSource = Files.readString(output.resolve("TurnPostAPI.swift"))
    assertThat(aggregateSource, containsString("public final class TurnPostAPI<TransportType : Transport>"))
    assertThat(aggregateSource, containsString("public let transport: TransportType"))
    assertThat(aggregateSource, containsString("public let defaultContentTypes: [MediaType]"))
    assertThat(aggregateSource, containsString("public let defaultAcceptTypes: [MediaType]"))
    assertThat(aggregateSource, containsString("public let users: UsersAPI"))
    assertThat(aggregateSource, containsString("public let projects: ProjectsAPI"))
    assertThat(aggregateSource, containsString("defaultContentTypes: [MediaType] = []"))
    assertThat(aggregateSource, containsString("defaultAcceptTypes: [MediaType] = []"))
    assertThat(aggregateSource, containsString("self.defaultContentTypes = defaultContentTypes"))
    assertThat(aggregateSource, containsString("self.defaultAcceptTypes = defaultAcceptTypes"))
    assertThat(aggregateSource, containsString("self.users = UsersAPI("))
    assertThat(aggregateSource, containsString("self.projects = ProjectsAPI("))
    assertThat(aggregateSource, containsString("defaultContentTypes: defaultContentTypes"))
    assertThat(aggregateSource, containsString("defaultAcceptTypes: defaultAcceptTypes"))
    assertThat(Files.exists(output.resolve("UsersAPI.swift")), equalTo(true))
    assertThat(Files.exists(output.resolve("ProjectsAPI.swift")), equalTo(true))
    assertThat(
      Files.exists(output.resolve("Users").resolve("Responses").resolve("UserSelfResponse.swift")),
      equalTo(true),
    )
    assertThat(
      Files.exists(output.resolve("Projects").resolve("Responses").resolve("ProjectResponse.swift")),
      equalTo(true),
    )
  }
}
