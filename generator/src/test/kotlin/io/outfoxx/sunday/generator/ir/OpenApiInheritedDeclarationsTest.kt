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

import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.writeText

class OpenApiInheritedDeclarationsTest {
  @Test
  fun `inferred mappings retain renamed imported wrappers`(
    @TempDir directory: Path,
  ) {
    directory.resolve("external.yaml").writeText(
      OpenApiReferenceDocuments.document(
        "External variants",
        """
        Pet:
          type: object
          required: [kind]
          properties: {kind: {type: string}}
          discriminator: {propertyName: kind}
        Cat: {allOf: [{${'$'}ref: '#/components/schemas/Pet'}], properties: {name: {type: string}}}
        WrappedCat: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}
        """.trimIndent(),
      ),
    )
    val api =
      convert(
        directory,
        """
        WrappedCat: {type: object, properties: {local: {type: boolean}}}
        Imported: {${'$'}ref: 'external.yaml#/components/schemas/Pet'}
        Variant: {type: object, properties: {pet: {${'$'}ref: 'external.yaml#/components/schemas/WrappedCat'}}}
        """.trimIndent(),
      )
    val pet = api.models.single { it.name == "Imported" }
    val target = pet.discriminatorMappings.getValue("WrappedCat")
    assertEquals("WrappedCat2", target.name)
    val variant = api.models.single { it.name == target.name }
    assertEquals(GeneratedModel.Kind.OBJECT, variant.kind)
    assertEquals("WrappedCat", variant.discriminatorValue)
  }

