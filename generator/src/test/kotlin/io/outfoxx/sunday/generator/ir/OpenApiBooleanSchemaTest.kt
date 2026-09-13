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
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiBooleanSchemaTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `true components and nested targets retain empty schema types and identity`(
    reverse: Boolean,
    @TempDir directory: Path,
  ) {
    val declarations =
      listOf(
        "Anything: true",
        "Empty: {}",
        "Alias: {${'$'}ref: '#/components/schemas/Anything'}",
        "AliasChain: {${'$'}ref: '#/components/schemas/Alias'}",
        """
        Container:
          type: object
          required: [original, value]
          properties:
            value: {${'$'}ref: '#/components/schemas/AliasChain'}
            original: true
            copy: {${'$'}ref: '#/components/schemas/Container/properties/original'}
            inline: true
            empty: {}
            array: {type: array, items: true}
        """.trimIndent(),
      )
    val source = api(directory, (if (reverse) declarations.reversed() else declarations).joinToString("\n"))
    val converter = OpenApiToGeneratedApi()
    val result = converter.convert(source.toUri())
    val models = result.models.associateBy { it.name }
    for (name in listOf("Anything", "Alias", "AliasChain", "Original")) {
      assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, models.getValue(name).kind)
      assertEquals(models.getValue("Empty").aliases, models.getValue(name).aliases)
    }
    val properties = models.getValue("Container").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("Original"), properties.getValue("original").type)
    assertEquals(properties.getValue("original").type, properties.getValue("copy").type)
    assertTrue(properties.getValue("original").required)
    assertFalse(properties.getValue("copy").required)
    assertEquals(properties.getValue("empty").type, properties.getValue("inline").type)
    assertEquals(listOf(GeneratedTypeRef.scalar("any")), properties.getValue("array").type.arguments)
    assertEquals(result, converter.convert(source.toUri()))
  }

  @Test
  fun `external true schemas and resource relative pointers resolve`(
    @TempDir directory: Path,
  ) {
    directory.resolve("anything.yaml").writeText("true")
    directory.resolve("resource.yaml").writeText(
      """
      ${'$'}id: https://schemas.example.test/resource
      ${'$'}defs: {Anything: true}
      type: object
      properties:
        value: {${'$'}ref: '#/${'$'}defs/Anything'}
      """.trimIndent(),
    )
    val source =
      api(
        directory,
        """
        External: {${'$'}ref: anything.yaml}
        Resource: {${'$'}ref: resource.yaml}
        Container:
          type: object
          properties:
            value: {${'$'}ref: 'https://schemas.example.test/resource#/${'$'}defs/Anything'}
        """.trimIndent(),
      )
    val requests = mutableListOf<String>()
    val loader =
      OpenApiDocumentLoader { uri ->
        assertEquals("file", uri.scheme)
        requests.add(uri.toString())
        OpenApiLoadedDocument(uri, Files.readAllBytes(Path.of(uri)))
      }
    val models = OpenApiToGeneratedApi().convert(source.toUri(), loader).models.associateBy { it.name }
    assertEquals(listOf(GeneratedTypeRef.scalar("any")), models.getValue("External").aliases)
    assertEquals(listOf(GeneratedTypeRef.scalar("any")), models.getValue("Anything").aliases)
    assertEquals(
      GeneratedTypeRef.named("Anything"),
      models
        .getValue("Resource")
        .properties
        .single()
        .type,
    )
    assertEquals(
      GeneratedTypeRef.named("Anything"),
      models
        .getValue("Container")
        .properties
        .single()
        .type,
    )
    assertEquals(3, requests.size)
  }

  @Test
  fun `true remote documents work offline and through captured inputs`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/anything", "true")
      val source = api(directory, "Anything: {${'$'}ref: '${server.baseUri}anything'}")
      val options = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true)
      val loader = OpenApiDocumentLoader.create(options)
      val resolution = OpenApiReferenceResolver(loader).resolve(source.toUri())
      val converter = OpenApiToGeneratedApi()
      val online = converter.convert(source.toUri(), loader)
      assertEquals(listOf(GeneratedTypeRef.scalar("any")), online.models.single().aliases)
      val snapshot = directory.resolve("snapshot")
      OpenApiDocumentSnapshot.write(snapshot, directory, resolution.documents)
      assertEquals(online, converter.convert(source.toUri(), OpenApiDocumentSnapshot.loader(snapshot, directory)))
      assertEquals(
        online,
        converter.convert(source.toUri(), OpenApiDocumentLoader.create(options.copy(offline = true))),
      )
      assertEquals(listOf("/anything"), server.requests.toList())
    }
  }

  @Test
  fun `referenced parameters may contain true schemas without changing requiredness`(
    @TempDir directory: Path,
  ) {
    val source = api(directory, "Anything: true")
    source.writeText(
      Files.readString(source).replace(
        "paths: {}",
        """
        paths:
          /items:
            get:
              parameters:
                - ${'$'}ref: '#/components/parameters/Value'
              responses: {'204': {description: Empty}}
        """.trimIndent(),
      ) + "\n  parameters:\n    Value: {name: value, in: query, required: true, schema: true}\n",
    )
    val parameter =
      OpenApiToGeneratedApi()
        .convert(
          source.toUri(),
        ).services
        .single()
        .operations
        .single()
        .parameters
        .single()
    assertEquals(GeneratedTypeRef.scalar("any"), parameter.type)
    assertTrue(parameter.required)
  }

  @Test
  fun `boolean additional properties retain their established forms`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        "Open: {type: object, additionalProperties: true}\nClosed: {type: object, additionalProperties: false}",
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
    assertEquals(true, result.getValue("Open").additionalProperties?.allowed)
    assertEquals(false, result.getValue("Closed").additionalProperties?.allowed)
    val document = OpenApiReferenceResolver().resolve(source.toUri()).document
    val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
    assertEquals(true, (schemas["Open"] as Map<*, *>)["additionalProperties"])
    assertEquals(false, (schemas["Closed"] as Map<*, *>)["additionalProperties"])
  }

  @Test
  fun `boolean references retain dialect and target checks with source locations`(
    @TempDir directory: Path,
  ) {
    val cases =
      listOf(
        Triple(
          "3.0.3",
          "Anything: true\nAlias: {${'$'}ref: '#/components/schemas/Anything'}",
          "expected a schema object",
        ),
        Triple(
          "3.1.0",
          "Anything: false\nAlias: {${'$'}ref: '#/components/schemas/Anything'}",
          "false schemas cannot be represented",
        ),
        Triple("3.1.0", "Alias: {${'$'}ref: '#/components/responses/Anything'}", "expected a schema object"),
      )
    for ((version, schemas, expected) in cases) {
      val source = api(directory, schemas, version)
      if (schemas.contains(
          "/responses/",
        )
      ) {
        source.writeText(Files.readString(source) + "  responses: {Anything: true}\n")
      }
      val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
      assertEquals(source.toUri().toString(), error.file)
      assertTrue(error.line >= 6, error.toString())
      assertTrue(error.message.orEmpty().contains(expected), error.toString())
    }
  }

  private fun api(
    directory: Path,
    schemas: String,
    version: String = "3.1.0",
  ): Path =
    directory.resolve("api.yaml").apply {
      writeText(
        "openapi: $version\ninfo: {title: Boolean schemas, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    ") + "\n",
      )
    }
}
