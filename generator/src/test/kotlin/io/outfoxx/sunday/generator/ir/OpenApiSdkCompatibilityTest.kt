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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.writeText

class OpenApiSdkCompatibilityTest {
  @Test
  fun `integer defaults and byte restrictions retain inherited declaration identities`(
    @TempDir directory: Path,
  ) {
    val api = convert(directory)
    val models = api.models.associateBy { it.name }
    val properties = GeneratedModelProperties { models[it.name] }
    val count = properties.fields(models.getValue("SdkIntegerChild")).single()
    assertTrue(count.inherited)
    assertEquals("1", count.effective.defaultValue)
    assertNull(count.declaration.defaultValue)
    assertEquals(GeneratedTypeRef.scalar("integer"), count.storage.type)
    val fields = properties.fields(models.getValue("SdkByteRestrictions")).associateBy { it.wireName }
    for (field in fields.values) {
      assertTrue(field.inherited)
      assertEquals(field.declaration.type, field.storage.type)
      assertEquals(GeneratedTypeRef.scalar("string", format = "byte"), properties.declarationType(field.storage.type))
      assertNull(field.declaration.allowedValues)
    }
    assertEquals(listOf("SGk="), fields.getValue("data").effective.allowedValues)
    assertTrue(fields.getValue("data").effective.required)
    assertFalse(fields.getValue("data").declaration.required)
    assertEquals(listOf("", "SGk=", "/wA="), fields.getValue("choices").effective.allowedValues)
    assertEquals(
      mapOf("minLength" to "4", "maxLength" to "4", "pattern" to "^SG"),
      fields.getValue("encoded").effective.validation,
    )
  }

  @Test
  fun `projected union aliases do not claim properties absent from their declaration`(
    @TempDir directory: Path,
  ) {
    val file = directory.resolve("projected-alias.yaml")
    file.writeText(
      OpenApiReferenceDocuments.document(
        "Alias projection",
        """
        Maybe:
          anyOf:
            - type: object
              properties: {value: {type: string}}
            - type: 'null'
        Annotated:
          ${'$'}ref: '#/components/schemas/Maybe'
          description: Alias detail
          readOnly: true
          deprecated: true
        """.trimIndent(),
      ),
    )
    val api = OpenApiToGeneratedApi().convert(file.toUri())
    assertEquals(
      GeneratedTypeRef.scalar("string"),
      api.models
        .single { it.name == "Annotated" }
        .properties
        .single()
        .type,
    )
  }

