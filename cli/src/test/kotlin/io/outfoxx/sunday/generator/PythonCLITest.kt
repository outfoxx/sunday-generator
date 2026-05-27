/*
 * Copyright 2026 Outfox, Inc.
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
import io.outfoxx.sunday.generator.python.PythonHttpxGenerateCommand
import io.outfoxx.sunday.generator.python.PythonLitestarGenerateCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class PythonCLITest {

  companion object {
    private val emptyFile = PythonCLITest::class.java.getResource("/empty.raml")!!.toURI()!!
    private val requiredOptions =
      arrayOf(
        "-out",
        emptyFile.resolve("..").path,
        emptyFile.path,
      )
  }

  class PythonHttpxGenerateCommandTest : PythonHttpxGenerateCommand() {
    override fun run() {}
  }

  @Test
  fun `python shared options`() {
    val command = PythonHttpxGenerateCommandTest()
    assertDoesNotThrow {
      command.parse(
        arrayOf(
          "-pkg",
          "turnpost_api",
          "-services-from-tags",
          "-aggregate",
          "-aggregate-service-name",
          "TurnPostAPI",
          *requiredOptions,
        ),
      )
    }

    assertThat(command.packageName, equalTo("turnpost_api"))
    assertThat(command.servicesFromTags, equalTo(true))
    assertThat(command.aggregateServices, equalTo(true))
    assertThat(command.aggregateServiceName, equalTo("TurnPostAPI"))
  }

  @Test
  fun `generates httpx client files from OpenAPI tags`() {
    val output = createTempDirectory("sunday-python-httpx-cli")
    val openApi = taggedOpenApi()

    val result =
      PythonHttpxGenerateCommand()
        .test(
          arrayOf(
            "-pkg",
            "turnpost_api",
            "-services-from-tags",
            "-aggregate",
            "-aggregate-service-name",
            "TurnPostAPI",
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("__init__.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("models.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("runtime.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("users.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("projects.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("api.py")), equalTo(true))
  }

  @Test
  fun `uses Sunday API id as default Python package directory`() {
    val output = createTempDirectory("sunday-python-api-id-cli")
    val openApi = taggedOpenApi()

    val result =
      PythonHttpxGenerateCommand()
        .test(
          arrayOf(
            "-out",
            output.toString(),
            openApi.toString(),
          ),
        )

    assertThat(result.statusCode, equalTo(0))
    assertThat(Files.exists(output.resolve("turnpost").resolve("__init__.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost").resolve("api.py")), equalTo(false))
  }

  @Test
  fun `generates Litestar server files from OpenAPI tags`() {
    val output = createTempDirectory("sunday-python-litestar-cli")
    val openApi = taggedOpenApi()

    val result =
      PythonLitestarGenerateCommand()
        .test(
          arrayOf(
            "-package",
            "turnpost_api",
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
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("__init__.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("models.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("users_server.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("projects_server.py")), equalTo(true))
    assertThat(Files.exists(output.resolve("turnpost_api").resolve("api_server.py")), equalTo(true))
  }

  private fun taggedOpenApi() =
    createTempFile("sunday-python-cli-openapi", ".yaml").apply {
      writeText(
        """
        openapi: 3.1.0
        info:
          title: TurnPost API
          version: 1.0.0
        x-sunday-apiId: turnpost
        paths:
          /users/{userId}:
            get:
              tags: [Users]
              operationId: getUser
              parameters:
                - name: userId
                  in: path
                  required: true
                  schema:
                    type: string
              responses:
                "200":
                  description: User response.
                  content:
                    application/json:
                      schema:
                        ${'$'}ref: "#/components/schemas/UserView"
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
                        ${'$'}ref: "#/components/schemas/ProjectView"
        components:
          schemas:
            UserView:
              type: object
              required: [userId]
              properties:
                userId:
                  type: string
            ProjectView:
              type: object
              required: [projectId]
              properties:
                projectId:
                  type: string
        """.trimIndent(),
      )
    }
}
