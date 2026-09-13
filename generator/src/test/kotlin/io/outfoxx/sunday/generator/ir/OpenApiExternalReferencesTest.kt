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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

@ExtendWith(ResourceExtension::class)
class OpenApiExternalReferencesTest {

  @Test
  fun `exports external references with document context and shared recursive model identity`(
    @ResourceUri("openapi/ir/external-refs/api.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = false)).export(source)
    val operations =
      api.services
        .single()
        .operations
        .associateBy { it.id }
    val parameter = operations.getValue("searchUsers").parameters.single()
    assertEquals("q", parameter.name)
    assertTrue(parameter.required)
    assertEquals(GeneratedTypeRef.scalar("string"), parameter.type)
    assertEquals("2", parameter.validation["minLength"])
    assertEquals(GeneratedTypeRef.named("User"), operations.getValue("createUser").requestBody?.type)
    assertEquals(
      GeneratedTypeRef.named("User"),
      operations
        .getValue("createUser")
        .responses
        .single()
        .type,
    )
    val download = operations.getValue("download").responses.single()
    assertEquals(GeneratedTypeRef.scalar("file", format = "binary"), download.type)
    assertEquals(GeneratedTypeRef.scalar("integer"), download.headers.single().type)

    val models = api.models.associateBy { it.name }
    assertEquals(setOf("User", "Node", "Node2", "Profile", "NameWithEscapeSpace"), models.keys)
    val properties = models.getValue("User").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("User"), properties.getValue("parent").type)
    assertEquals(GeneratedTypeRef.named("Profile"), properties.getValue("profile").type)
    assertEquals(properties.getValue("profile").type, properties.getValue("repeatedProfile").type)
    assertEquals(GeneratedTypeRef.named("Node2"), properties.getValue("externalNode").type)
    assertEquals(
      GeneratedTypeRef.named("User"),
      models
        .getValue("Profile")
        .properties
        .first()
        .type,
    )
    assertEquals(
      "local",
      models
        .getValue("Node")
        .properties
        .single()
        .name,
    )
    assertEquals(
      "external",
      models
        .getValue("Node2")
        .properties
        .single()
        .name,
    )
    assertEquals("2", properties.getValue("escaped").validation["minLength"])
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "./missing.yaml#/Parameter",
      "./shared.yaml#/Missing",
      "./shared.yaml#/Parameter~2",
      "./shared.yaml#anchor",
      "./shared.yaml#/parameters/01",
      "./shared.yaml#/Wrong",
      "./shared.yaml#/Schema",
      "./shared.yaml#/components/responses/Wrong",
      "./shared%XX.yaml#/Parameter",
      "ftp://example.invalid/shared.yaml#/Parameter",
    ],
  )
  fun `rejects invalid parameter references at the source`(
    reference: String,
    @TempDir directory: Path,
  ) {
    val root = writeParameterApi(directory, reference)
    directory.resolve("shared.yaml").writeText(
      """
      Parameter: {name: q, in: query, schema: {type: string}}
      parameters: [{name: q, in: query, schema: {type: string}}]
      Wrong: 42
      Schema: {type: object, properties: {id: {type: string}}}
      components:
        responses:
          Wrong: {description: response}
      """.trimIndent(),
    )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(root.toUri()) }
    assertEquals(root.toUri().toString(), error.file)
    assertEquals(9, error.line)
    assertTrue(error.column > 0)
    assertTrue(error.message.orEmpty().contains(reference), error.toString())
  }

  @Test
  fun `reports invalid external documents at the referring object`(
    @TempDir directory: Path,
  ) {
    val root = writeParameterApi(directory, "./shared.yaml#/Parameter")
    directory.resolve("shared.yaml").writeText("Parameter: [unterminated")
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(root.toUri()) }
    assertEquals(root.toUri().toString(), error.file)
    assertEquals(9, error.line)
    assertTrue(error.message.orEmpty().contains("Invalid OpenAPI document"), error.toString())
  }

  @ParameterizedTest
  @ValueSource(strings = ["schemas", "parameters", "requestBodies", "responses", "headers", "securitySchemes"])
  fun `rejects incompatible external component objects`(
    section: String,
    @TempDir directory: Path,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: Invalid component, version: 1.0.0}
      paths: {}
      components:
        $section:
          Invalid:
            ${'$'}ref: './shared.yaml'
      """.trimIndent(),
    )
    directory.resolve("shared.yaml").writeText(
      if (section == "schemas") "type: definitely-not-a-schema-type" else "description: Schema\ntype: object",
    )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(root.toUri()) }
    assertEquals(root.toUri().toString(), error.file)
    assertEquals(7, error.line)
    assertTrue(error.message.orEmpty().contains("expected a"), error.toString())
  }

  @Test
  fun `rejects schema references into an actual response component`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Wrong target kind, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Invalid: {${'$'}ref: '#/components/responses/Response'}
        responses:
          Response: {description: Response, content: {}}
      """.trimIndent(),
    )
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
    assertEquals(6, error.line)
    assertTrue(error.message.orEmpty().contains("expected a schema"))
  }

  @Test
  fun `resolves external discriminator mapping targets in their own document`(
    @TempDir directory: Path,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: External discriminator, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Base:
            ${'$'}ref: './shared.yaml#/components/schemas/Base'
          Child:
            type: object
            properties: {local: {type: string}}
      """.trimIndent(),
    )
    directory.resolve("shared.yaml").writeText(
      """
      components:
        schemas:
          Base:
            type: object
            properties: {kind: {type: string}}
            discriminator:
              propertyName: kind
              mapping:
                child: Child
          Child:
            ${'$'}id: nested/child.yaml
            allOf:
              - ${'$'}ref: '../shared.yaml#/components/schemas/Base'
              - type: object
                properties: {external: {type: boolean}}
      """.trimIndent(),
    )
    val models = OpenApiToGeneratedApi().convert(root.toUri()).models.associateBy { it.name }
    assertEquals(setOf("Base", "Child", "Child2"), models.keys)
    assertEquals(mapOf("child" to GeneratedTypeRef.named("Child2")), models.getValue("Base").discriminatorMappings)
    assertEquals(listOf(GeneratedTypeRef.named("Base")), models.getValue("Child2").inherits)
    assertEquals("child", models.getValue("Child2").discriminatorValue)
  }

  @Test
  fun `preserves recursive schemas nested in external definitions`(
    @TempDir directory: Path,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: Recursive definitions, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Tree:
            ${'$'}ref: './shared.yaml'
      """.trimIndent(),
    )
    directory.resolve("shared.yaml").writeText(
      """
      type: object
      ${'$'}defs:
        Node:
          type: object
          properties:
            child:
              ${'$'}ref: '#/${'$'}defs/Node'
      properties:
        parent:
          ${'$'}ref: ''
        root:
          ${'$'}ref: '#/${'$'}defs/Node'
      """.trimIndent(),
    )
    val models = OpenApiToGeneratedApi().convert(root.toUri()).models.associateBy { it.name }
    assertEquals(setOf("Tree", "Node"), models.keys)
    val treeProperties = models.getValue("Tree").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("Tree"), treeProperties.getValue("parent").type)
    assertEquals(GeneratedTypeRef.named("Node"), treeProperties.getValue("root").type)
    assertEquals(
      GeneratedTypeRef.named("Node"),
      models
        .getValue("Node")
        .properties
        .single()
        .type,
    )
  }

  @Test
  fun `reports reference cycles in external parameters at the nested source`(
    @TempDir directory: Path,
  ) {
    val root = writeParameterApi(directory, "./shared.yaml#/Parameter")
    val shared = directory.resolve("shared.yaml")
    shared.writeText("Parameter:\n  ${'$'}ref: './other.yaml#/Parameter'\n")
    directory.resolve("other.yaml").writeText("Parameter:\n  ${'$'}ref: './shared.yaml#/Parameter'\n")
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(root.toUri()) }
    assertEquals(shared.toUri().toString(), error.file)
    assertEquals(2, error.line)
    assertTrue(error.message.orEmpty().contains("Cyclic OpenAPI parameter"), error.toString())
  }

  @Test
  fun `rejects schema alias cycles without rejecting recursive properties`(
    @TempDir directory: Path,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: Cyclic aliases, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Alias:
            ${'$'}ref: './shared.yaml#/Alias'
      """.trimIndent(),
    )
    directory.resolve("shared.yaml").writeText("Alias:\n  ${'$'}ref: './api.yaml#/components/schemas/Alias'\n")
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(root.toUri()) }
    assertTrue(error.message.orEmpty().contains("Cyclic OpenAPI schema"), error.toString())
    assertTrue(error.line > 0)
  }

  @Test
  fun `leaves reference shaped example data untouched`(
    @TempDir directory: Path,
  ) {
    val root = writeParameterApi(directory, "./shared.yaml#/Parameter")
    directory.resolve("shared.yaml").writeText(
      """
      Parameter:
        name: q
        in: query
        schema: {type: string}
        example: {${'$'}ref: './data-not-a-reference.yaml'}
      """.trimIndent(),
    )
    val parameter =
      OpenApiToGeneratedApi()
        .convert(
          root.toUri(),
        ).services
        .single()
        .operations
        .single()
        .parameters
        .single()
    assertEquals(mapOf("${'$'}ref" to "./data-not-a-reference.yaml"), parameter.examples.single().value)
  }

  @Test
  fun `ignores unsupported reference siblings before resolving their contents`(
    @TempDir directory: Path,
  ) {
    val root = writeParameterApi(directory, "./shared.yaml#/Parameter")
    directory.resolve("shared.yaml").writeText(
      """
      Parameter:
        ${'$'}ref: '#/Query'
        schema: {${'$'}ref: './ignored.yaml'}
      Query:
        name: q
        in: query
        schema: {type: string}
      """.trimIndent(),
    )
    val parameter =
      OpenApiToGeneratedApi()
        .convert(
          root.toUri(),
        ).services
        .single()
        .operations
        .single()
        .parameters
        .single()
    assertEquals(GeneratedTypeRef.scalar("string"), parameter.type)
    assertEquals("Referenced query", parameter.documentation?.description)
  }

  private fun writeParameterApi(
    directory: Path,
    reference: String,
  ): Path =
    directory.resolve("api.yaml").also { path ->
      path.writeText(
        """
        openapi: 3.1.0
        info: {title: External parameter, version: 1.0.0}
        paths:
          /search:
            get:
              operationId: search
              parameters:
                - description: Referenced query
                  ${'$'}ref: '$reference'
              responses:
                '204': {description: Done}
        """.trimIndent(),
      )
    }
}