  @Test
  fun `recursive inline declarations are reserved before their containing model`(
    @TempDir directory: Path,
  ) {
    val file = directory.resolve("recursive-inline.yaml")
    file.writeText(
      OpenApiReferenceDocuments.document(
        "Recursive inline",
        OpenApiReferenceDocuments.sdkCompatibility
          .replace(
            "next: {${'$'}ref: '#/components/schemas/SdkInlineNode'}",
            "next: {${'$'}ref: '#/components/schemas/SdkInlineBase'}",
          ),
      ),
    )
    val converter = OpenApiToGeneratedApi()
    val api = converter.convert(file.toUri())
    val declared =
      api.models
        .single { it.name == "SdkInlineBase" }
        .properties
        .single { it.name == "detail" }
        .type
    assertEquals(
      declared,
      api.models
        .single { it.name == "SdkInlineChild" }
        .properties
        .single { it.name == "detail" }
        .type,
    )
    assertEquals(
      "SdkInlineBase",
      api.models
        .single {
          it.name == declared.name
        }.properties
        .single { it.name == "next" }
        .type.name,
    )
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      executor
        .invokeAll(
          List(4) { Callable { converter.convert(file.toUri()) } },
        ).forEach { assertEquals(api, it.get()) }
    }
  }

  @Test
  fun `inherited inline declarations retain identity across aliases and ordering`(
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      val declarations = OpenApiReferenceDocuments.sdkCompatibility.split(Regex("\n(?=[A-Z][A-Za-z]+:)"))
      val outputs =
        listOf(declarations, declarations.reversed()).mapIndexed { index, schemas ->
          val file = directory.resolve("inline-$version-$index.yaml")
          file.writeText(
            OpenApiReferenceDocuments.document("Inline", schemas.joinToString("\n")).replace("3.1.0", version),
          )
          OpenApiToGeneratedApi().convert(file.toUri())
        }
      outputs.forEach { api ->
        val parent = api.models.single { it.name == "SdkInlineBase" }
        val child = api.models.single { it.name == "SdkInlineChild" }
        val aliasChild = api.models.single { it.name == "SdkInlineAliasChild" }
        val alias = api.models.single { it.name == "SdkInlineAlias" }
        assertEquals(listOf(GeneratedTypeRef.named("SdkInlineAlias")), aliasChild.inherits)
        for (name in listOf("detail", "selection", "tags")) {
          assertEquals(
            parent.properties.single { it.name == name }.type,
            child.properties.single { it.name == name }.type,
          )
        }
        assertEquals(
          "SdkInlineBaseDetail2",
          parent.properties
            .single { it.name == "detail" }
            .type.name,
        )
        assertFalse(parent.properties.single { it.name == "detail" }.required)
        assertTrue(child.properties.single { it.name == "detail" }.required)
        assertEquals("true", child.properties.single { it.name == "tags" }.validation["uniqueItems"])
        assertTrue(
          api.models.none {
            it.name.startsWith("SdkInlineChildDetail") ||
              it.name.startsWith("SdkInlineChildSelection")
          },
        )
      }
      assertEquals(outputs[0].models, outputs[1].models)
    }
  }

  @Test
  fun `canonical wrappers preserve aliases recursion and genuine nullable contracts`(
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      val schemas =
        """
        Item:
          type: object
          required: [id]
          properties:
            id: {type: string}
            child: {${'$'}ref: '#/components/schemas/Item'}
        Alias: {${'$'}ref: '#/components/schemas/Item'}
        Wrapped: {allOf: [{${'$'}ref: '#/components/schemas/Alias'}], nullable: true}
        Nullable: {type: object, nullable: true, properties: {id: {type: string}}}
        Holder:
          type: object
          required: [item, nullable]
          properties:
            item: {${'$'}ref: '#/components/schemas/Item', nullable: true}
            wrapped: {allOf: [{${'$'}ref: '#/components/schemas/Alias'}], nullable: true}
            nullable: {allOf: [{${'$'}ref: '#/components/schemas/Nullable'}]}
            refined: {allOf: [{${'$'}ref: '#/components/schemas/Item'}, {properties: {detail: {type: string}}}]}
        """.trimIndent()
      val file = directory.resolve("wrappers-$version.yaml")
      file.writeText(OpenApiReferenceDocuments.document("Wrappers", schemas).replace("3.1.0", version))
      val api = OpenApiToGeneratedApi().convert(file.toUri())
      val holder =
        api.models
          .single { it.name == "Holder" }
          .properties
          .associateBy { it.name }
      assertEquals("Item", holder.getValue("item").type.name)
      assertTrue(holder.getValue("item").required)
      assertFalse(holder.getValue("item").type.nullable)
      assertEquals("Alias", holder.getValue("wrapped").type.name)
      assertFalse(holder.getValue("wrapped").required)
      assertTrue(holder.getValue("nullable").required)
      assertTrue(holder.getValue("nullable").type.nullable)
      assertEquals("HolderRefined", holder.getValue("refined").type.name)
      val wrapped = api.models.single { it.name == "Wrapped" }
      assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, wrapped.kind)
      assertTrue(wrapped.inherits.isEmpty())
      Executors.newVirtualThreadPerTaskExecutor().use { executor ->
        executor.invokeAll(List(4) { Callable { OpenApiToGeneratedApi().convert(file.toUri()) } }).forEach {
          assertEquals(api, it.get())
        }
      }
    }
  }

  @Test
  fun `nullable reference annotations cannot introduce a discriminator subtype`(
    @TempDir directory: Path,
  ) {
    val api = convert(directory)
    val field =
      api.models
        .single { it.name == "EntityDetails" }
        .properties
        .single()
    assertEquals("CurrentAsset", field.type.name)
    assertFalse(field.required)
    assertFalse(api.models.any { it.name == "EntityDetailsCurrentAsset" })
    assertEquals(
      setOf("rendered", "refused"),
      api.models
        .single { it.name == "CurrentAsset" }
        .discriminatorMappings.keys,
    )
  }

  @Test
  fun `inherited restrictions retain canonical enum types and parent storage`(
    @TempDir directory: Path,
  ) {
    val api = convert(directory)
    val models = api.models.associateBy { it.name }
    val child = models.getValue("CharacterChangeEvent")
    assertEquals(listOf(GeneratedTypeRef.named("BaseNarrativeChangeEvent")), child.inherits)
    val properties = child.properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("NarrativeChangeEventType"), properties.getValue("type").type)
    assertEquals(listOf("character"), properties.getValue("type").allowedValues)
    assertEquals("20", properties.getValue("count").defaultValue)
    assertEquals("1", properties.getValue("count").validation["minimum"])
    assertEquals(
      "0",
      models
        .getValue("BaseNarrativeChangeEvent")
        .properties
        .single {
          it.name == "count"
        }.validation["minimum"],
    )
    assertNull(
      models
        .getValue("BaseNarrativeChangeEvent")
        .properties
        .single { it.name == "type" }
        .allowedValues,
    )
    assertEquals(listOf(GeneratedTypeRef.named("FactEditOp")), models.getValue("AddFactOp").inherits)
    val problem = models.getValue("BadRequestProblem")
    assertEquals(listOf(GeneratedTypeRef.named("HttpProblem")), problem.inherits)
    assertEquals(listOf(400), problem.properties.single { it.name == "status" }.allowedValues)
    val fields = GeneratedModelProperties { models[it.name] }.fields(problem)
    val status = fields.single { it.wireName == "status" }
    assertTrue(status.storage.type.nullable)
    assertFalse(status.effective.type.nullable)
    assertEquals(5, fields.size)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
  }

  @Test
  fun `allowed values distinguish null zero false and absent metadata`() {
    val values = listOf(null, 0, false, "0")
    val property = GeneratedModelProperty("value", GeneratedTypeRef.scalar("any"), allowedValues = values)
    val api =
      GeneratedApi(
        name = "Values",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(GeneratedModel("Value", GeneratedModel.Kind.OBJECT, properties = listOf(property))),
      )
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    val old =
      GeneratedApiYaml.writeString(
        api.copy(models = listOf(api.models.single().copy(properties = listOf(property.copy(allowedValues = null))))),
      )
    assertFalse(old.contains("allowedValues"))
    assertNull(
      GeneratedApiYaml
        .readString(old)
        .models
        .single()
        .properties
        .single()
        .allowedValues,
    )
  }

  private fun convert(directory: Path): GeneratedApi {
    val file = directory.resolve("sdk.yaml")
    file.writeText(OpenApiReferenceDocuments.document("SDK compatibility", OpenApiReferenceDocuments.sdkCompatibility))
    return OpenApiToGeneratedApi().convert(file.toUri())
  }
}
