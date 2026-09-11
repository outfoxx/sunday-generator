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
import java.util.concurrent.Executors
import kotlin.io.path.writeText

class OpenApiDiscriminatorValuesTest {
  @Test
  fun `generated name collisions preserve original discriminator values`(
    @TempDir directory: Path,
  ) {
    for ((rootName, importedName, stem) in listOf(
      Triple("cat", "Cat", "Cat"),
      Triple("cat.name", "cat-name", "CatName"),
      Triple("cat_name", "cat.name", "CatName"),
    )) {
      for (reverse in listOf(false, true)) {
        val source = hierarchy(directory, catName = importedName, rootName = rootName, reverse = reverse)
        source.writeText(Files.readString(source) + "    ${stem}2: {type: string}\n")
        val resolver = OpenApiReferenceResolver()
        val result = resolver.resolve(source.toUri())
        assertEquals(mapOf(importedName to "${stem}3", "Dog" to "Dog"), mappings(result, "Pet"))
        val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
        assertEquals(
          "unrelated",
          models
            .getValue(rootName)
            .properties
            .single()
            .name,
        )
        assertEquals(importedName, models.getValue("${stem}3").discriminatorValue)
        assertEquals(
          GeneratedTypeRef.named("${stem}3"),
          models
            .getValue("${stem}3")
            .properties
            .single { it.name == "child" }
            .type,
        )
        assertEquals(result.document, resolver.resolve(source.toUri()).document)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `allOf imports preserve wire values and unchanged alternatives`(
    version: String,
    @TempDir directory: Path,
  ) {
    val source = hierarchy(directory, version = version)
    val resolver = OpenApiReferenceResolver()
    val resolution = resolver.resolve(source.toUri())
    assertEquals(setOf(source.toUri(), directory.resolve("shared.yaml").toUri()), resolution.documents.keys)
    assertEquals(mapOf("Cat" to "Cat2", "Dog" to "Dog"), mappings(resolution, "Pet"))
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
    assertEquals("Cat", models.getValue("Cat2").discriminatorValue)
    assertEquals("Dog", models.getValue("Dog").discriminatorValue)
    assertEquals(listOf(GeneratedTypeRef.named("Pet")), models.getValue("Cat2").inherits)
    assertFalse(models.containsKey("Unreachable"))
    assertEquals(resolution.document, resolver.resolve(source.toUri()).document)
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `union alternatives retain values through pointer anchor and id aliases`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val source = hierarchy(directory)
    for (reference in listOf("#/components/schemas/Cat", "#cat", "https://schemas.example.test/cat#cat")) {
      val identifier = if (reference.startsWith("https:")) "${'$'}id: https://schemas.example.test/cat" else ""
      document(
        directory.resolve("shared.yaml"),
        """
        Pet:
          $keyword:
            - ${'$'}ref: '$reference'
            - ${'$'}ref: '#/components/schemas/Dog'
          discriminator: {propertyName: kind}
        Cat:
          $identifier
          ${'$'}anchor: cat
          type: object
          required: [kind]
          properties:
            kind: {type: string}
            child: {${'$'}ref: '#cat'}
        Dog: {type: object, required: [kind], properties: {kind: {type: string}}}
        """.trimIndent(),
      )
      val requests = mutableListOf<String>()
      val result =
        OpenApiReferenceResolver(
          OpenApiDocumentLoader { uri ->
            requests.add(uri.toString())
            assertEquals("file", uri.scheme)
            OpenApiLoadedDocument(uri, Files.readAllBytes(Path.of(uri)))
          },
        ).resolve(source.toUri())
      assertEquals(mapOf("Cat" to "Cat2", "Dog" to "Dog"), mappings(result, "Pet"))
      assertEquals(2, requests.size)
      val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
      assertEquals(
        GeneratedTypeRef.named("Cat2"),
        models
          .getValue("Cat2")
          .properties
          .single { it.name == "child" }
          .type,
      )
    }
  }

  @Test
  fun `explicit values remain authoritative when inferred mappings are completed`(
    @TempDir directory: Path,
  ) {
    for ((mapping, expected) in listOf(
      "canine: Dog" to mapOf("Cat" to "Cat2", "canine" to "Dog"),
      "Cat2: Cat, canine: Dog" to mapOf("Cat2" to "Cat2", "canine" to "Dog"),
      "feline: Cat" to mapOf("feline" to "Cat2"),
    )) {
      val source = hierarchy(directory, mapping = "mapping: {$mapping}")
      assertEquals(expected, mappings(OpenApiReferenceResolver().resolve(source.toUri()), "Pet"))
    }
  }

  @Test
  fun `original names survive name normalization root aliases and declaration order`(
    @TempDir directory: Path,
  ) {
    for ((name, generated) in listOf("cat.v2" to "CatV2", "a-cat" to "ACat", "Cat" to "Cat2")) {
      for (reverse in listOf(false, true)) {
        val source = hierarchy(directory, catName = name, reverse = reverse)
        assertEquals(
          mapOf(name to generated, "Dog" to "Dog"),
          mappings(OpenApiReferenceResolver().resolve(source.toUri()), "Pet"),
        )
      }
    }
    val source = hierarchy(directory)
    source.writeText(
      Files.readString(source) +
        "    Cat2: {${'$'}ref: 'shared.yaml#/components/schemas/Cat'}\n" +
        "    Alias: {${'$'}ref: '#/components/schemas/Cat2'}\n",
    )
    assertEquals(
      mapOf("Cat" to "Cat2", "Dog" to "Dog"),
      mappings(OpenApiReferenceResolver().resolve(source.toUri()), "Pet"),
    )
  }

  @Test
  fun `unrenamed hierarchies retain their implicit mappings`(
    @TempDir directory: Path,
  ) {
    hierarchy(directory, unreachable = false)
    val result = OpenApiReferenceResolver().resolve(directory.resolve("shared.yaml").toUri())
    assertEquals(emptyMap<String, String>(), mappings(result, "Pet"))
  }

  @Test
  fun `promoting an anonymous schema does not invent a discriminator value`(
    @TempDir directory: Path,
  ) {
    val source = hierarchy(directory)
    document(
      directory.resolve("shared.yaml"),
      """
      Pet:
        oneOf: [{${'$'}ref: '#/components/schemas/Cat'}, {${'$'}ref: '#anonymous'}]
        discriminator: {propertyName: kind}
      Cat: {type: object, properties: {kind: {type: string}}}
      Dog: {type: object}
      Container:
        properties:
          anonymous:
            ${'$'}anchor: anonymous
            type: object
            properties: {kind: {type: string}}
      """.trimIndent(),
    )
    val result = OpenApiReferenceResolver().resolve(source.toUri())
    assertEquals(mapOf("Cat" to "Cat2"), mappings(result, "Pet"))
    val schemas = (result.document["components"] as Map<*, *>)["schemas"] as Map<*, *>
    assertTrue(schemas.containsKey("Anonymous"))
  }

  @Test
  fun `mapping completion remains local to a hierarchy and conversion`(
    @TempDir directory: Path,
  ) {
    val source = hierarchy(directory, rootName = "cat")
    source.writeText(
      Files.readString(source) +
        """
        Other:
          oneOf:
            - ${'$'}ref: shared.yaml#/components/schemas/Cat
            - ${'$'}ref: shared.yaml#/components/schemas/Dog
          discriminator: {propertyName: kind, mapping: {kitten: 'shared.yaml#/components/schemas/Cat', puppy: 'shared.yaml#/components/schemas/Dog'}}
        """.trimIndent().prependIndent("    ") + "\n",
    )
    val converter = OpenApiToGeneratedApi()
    Executors.newFixedThreadPool(4).use { executor ->
      val results = (1..8).map { executor.submit<GeneratedApi> { converter.convert(source.toUri()) } }.map { it.get() }
      assertTrue(results.all { it == results.first() })
      val models = results.first().models.associateBy { it.name }
      assertEquals(
        mapOf("Cat" to GeneratedTypeRef.named("Cat2"), "Dog" to GeneratedTypeRef.named("Dog")),
        models.getValue("Pet").discriminatorMappings,
      )
      assertEquals(
        mapOf("kitten" to GeneratedTypeRef.named("Cat2"), "puppy" to GeneratedTypeRef.named("Dog")),
        models.getValue("Other").discriminatorMappings,
      )
    }
  }

  @Test
  fun `ambiguous implicit names require explicit mappings at the discriminator`(
    @TempDir directory: Path,
  ) {
    listOf("first", "second").forEach { name ->
      document(directory.resolve("$name.yaml"), "Cat: {type: object, properties: {kind: {type: string}}}")
    }
    val source = directory.resolve("api.yaml")
    val schemas =
      """
      Pet:
        oneOf:
          - ${'$'}ref: first.yaml#/components/schemas/Cat
          - ${'$'}ref: second.yaml#/components/schemas/Cat
        discriminator:
          propertyName: kind
      """.trimIndent()
    document(source, schemas)
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertEquals(10, error.line)
    assertTrue(error.message.orEmpty().contains("Conflicting implicit OpenAPI discriminator value 'Cat'"))
    document(
      source,
      schemas +
        "\n    mapping: {first: 'first.yaml#/components/schemas/Cat', second: 'second.yaml#/components/schemas/Cat'}",
    )
    assertEquals(
      mapOf("first" to "Cat", "second" to "Cat2"),
      mappings(OpenApiReferenceResolver().resolve(source.toUri()), "Pet"),
    )
  }

  private fun hierarchy(
    directory: Path,
    version: String = "3.1.0",
    catName: String = "Cat",
    mapping: String = "",
    reverse: Boolean = false,
    unreachable: Boolean = true,
    rootName: String = "Cat",
  ): Path {
    val cat =
      """
      $catName:
        allOf:
          - ${'$'}ref: '#/components/schemas/Pet'
          - type: object
            properties:
              lives: {type: integer}
              child: {${'$'}ref: '#/components/schemas/$catName'}
      """.trimIndent()
    val dog =
      """
      Dog:
        allOf:
          - ${'$'}ref: '#/components/schemas/Pet'
          - type: object
            properties: {barks: {type: boolean}}
      """.trimIndent()
    val definitions = if (reverse) listOf(dog, cat) else listOf(cat, dog)
    document(
      directory.resolve("shared.yaml"),
      """
      Pet:
        type: object
        required: [kind]
        properties: {kind: {type: string}}
        discriminator:
          propertyName: kind
          $mapping
      """.trimIndent() + "\n" + definitions.joinToString("\n") +
        if (unreachable) {
          "\nUnreachable:\n  allOf: [{${'$'}ref: '#/components/schemas/Pet'}]\n" +
            "  properties: {missing: {${'$'}ref: 'https://never.example.test/missing'}}"
        } else {
          ""
        },
      version,
    )
    return directory.resolve("api.yaml").also {
      document(
        it,
        """
        $rootName: {type: object, properties: {unrelated: {type: boolean}}}
        Container:
          type: object
          properties:
            animal: {${'$'}ref: 'shared.yaml#/components/schemas/Pet'}
            cat: {${'$'}ref: 'shared.yaml#/components/schemas/$catName'}
            dog: {${'$'}ref: 'shared.yaml#/components/schemas/Dog'}
        """.trimIndent(),
        version,
      )
    }
  }

  private fun document(
    path: Path,
    schemas: String,
    version: String = "3.1.0",
  ) {
    path.writeText(
      "openapi: $version\ninfo: {title: Discriminators, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
        schemas.prependIndent("    ") + "\n",
    )
  }

  private fun mappings(
    result: OpenApiReferenceResolution,
    name: String,
  ): Map<String, String> {
    val schemas = (result.document["components"] as Map<*, *>)["schemas"] as Map<*, *>
    val discriminator = (schemas[name] as Map<*, *>)["discriminator"] as Map<*, *>
    return (discriminator["mapping"] as? Map<*, *>).orEmpty().entries.associate {
      it.key.toString() to it.value.toString().substringAfterLast('/')
    }
  }
}