  @Test
  fun `inline response discriminators retain selected declarations`(
    @TempDir directory: Path,
  ) {
    val file = directory.resolve("inline.yaml")
    file.writeText(
      """
      openapi: 3.1.0
      info: {title: Inline discriminator, version: '1'}
      paths:
        /pets:
          get:
            responses:
              '200':
                description: Pet
                content:
                  application/json:
                    schema:
                      oneOf: [{${'$'}ref: '#/components/schemas/WrappedCat'}]
                      discriminator:
                        propertyName: kind
                        mapping: {cat: '#/components/schemas/WrappedCat'}
      components:
        schemas:
          Cat: {type: object, properties: {kind: {type: string}, name: {type: string}}}
          WrappedCat: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(file.toUri())
    assertEquals(GeneratedModel.Kind.OBJECT, api.models.single { it.name == "WrappedCat" }.kind)
  }

  @Test
  fun `discriminator selected wrappers retain identities before alias projection`(
    @TempDir directory: Path,
  ) {
    val declarations =
      listOf(
        """
        Pet:
          type: object
          required: [kind]
          properties: {kind: {type: string}}
          discriminator:
            propertyName: kind
            mapping: {cat: '#/components/schemas/WrappedCat', feline: '#/components/schemas/OtherCat'}
        """.trimIndent(),
        """
        Cat:
          type: object
          allOf: [{${'$'}ref: '#/components/schemas/Pet'}]
          required: [name]
          properties:
            name: {type: string}
            next: {${'$'}ref: '#/components/schemas/Pet'}
        """.trimIndent(),
        "Ordinary: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}",
        "WrappedCat: {type: object, description: Wrapped cat, allOf: [{${'$'}ref: '#/components/schemas/Ordinary'}]}",
        "OtherCat: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}",
        "Child: {allOf: [{${'$'}ref: '#/components/schemas/WrappedCat'}], properties: {extra: {type: string}}}",
        "Branch: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}",
        "Choice: {oneOf: [{${'$'}ref: '#/components/schemas/Branch'}], discriminator: {propertyName: kind}}",
        "DataOnly: {type: object, allOf: [{${'$'}ref: '#/components/schemas/Cat'}]}",
        """
        Literal:
          type: string
          example: {discriminator: {mapping: {data: '#/components/schemas/DataOnly'}}}
        """.trimIndent(),
      )
    for (version in listOf("3.0.3", "3.1.0")) {
      for (keyword in listOf("oneOf", "anyOf")) {
        for (ordered in listOf(declarations, declarations.reversed())) {
          val api = convert(directory, ordered.joinToString("\n").replace("oneOf:", "$keyword:"), version)
          val models = api.models.associateBy { it.name }
          for (name in listOf("WrappedCat", "OtherCat", "Branch")) {
            assertEquals(GeneratedModel.Kind.OBJECT, models.getValue(name).kind)
            assertEquals(listOf(GeneratedTypeRef.named("Cat")), models.getValue(name).inherits)
            assertTrue(models.getValue(name).properties.isEmpty())
          }
          for (name in listOf("Ordinary", "DataOnly")) {
            assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, models.getValue(name).kind)
          }
          assertEquals("cat", models.getValue("WrappedCat").discriminatorValue)
          assertEquals("feline", models.getValue("OtherCat").discriminatorValue)
          assertEquals("Wrapped cat", models.getValue("WrappedCat").documentation?.description)
          assertEquals(listOf(GeneratedTypeRef.named("WrappedCat")), models.getValue("Child").inherits)
          assertEquals(
            mapOf("cat" to GeneratedTypeRef.named("WrappedCat"), "feline" to GeneratedTypeRef.named("OtherCat")),
            models.getValue("Pet").discriminatorMappings,
          )
          val uri = directory.resolve("api.yaml").toUri()
          val converter = OpenApiToGeneratedApi()
          Executors.newFixedThreadPool(2).use { executor ->
            executor.invokeAll(List(4) { Callable { converter.convert(uri) } }).forEach { assertEquals(api, it.get()) }
          }
        }
      }
    }
  }

  @Test
  fun `collapsed wrappers canonicalize inheritance without erasing object declarations`(
    @TempDir directory: Path,
  ) {
    val declarations =
      listOf(
        """
        Base:
          type: object
          required: [label]
          properties:
            label: {type: string, description: Parent label}
            count: {type: integer, minimum: 0, default: 1}
            next: {${'$'}ref: '#/components/schemas/Base'}
        """.trimIndent(),
        """
        Wrapper:
          type: object
          description: Wrapped base
          allOf: [{${'$'}ref: '#/components/schemas/Base'}]
        """.trimIndent(),
        """
        Chain:
          type: object
          allOf: [{${'$'}ref: '#/components/schemas/Wrapper'}]
        """.trimIndent(),
        "Expanded: {${'$'}ref: '#/components/schemas/Base'}",
        """
        Refined:
          allOf: [{${'$'}ref: '#/components/schemas/Chain'}]
          properties: {count: {minimum: 2, default: 2}}
        """.trimIndent(),
        """
        Child:
          allOf: [{${'$'}ref: '#/components/schemas/Chain'}, {${'$'}ref: '#/components/schemas/Base'}]
          required: [extra]
          properties: {extra: {type: string}}
        """.trimIndent(),
        """
        ExpandedChild:
          allOf: [{${'$'}ref: '#/components/schemas/Expanded'}]
          properties: {extra: {type: string}}
        """.trimIndent(),
        """
        RefinedChild:
          allOf: [{${'$'}ref: '#/components/schemas/Refined'}]
          properties: {extra: {type: string}}
        """.trimIndent(),
      )
    for (version in listOf("3.0.3", "3.1.0")) {
      for (schemas in listOf(declarations, declarations.reversed())) {
        val api = convert(directory, schemas.joinToString("\n"), version)
        val models = api.models.associateBy { it.name }
        val fields = GeneratedModelProperties { models[it.name] }
        for (name in listOf("Wrapper", "Chain")) {
          assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, models.getValue(name).kind)
          assertTrue(models.getValue(name).inherits.isEmpty())
        }
        assertEquals(listOf(GeneratedTypeRef.named("Base")), models.getValue("Child").inherits)
        assertEquals(listOf(GeneratedTypeRef.named("Base")), models.getValue("Refined").inherits)
        assertEquals(listOf(GeneratedTypeRef.named("Refined")), models.getValue("RefinedChild").inherits)
        assertEquals(listOf(GeneratedTypeRef.named("Expanded")), models.getValue("ExpandedChild").inherits)
        assertEquals(GeneratedModel.Kind.OBJECT, models.getValue("Expanded").kind)
        assertEquals(listOf("extra"), models.getValue("Child").properties.map { it.name })
        val childFields = fields.fields(models.getValue("Child"))
        assertEquals(setOf("label", "count", "next", "extra"), childFields.map { it.wireName }.toSet())
        assertTrue(childFields.single { it.wireName == "label" }.effective.required)
        assertEquals(GeneratedTypeRef.named("Base"), childFields.single { it.wireName == "next" }.effective.type)
        val count = fields.fields(models.getValue("Refined")).single { it.wireName == "count" }
        assertEquals("2", count.effective.defaultValue)
        assertEquals(mapOf("minimum" to "2"), count.effective.validation)
        assertEquals("1", count.declaration.defaultValue)
        val label = models.getValue("Base").properties.first()
        assertEquals("Parent label", label.documentation?.description)
        Executors.newFixedThreadPool(2).use { executor ->
          val file = directory.resolve("api.yaml").toUri()
          executor.invokeAll(List(4) { Callable { OpenApiToGeneratedApi().convert(file) } }).forEach {
            assertEquals(api, it.get())
          }
        }
      }
    }
  }

  @Test
  fun `refined parents with different fields flatten without losing either contract`(
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      for (parents in listOf(listOf("First", "Second"), listOf("Second", "First"))) {
        for (suffix in listOf("", "Alias")) {
          val api =
            convert(
              directory,
              """
              First:
                type: object
                required: [a]
                properties:
                  a: {type: string}
                  count: {type: integer, minimum: 0, default: 1}
                  state: {type: string, enum: [a, b]}
              Second:
                type: object
                required: [b]
                properties:
                  b: {type: string, nullable: true}
                  count: {type: integer, maximum: 10}
                  state: {type: string, enum: [b, c]}
              FirstAlias: {${'$'}ref: '#/components/schemas/First'}
              SecondAlias: {${'$'}ref: '#/components/schemas/Second'}
              Child:
                allOf: [${parents.joinToString { "{${'$'}ref: '#/components/schemas/$it$suffix'}" }}]
                required: [count]
                properties:
                  count: {minimum: 1, default: 2}
                  b: {type: string, nullable: false}
              """.trimIndent(),
              version,
            )
          val child = api.models.single { it.name == "Child" }
          val fields = child.properties.associateBy { it.name }
          assertTrue(child.inherits.isEmpty())
          assertEquals(setOf("a", "b", "count", "state"), fields.keys)
          assertEquals(
            setOf("a", "b", "count"),
            child.properties
              .filter { it.required }
              .map { it.name }
              .toSet(),
          )
          assertFalse(fields.getValue("b").type.nullable)
          assertEquals(mapOf("minimum" to "1", "maximum" to "10"), fields.getValue("count").validation)
          assertEquals("2", fields.getValue("count").defaultValue)
          assertEquals(listOf("b"), fields.getValue("state").allowedValues)
          val first =
            api.models
              .single { it.name == "First" }
              .properties
              .associateBy { it.name }
          assertEquals("1", first.getValue("count").defaultValue)
          assertEquals(mapOf("minimum" to "0"), first.getValue("count").validation)
          assertEquals(listOf("a", "b"), first.getValue("state").allowedValues)
          assertTrue(
            api.models
              .single { it.name == "Second" }
              .properties
              .single { it.name == "b" }
              .type.nullable,
          )
          assertEquals(api, OpenApiToGeneratedApi().convert(directory.resolve("api.yaml").toUri()))
        }
      }
    }
  }

  @Test
  fun `different nominal parents flatten the complete intersection in either order`(
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      for (parents in listOf(listOf("First", "Second"), listOf("Second", "First"))) {
        val api =
          convert(
            directory,
            """
            FirstStatus: {type: string, enum: [a, b]}
            SecondStatus: {type: string, enum: [b, c]}
            First: {type: object, properties: {status: {${'$'}ref: '#/components/schemas/FirstStatus'}}}
            Second: {type: object, properties: {status: {${'$'}ref: '#/components/schemas/SecondStatus'}}}
            Child:
              allOf: [${parents.joinToString { "{${'$'}ref: '#/components/schemas/$it'}" }}]
            """.trimIndent(),
            version,
          )
        val child = api.models.single { it.name == "Child" }
        assertTrue(child.inherits.isEmpty())
        assertEquals(GeneratedTypeRef.scalar("string"), child.properties.single().type)
        assertEquals(listOf("b"), child.properties.single().allowedValues)
        assertEquals(listOf("a", "b"), api.models.single { it.name == "FirstStatus" }.values)
        assertEquals(listOf("b", "c"), api.models.single { it.name == "SecondStatus" }.values)
      }
    }
  }

  @Test
  fun `a shared declaration keeps the required intersection in both parent orders`(
    @TempDir directory: Path,
  ) {
    for (parents in listOf(listOf("Required", "Optional"), listOf("Optional", "Required"))) {
      val api =
        convert(
          directory,
          """
          Status: {type: string, enum: [a, b]}
          Root: {type: object, properties: {status: {${'$'}ref: '#/components/schemas/Status'}}}
          Required: {allOf: [{${'$'}ref: '#/components/schemas/Root'}], required: [status]}
          Optional: {allOf: [{${'$'}ref: '#/components/schemas/Root'}]}
          Child:
            allOf: [${parents.joinToString { "{${'$'}ref: '#/components/schemas/$it'}" }}]
          """.trimIndent(),
        )
      val child = api.models.single { it.name == "Child" }
      val root = api.models.single { it.name == "Root" }
      assertEquals(parents, child.inherits.map { it.name })
      assertTrue(child.properties.single().required)
      assertEquals(GeneratedTypeRef.named("Status"), child.properties.single().type)
      val models = api.models.associateBy { it.name }
      val fields = GeneratedModelProperties { models[it.name] }.fields(child)
      assertTrue(fields.single().effective.required)
      assertFalse(fields.single().storage.required)
      assertFalse(root.properties.single().required)
    }
  }

  @Test
  fun `shared declarations and equivalent primitive collections keep inheritance`(
    @TempDir directory: Path,
  ) {
    val schemas =
      """
      Status: {type: string, enum: [a, b]}
      Number: {type: integer}
      NumberAlias: {${'$'}ref: '#/components/schemas/Number'}
      Root:
        type: object
        properties:
          detail: {type: object, properties: {value: {type: string}}}
          status: {${'$'}ref: '#/components/schemas/Status'}
      RootAlias: {${'$'}ref: '#/components/schemas/Root'}
      First: {allOf: [{${'$'}ref: '#/components/schemas/RootAlias'}]}
      Second: {allOf: [{${'$'}ref: '#/components/schemas/Root'}]}
      Child:
        allOf: [{${'$'}ref: '#/components/schemas/First'}, {${'$'}ref: '#/components/schemas/Second'}]
        required: [detail]
        properties: {status: {enum: [b]}}
      NumbersOne:
        type: object
        properties:
          values: {type: array, items: {${'$'}ref: '#/components/schemas/Number'}}
          arbitrary: {}
          state: {enum: [a, b]}
      NumbersTwo:
        type: object
        properties:
          values: {type: array, items: {${'$'}ref: '#/components/schemas/NumberAlias'}}
          arbitrary: {description: Unrestricted}
          state: {enum: [b, c]}
      Numbers:
        allOf: [{${'$'}ref: '#/components/schemas/NumbersOne'}, {${'$'}ref: '#/components/schemas/NumbersTwo'}]
        properties: {values: {minItems: 1}}
      Nodes: {type: array, items: {${'$'}ref: '#/components/schemas/Nodes'}}
      NodesOne: {type: object, properties: {nodes: {${'$'}ref: '#/components/schemas/Nodes'}}}
      NodesTwo: {type: object, properties: {nodes: {${'$'}ref: '#/components/schemas/Nodes'}}}
      Recursive:
        allOf: [{${'$'}ref: '#/components/schemas/NodesOne'}, {${'$'}ref: '#/components/schemas/NodesTwo'}]
      """.trimIndent()
    val api = convert(directory, schemas)
    val child = api.models.single { it.name == "Child" }
    val root = api.models.single { it.name == "Root" }
    assertEquals(listOf("First", "Second"), child.inherits.map { it.name })
    assertEquals(
      root.properties.single { it.name == "detail" }.type,
      child.properties.single { it.name == "detail" }.type,
    )
    assertEquals(GeneratedTypeRef.named("Status"), child.properties.single { it.name == "status" }.type)
    assertFalse(root.properties.single { it.name == "detail" }.required)
    assertEquals(
      listOf("NumbersOne", "NumbersTwo"),
      api.models
        .single { it.name == "Numbers" }
        .inherits
        .map { it.name },
    )
    assertEquals(
      listOf("NodesOne", "NodesTwo"),
      api.models
        .single { it.name == "Recursive" }
        .inherits
        .map { it.name },
    )
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      val uri = directory.resolve("api.yaml").toUri()
      val converter = OpenApiToGeneratedApi()
      executor.invokeAll(List(4) { Callable { converter.convert(uri) } }).forEach { assertEquals(api, it.get()) }
    }
  }

  @Test
  fun `unrelated inline identities cannot be collapsed by documentary equality`(
    @TempDir directory: Path,
  ) {
    for (field in listOf(
      "{type: object, properties: {value: {type: string}}}",
      "{oneOf: [{type: string}, {type: integer}]}",
    )) {
      val api =
        convert(
          directory,
          """
          First: {type: object, properties: {value: $field}}
          Second: {type: object, properties: {value: $field}}
          Child: {allOf: [{${'$'}ref: '#/components/schemas/First'}, {${'$'}ref: '#/components/schemas/Second'}]}
          """.trimIndent(),
        )
      assertTrue(
        api.models
          .single { it.name == "Child" }
          .inherits
          .isEmpty(),
      )
      assertEquals(
        "ChildValue",
        api.models
          .single { it.name == "Child" }
          .properties
          .single()
          .type.name,
      )
    }
  }

  private fun convert(
    directory: Path,
    schemas: String,
    version: String = "3.1.0",
  ): GeneratedApi {
    val file = directory.resolve("api.yaml")
    file.writeText(OpenApiReferenceDocuments.document("Inherited declarations", schemas).replace("3.1.0", version))
    return OpenApiToGeneratedApi().convert(file.toUri())
  }
}
