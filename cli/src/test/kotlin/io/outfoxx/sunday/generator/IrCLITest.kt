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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class IrCLITest {
  @Test
  fun `exports reference siblings and canonical inline anchors through the CLI`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: References, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Base:
            type: object
            required: [id]
            properties: {id: {type: string}}
          Child:
            ${'$'}ref: '#/components/schemas/Base'
            properties: {name: {type: string}}
          Container:
            type: object
            properties:
              node:
                ${'$'}anchor: node
                type: object
                properties:
                  child: {${'$'}ref: '#node'}
              copy: {${'$'}ref: '#node'}
      """.trimIndent(),
    )
    val output = directory.resolve("api.ir.yaml")
    val command = IrCommand().test(arrayOf("-out", output.toString(), source.toString()))
    assertEquals(0, command.statusCode, command.output)
    val api = GeneratedApiYaml.readPath(output)
    assertEquals(
      true,
      api.models
        .single { it.name == "Base" }
        .properties
        .single()
        .required,
    )
    val child = api.models.single { it.name == "Child" }
    assertEquals("Base", child.inherits.single().name)
    assertEquals("name", child.properties.single().name)
    val container = api.models.single { it.name == "Container" }
    assertEquals(
      1,
      container.properties
        .map { it.type }
        .distinct()
        .size,
    )
    val canonical = container.properties.first().type
    assertEquals(
      canonical,
      api.models
        .single { it.name == canonical.name }
        .properties
        .single()
        .type,
    )
  }

  @Test
  fun `exports remote schema resources and reuses the CLI cache offline`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/redirect", "", 302, mapOf("Location" to "/user.yaml"))
      server.respond(
        "/user.yaml",
        OpenApiReferenceDocuments.document(
          "Shared user",
          """
          User:
            ${'$'}id: ./user-resource.yaml
            ${'$'}anchor: user
            type: [object, 'null']
            required: [parent, detail]
            properties:
              id: {type: string}
              parent: {${'$'}ref: '#user'}
              detail:
                ${'$'}anchor: detail
                description: Original detail
                anyOf:
                  - {type: object, properties: {value: {type: string}}}
                  - {type: 'null'}
              copiedDetail: {${'$'}ref: '#detail'}
              strictDetail:
                type: object
                anyOf: [{type: object, properties: {value: {type: string}}}, {type: 'null'}]
              text:
                ${'$'}anchor: text
                oneOf: [{type: [string, 'null']}, {type: 'null'}]
              copiedText: {${'$'}ref: '#text'}
              state:
                ${'$'}anchor: state
                type: [string, 'null']
                enum: [active, inactive]
              copiedState: {${'$'}ref: '#state'}
          AnnotatedDetail:
            ${'$'}ref: '#/components/schemas/User/properties/detail'
            description: Alias detail
            readOnly: true
            deprecated: true
          """.trimIndent(),
          OpenApiReferenceDocuments.nullableValues,
          OpenApiReferenceDocuments.booleanSchemas,
          OpenApiReferenceDocuments.pet,
          OpenApiReferenceDocuments.mappedPet(),
          OpenApiReferenceDocuments.cat,
          OpenApiReferenceDocuments.records,
          OpenApiReferenceDocuments.dog,
        ),
      )
      server.respond("/mapped-cat?schema=cat", OpenApiReferenceDocuments.mappedCat())
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: 3.0.3
        info: {title: Remote, version: 1.0.0}
        paths:
          /users:
            get:
              operationId: getUser
              parameters:
                - name: limit
                  in: query
                  schema: {type: integer, default: 20, minimum: 1, maximum: 100}
              responses: {'204': {description: Done}}
        components:
          schemas:
            cat: {type: object, properties: {unrelated: {type: boolean}}}
            Anything: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/Anything'}
            Empty: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/Empty'}
            Unbounded: {type: integer, exclusiveMinimum: false, exclusiveMaximum: false}
            DocumentedRecord: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/DocumentedRecord'}
            MappedPet: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/MappedPet'}
            Pets:
              type: object
              properties:
                animal: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/Pet'}
                cat: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/Cat'}
                dog: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/Dog'}
            User: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/User'}
            Limit: {${'$'}ref: '#/paths/~1users/get/parameters/0/schema'}
            AnnotatedDetail: {${'$'}ref: '${server.baseUri}redirect#/components/schemas/AnnotatedDetail'}
            Nullability:
              type: object
              required: [strictText, values]
              properties:
                strictText: {type: string, allOf: [{type: string, nullable: true}]}
                values: {${'$'}ref: '${server.baseUri}redirect#values'}
        """.trimIndent(),
      )
      val output = directory.resolve("api.ir.yaml")
      val arguments =
        arrayOf(
          "--openapi-reference-cache-dir",
          directory.resolve("cache").toString(),
          "-out",
          output.toString(),
          source.toString(),
        )
      val online = IrCommand().test(arguments)
      assertEquals(0, online.statusCode, online.output)
      val onlineApi = GeneratedApiYaml.readPath(output)
      assertEquals(
        "unrelated",
        onlineApi.models
          .single { it.name == "cat" }
          .properties
          .single()
          .name,
      )
      assertEquals(listOf(GeneratedTypeRef.scalar("any")), onlineApi.models.single { it.name == "Anything" }.aliases)
      assertEquals(
        onlineApi.models.single { it.name == "Empty" }.aliases,
        onlineApi.models.single { it.name == "Anything" }.aliases,
      )
      assertEquals(
        listOf(GeneratedTypeRef.scalar("integer")),
        onlineApi.models.single { it.name == "Unbounded" }.aliases,
      )
      assertEquals(
        mapOf("Cat" to GeneratedTypeRef.named("Cat2"), "Dog" to GeneratedTypeRef.named("Dog")),
        onlineApi.models.single { it.name == "Pet" }.discriminatorMappings,
      )
      assertEquals("Cat", onlineApi.models.single { it.name == "Cat2" }.discriminatorValue)
      assertEquals(
        mapOf("kitty" to GeneratedTypeRef.named("MappedCat")),
        onlineApi.models.single { it.name == "MappedPet" }.discriminatorMappings,
      )
      val mappedCat = onlineApi.models.single { it.name == "MappedCat" }
      assertEquals("kitty", mappedCat.discriminatorValue)
      assertEquals(listOf(GeneratedTypeRef.named("MappedPet")), mappedCat.inherits)
      assertEquals("lives", mappedCat.properties.single().name)
      assertTrue(mappedCat.properties.single().required)
      assertEquals(listOf(GeneratedTypeRef.named("Pet")), onlineApi.models.single { it.name == "Cat2" }.inherits)
      val record = onlineApi.models.single { it.name == "DocumentedRecord" }
      assertEquals(listOf(GeneratedTypeRef.named("BaseRecord")), record.inherits)
      assertEquals(listOf("detail"), record.properties.map { it.name })
      val identifier =
        onlineApi.models
          .single { it.name == "BaseRecord" }
          .properties
          .single { it.name == "id" }
      assertTrue(identifier.required)
      assertEquals("Parent identifier", identifier.documentation?.description)
      val payload =
        onlineApi.models
          .single { it.name == "BaseRecord" }
          .properties
          .single { it.name == "payload" }
      assertEquals(GeneratedTypeRef.scalar("string", nullable = true), payload.type)
      assertFalse(payload.required)
      val next =
        onlineApi.models
          .single { it.name == "BaseRecord" }
          .properties
          .single { it.name == "next" }
      assertEquals(GeneratedTypeRef.named("RecordNode", nullable = true), next.type)
      assertFalse(next.required)
      for (field in listOf("direct", "wrapped")) {
        for ((name, description) in listOf("BaseRecord" to "Parent", "RecordNode" to "Updated")) {
          val property =
            onlineApi.models
              .single { it.name == name }
              .properties
              .single { it.name == field }
          assertEquals(GeneratedTypeRef.named("RecordNode"), property.type)
          assertFalse(property.required)
          assertEquals("$description $field", property.documentation?.description)
        }
      }
      assertEquals(1, onlineApi.models.count { it.name.startsWith("RecordNode") })
      val nullability =
        onlineApi.models
          .single { it.name == "Nullability" }
          .properties
          .associateBy { it.name }
      assertEquals(GeneratedTypeRef.scalar("string"), nullability.getValue("strictText").type)
      assertEquals(GeneratedTypeRef.named("NullableValues", nullable = true), nullability.getValue("values").type)
      assertTrue(nullability.values.all { it.required })
      val user = onlineApi.models.single { it.name == "User" }
      assertEquals("User", user.name)
      val parent = user.properties.single { it.name == "parent" }
      assertEquals(GeneratedTypeRef.named("User", nullable = true), parent.type)
      assertTrue(parent.required)
      val detail = user.properties.single { it.name == "detail" }
      assertEquals(GeneratedTypeRef.named("Detail", nullable = true), detail.type)
      assertEquals(detail.type, user.properties.single { it.name == "copiedDetail" }.type)
      assertTrue(detail.required)
      assertEquals(false, detail.readOnly)
      assertEquals(false, detail.deprecated)
      assertEquals("Original detail", detail.documentation?.description)
      assertEquals(GeneratedTypeRef.named("Text"), user.properties.single { it.name == "text" }.type)
      assertEquals(GeneratedTypeRef.named("State"), user.properties.single { it.name == "state" }.type)
      assertEquals(listOf(GeneratedTypeRef.scalar("string")), onlineApi.models.single { it.name == "Text" }.aliases)
      assertFalse(
        user.properties
          .single { it.name == "strictDetail" }
          .type.nullable,
      )
      val limit =
        onlineApi.services
          .single()
          .operations
          .single()
          .parameters
          .single()
      assertEquals(GeneratedTypeRef.named("Limit"), limit.type)
      assertEquals(20, limit.defaultValue)
      assertEquals(mapOf("minimum" to "1", "maximum" to "100"), limit.validation)
      val offline = IrCommand().test(arrayOf("--openapi-offline", *arguments))
      assertEquals(0, offline.statusCode, offline.output)
      assertEquals(onlineApi, GeneratedApiYaml.readPath(output))
      source.writeText(Files.readString(source).replace("/redirect#", "/user.yaml#"))
      val effectiveOffline = IrCommand().test(arrayOf("--openapi-offline", *arguments))
      assertEquals(0, effectiveOffline.statusCode, effectiveOffline.output)
      assertEquals(onlineApi, GeneratedApiYaml.readPath(output))
      assertEquals(listOf("/redirect", "/user.yaml", "/mapped-cat?schema=cat"), server.requests.toList())
    }
  }

  @Test
  fun `exports external OpenAPI references from a root source`(
    @TempDir directory: Path,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: External query, version: 1.0.0}
      paths:
        /search:
          get:
            operationId: search
            parameters:
              - ${'$'}ref: './shared.yaml#/Query'
            responses:
              '204': {description: Done}
      """.trimIndent(),
    )
    directory.resolve("shared.yaml").writeText(
      "Query: {name: q, in: query, required: true, schema: {type: string, minLength: 2}}",
    )
    val output = directory.resolve("api.ir.yaml")
    val result = IrCommand().test(arrayOf("-out", output.toString(), root.toString()))
    assertEquals(0, result.statusCode, result.output)
    val parameter =
      GeneratedApiYaml
        .readPath(output)
        .services
        .single()
        .operations
        .single()
        .parameters
        .single()
    assertEquals("q", parameter.name)
    assertEquals(true, parameter.required)
    assertEquals("2", parameter.validation["minLength"])
  }

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
