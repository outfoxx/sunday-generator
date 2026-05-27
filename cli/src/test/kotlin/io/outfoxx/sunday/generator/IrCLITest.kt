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

import com.github.ajalt.clikt.testing.test
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class IrCLITest {

  @Test
  fun `exports RAML source to IR YAML`() {

    val source =
      createTempFile("sunday-ir-cli-source", ".raml").apply {
        writeText(
          """
          #%RAML 1.0
          title: Projects API
          mediaType:
          - application/json

          types:
            Project:
              type: object
              properties:
                id: string

          /projects:
            get:
              displayName: getProjects
              responses:
                200:
                  body:
                    application/json:
                      type: Project
          """.trimIndent(),
        )
      }
    val output = createTempFile("sunday-ir-cli", ".yaml")

    val result = IrCommand().test(arrayOf("-out", output.toString(), source.toString()))

    assertEquals(0, result.statusCode)
    assertThat(Files.readString(output), containsString("name: \"Projects API\""))
    assertThat(Files.readString(output), containsString("id: \"getProjects\""))
  }

  @Test
  fun `exports AsyncAPI source to IR YAML`() {

    val source =
      createTempFile("sunday-ir-cli-source", ".yaml").apply {
        writeText(
          """
          asyncapi: 2.6.0
          info:
            title: Craft Events API
            version: 1.0.0
          x-sunday-apiId: craft
          channels:
            project.changed:
              x-sunday-service: projects
              subscribe:
                operationId: projectChanged
                message:
                  name: ProjectChanged
                  contentType: application/json
                  payload:
                    type: object
                    required:
                      - projectId
                    properties:
                      projectId:
                        type: string
          """.trimIndent(),
        )
      }
    val output = createTempFile("sunday-ir-cli", ".yaml")

    val result = IrCommand().test(arrayOf("-out", output.toString(), "--source", "asyncapi", source.toString()))

    assertEquals(0, result.statusCode)
    val api = GeneratedApiYaml.readPath(output)
    assertThat(api.source.kind, equalTo(GeneratedSourceSpec.Kind.ASYNCAPI))
    assertThat(
      api
        .services
        .single()
        .operations
        .single()
        .id,
      equalTo("projectChanged"),
    )
  }

  @Test
  fun `composes OpenAPI and AsyncAPI sources to IR YAML`() {

    val openApi =
      createTempFile("sunday-ir-cli-source", ".yaml").apply {
        writeText(
          """
          openapi: 3.1.0
          info:
            title: Craft HTTP API
            version: 1.0.0
          x-sunday-apiId: craft
          paths:
            /projects/{projectId}:
              x-sunday-service: projects
              get:
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
          components:
            schemas:
              Project:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: string
          """.trimIndent(),
        )
      }
    val asyncApi =
      createTempFile("sunday-ir-cli-source", ".yaml").apply {
        writeText(
          """
          asyncapi: 2.6.0
          info:
            title: Craft Events API
            version: 1.0.0
          x-sunday-apiId: craft
          channels:
            project.changed:
              x-sunday-service: projects
              subscribe:
                operationId: projectChanged
                message:
                  name: ProjectChanged
                  contentType: application/json
                  payload:
                    type: object
                    required:
                      - projectId
                    properties:
                      projectId:
                        type: string
          """.trimIndent(),
        )
      }
    val output = createTempFile("sunday-ir-cli", ".yaml")

    val result = IrCommand().test(arrayOf("-out", output.toString(), openApi.toString(), asyncApi.toString()))

    assertEquals(0, result.statusCode)
    val api = GeneratedApiYaml.readPath(output)
    assertThat(api.name, equalTo("Craft HTTP API"))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { operation -> operation.id },
      equalTo(listOf("getProject", "projectChanged")),
    )
  }

  @Test
  fun `exports source tags as services when requested`() {

    val source =
      createTempFile("sunday-ir-cli-source", ".yaml").apply {
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
                    description: Project response.
          """.trimIndent(),
        )
      }
    val output = createTempFile("sunday-ir-cli", ".yaml")

    val result = IrCommand().test(arrayOf("-services-from-tags", "-out", output.toString(), source.toString()))

    assertEquals(0, result.statusCode)
    val api = GeneratedApiYaml.readPath(output)
    assertThat(api.services.single().name, equalTo("ProjectsService"))
  }

  @Test
  fun `validates IR YAML`() {

    val ir =
      createTempFile("sunday-ir-cli-validate", ".yaml").apply {
        writeText(
          """
          irVersion: "1"
          name: "Projects API"
          source:
            kind: "RAML"
            location: "projects.raml"
          """.trimIndent(),
        )
      }

    val result = IrCommand().test(arrayOf("--validate", ir.toString()))

    assertEquals(0, result.statusCode)
    assertThat(result.stdout, containsString("Valid Sunday IR"))
    assertThat(result.stdout, containsString("Projects API"))
  }
}
