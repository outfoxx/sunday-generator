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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class OpenApiModelAllocationTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `generated spellings reserve declared promoted and inline names`(
    reverse: Boolean,
    @TempDir directory: Path,
  ) {
    directory.resolve("container-foo.yaml").writeText("type: object\nproperties: {remote: {type: integer}}")
    val declarations =
      listOf(
        "container.foo: {type: string}",
        "container_foo2: {type: boolean}",
        """
        Container:
          type: object
          properties:
            foo: {type: object, properties: {local: {type: string}}}
            remote: {${'$'}ref: container-foo.yaml}
            alias: {${'$'}ref: '#/components/schemas/container.foo'}
        """.trimIndent(),
      )
    val source = api(directory, "3.1.0", (if (reverse) declarations.reversed() else declarations).joinToString("\n"))
    val converter = OpenApiToGeneratedApi()
    val result = converter.convert(source.toUri())
    val models = result.models.associateBy { it.name }
    val properties = models.getValue("Container").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("ContainerFoo3"), properties.getValue("remote").type)
    assertEquals(GeneratedTypeRef.named("ContainerFoo4"), properties.getValue("foo").type)
    assertEquals(GeneratedTypeRef.named("container.foo"), properties.getValue("alias").type)
    assertEquals(GeneratedTypeRef.scalar("string"), models.getValue("container.foo").aliases.single())
    assertEquals(GeneratedTypeRef.scalar("boolean"), models.getValue("container_foo2").aliases.single())
    assertEquals(
      "remote",
      models
        .getValue("ContainerFoo3")
        .properties
        .single()
        .name,
    )
    assertEquals(
      "local",
      models
        .getValue("ContainerFoo4")
        .properties
        .single()
        .name,
    )
    assertEquals(result, converter.convert(source.toUri()))
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inline objects and unions cannot overwrite declared or promoted models`(
    reverse: Boolean,
    @TempDir directory: Path,
  ) {
    directory.resolve("container-foo.yaml").writeText("type: object\nproperties: {remote: {type: integer}}")
    val declarations =
      listOf(
        "ContainerFoo2: {type: string}",
        "ContainerChoice: {type: boolean}",
        """
        Container:
          type: object
          properties:
            foo:
              type: object
              properties:
                local: {type: string}
                nested: {type: object, properties: {value: {type: boolean}}}
            bar: {${'$'}ref: 'container-foo.yaml'}
            choice:
              oneOf:
                - {${'$'}ref: '#/components/schemas/ContainerFoo2'}
                - {${'$'}ref: '#/components/schemas/ContainerChoice'}
            plain: {type: object, properties: {unchanged: {type: string}}}
        """.trimIndent(),
      )
    val source = api(directory, "3.1.0", (if (reverse) declarations.reversed() else declarations).joinToString("\n"))
    val converter = OpenApiToGeneratedApi()
    val result = converter.convert(source.toUri())
    val models = result.models.associateBy { it.name }
    val container = models.getValue("Container").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("ContainerFoo3"), container.getValue("foo").type)
    assertEquals(GeneratedTypeRef.named("ContainerFoo"), container.getValue("bar").type)
    assertEquals(GeneratedTypeRef.named("ContainerChoice2"), container.getValue("choice").type)
    assertEquals(GeneratedTypeRef.named("ContainerPlain"), container.getValue("plain").type)
    assertEquals(
      "remote",
      models
        .getValue("ContainerFoo")
        .properties
        .single()
        .name,
    )
    assertEquals(GeneratedTypeRef.scalar("string"), models.getValue("ContainerFoo2").aliases.single())
    assertEquals(GeneratedTypeRef.scalar("boolean"), models.getValue("ContainerChoice").aliases.single())
    assertEquals(GeneratedModel.Kind.UNION, models.getValue("ContainerChoice2").kind)
    val inline = models.getValue("ContainerFoo3").properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.scalar("string"), inline.getValue("local").type)
    assertEquals(GeneratedTypeRef.named("ContainerFoo3Nested"), inline.getValue("nested").type)
    assertEquals(
      "value",
      models
        .getValue("ContainerFoo3Nested")
        .properties
        .single()
        .name,
    )
    assertEquals(result, converter.convert(source.toUri()))
    Executors.newFixedThreadPool(4).use { executor ->
      val conversions = (1..4).map { CompletableFuture.supplyAsync({ converter.convert(source.toUri()) }, executor) }
      conversions.forEach { assertEquals(result, it.get(10, TimeUnit.SECONDS)) }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `nullable alone preserves unconstrained types through aliases and compositions`(
    version: String,
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        version,
        """
        Anything: {nullable: false, description: Arbitrary value}
        NullableAnything: {nullable: true}
        Alias: {${'$'}ref: '#/components/schemas/NullableAnything'}
        Composed: {allOf: [{nullable: true}], description: Composed arbitrary value}
        Typed: {type: string, nullable: true}
        TypedComposition:
          allOf: [{type: string}]
          nullable: true
        Container:
          type: object
          properties:
            absent: {description: No type}
            any: {nullable: false, x-note: annotation}
            nullableAny: {nullable: true}
            composed: {allOf: [{nullable: true}]}
            structured: {nullable: true, properties: {id: {type: string}}}
        """.trimIndent(),
      )
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
    for (name in listOf("Anything", "NullableAnything", "Alias", "Composed")) {
      val model = models.getValue(name)
      assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, model.kind)
      assertEquals(GeneratedTypeRef.scalar("any", nullable = name != "Anything"), model.aliases.single())
    }
    assertEquals("Arbitrary value", models.getValue("Anything").documentation?.description)
    assertEquals(GeneratedTypeRef.scalar("string", nullable = true), models.getValue("Typed").aliases.single())
    assertEquals(
      GeneratedTypeRef.scalar("string"),
      models.getValue("TypedComposition").aliases.single(),
    )
    val properties = models.getValue("Container").properties.associateBy { it.name }
    for (name in listOf("absent", "any", "nullableAny", "composed")) {
      assertEquals(
        GeneratedTypeRef.scalar("any", nullable = name in setOf("nullableAny", "composed")),
        properties.getValue(name).type,
      )
    }
    assertFalse(models.containsKey("ContainerNullableAny"))
    assertEquals(GeneratedModel.Kind.OBJECT, models.getValue("ContainerStructured").kind)
    assertTrue(properties.getValue("structured").type.nullable)
  }

  private fun api(
    directory: Path,
    version: String,
    schemas: String,
  ): Path =
    directory.resolve("api.yaml").also {
      it.writeText(
        "openapi: $version\ninfo: {title: Allocation, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    "),
      )
    }
}
