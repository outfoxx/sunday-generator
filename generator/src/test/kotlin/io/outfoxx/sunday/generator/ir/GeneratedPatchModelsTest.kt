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

import io.outfoxx.sunday.generator.tools.patchableApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class GeneratedPatchModelsTest {
  @ParameterizedTest
  @ValueSource(strings = ["composed-collisions", "composed-collisions-reversed"])
  fun `composition reserves source names before assigning shared companion names`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory)
    assertEquals(
      api.models.size,
      api.models
        .map { it.name }
        .distinct()
        .size,
    )
    val models = api.models.associateBy { it.name }
    listOf("SomeRequestPatch", "BasePatch", "DetailsPatch", "DetailsPatch2").forEach { name ->
      assertFalse(models.getValue(name).patchable)
      assertEquals(
        "reserved",
        models
          .getValue(name)
          .properties
          .single()
          .name,
      )
    }
    val request = models.getValue("SomeRequestPatch2")
    assertEquals("SomeRequest", request.patchOf?.name)
    assertEquals("BasePatch2", request.inherits.single().name)
    assertEquals(
      "DetailsPatch3",
      request.properties
        .single { it.name == "details" }
        .type.name,
    )
    val details = models.getValue("DetailsPatch3")
    assertEquals("Details", details.patchOf?.name)
    assertEquals(
      "DetailsPatch3",
      details.properties
        .single { it.name == "child" }
        .type.name,
    )
    api.services.flatMap { it.operations }.filter { it.id == "updateRequest" }.forEach {
      assertEquals("SomeRequestPatch2", it.requestBody!!.type.name)
      assertTrue(it.requestBody.payloads.all { option -> option.type.name == "SomeRequestPatch2" })
    }
    val fragment = GeneratedApiFragment(api, GeneratedIdentity.native("patch"))
    assertEquals(api, GeneratedApiComposer().compose(listOf(fragment, fragment)))
  }

  @Test
  fun `inherited requiredness and allowed values survive patch projection`() {
    val required = GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true)
    val base = GeneratedModel("Base", GeneratedModel.Kind.OBJECT, properties = listOf(required))
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        patchable = true,
        inherits = listOf(GeneratedTypeRef.named("Base")),
        properties =
          listOf(
            required.copy(required = false, type = required.type.copy(nullable = true)),
            GeneratedModelProperty("tag", GeneratedTypeRef.scalar("string"), allowedValues = listOf("fixed")),
            GeneratedModelProperty(
              "onlyNull",
              GeneratedTypeRef.scalar("string").copy(nullable = true),
              required = true,
              allowedValues = listOf(null),
            ),
          ),
      )
    val api =
      GeneratedPatchModels.materialize(
        GeneratedApi(
          name = "Inheritance",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          models = listOf(base, child),
        ),
      )
    val fields =
      api.models
        .single { it.name == "ChildPatch" }
        .properties
        .associateBy { it.name }
    assertEquals(false, fields.getValue("id").patchDeletionAllowed)
    assertEquals(listOf("fixed", null), fields.getValue("tag").allowedValues)
    assertEquals(emptyList<Any?>(), fields.getValue("onlyNull").allowedValues)
    val roundTrip = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    assertEquals(api, roundTrip)
    assertEquals(api.models, GeneratedPatchModels.normalizeFields(roundTrip.models))
    assertEquals(api, GeneratedPatchModels.materialize(api))
  }

  @Test
  fun `open member constraints allow deletion in companions without changing the original`() {
    val model =
      GeneratedModel(
        "Item",
        GeneratedModel.Kind.OBJECT,
        patchable = true,
        additionalProperties =
          GeneratedAdditionalProperties(
            allowed = true,
            type = GeneratedTypeRef.scalar("string"),
            allowedValues = listOf("fixed"),
          ),
        patternProperties =
          listOf(
            GeneratedPatternProperty(
              "^x-",
              GeneratedTypeRef.scalar("string"),
              allowedValues = listOf("fixed"),
            ),
          ),
      )
    val api =
      GeneratedPatchModels.materialize(
        GeneratedApi(
          name = "OpenPatch",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          models = listOf(model),
        ),
      )
    val original = api.models.single { it.name == "Item" }
    val patch = api.models.single { it.name == "ItemPatch" }
    assertEquals(listOf("fixed"), original.additionalProperties?.allowedValues)
    assertEquals(listOf("fixed"), original.patternProperties.single().allowedValues)
    assertEquals(listOf("fixed", null), patch.additionalProperties?.allowedValues)
    assertEquals(listOf("fixed", null), patch.patternProperties.single().allowedValues)
    assertEquals(true, patch.additionalProperties?.type?.nullable)
    assertTrue(
      patch.patternProperties
        .single()
        .type.nullable,
    )
  }

  @Test
  fun `patch deletion metadata survives serialization and repeated normalization`() {
    val model =
      GeneratedModel(
        "Item",
        GeneratedModel.Kind.OBJECT,
        patchable = true,
        properties =
          listOf(
            GeneratedModelProperty("required", GeneratedTypeRef.scalar("string"), required = true),
            GeneratedModelProperty(
              "requiredNull",
              GeneratedTypeRef.scalar("string").copy(nullable = true),
              required = true,
            ),
            GeneratedModelProperty("optional", GeneratedTypeRef.scalar("string")),
            GeneratedModelProperty("optionalNull", GeneratedTypeRef.scalar("string").copy(nullable = true)),
          ),
      )
    val api =
      GeneratedApi(
        name = "Patch",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = GeneratedPatchModels.normalizeFields(listOf(model)),
      )
    val roundTrip = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    assertEquals(api, roundTrip)
    assertEquals(api.models, GeneratedPatchModels.normalizeFields(roundTrip.models))
    assertEquals(
      listOf(false, false, true, true),
      api.models
        .single()
        .properties
        .map { it.patchDeletionAllowed },
    )
    assertEquals(
      listOf(false, false, true, true),
      api.models
        .single()
        .properties
        .map { it.type.nullable },
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference"])
  fun `disabling automatic promotion preserves ordinary bodies and explicitly declared patch models`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory, autoPatchable = false)
    assertEquals(
      frontend in setOf("raml", "asyncapi", "composed"),
      api.models.any { it.name == "SomeRequestPatch" && it.patchable },
    )
    api.services.flatMap { it.operations }.filter { it.id == "updateRequest" }.forEach { operation ->
      assertEquals(if (frontend == "raml") "SomeRequestPatch" else "SomeRequest", operation.requestBody!!.type.name)
    }
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference"])
  fun `IR round trips retain patch fields and operation selection`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory)
    assertEquals("1", api.irVersion)
    assertEquals(api, GeneratedPatchModels.materialize(api))
    val models = api.models.associateBy { it.name }
    assertFalse(models.getValue("SomeRequest").patchable)
    val patch = models.getValue("SomeRequestPatch")
    assertTrue(patch.patchable)
    assertEquals(GeneratedTypeRef.named("SomeRequest"), patch.patchOf)
    assertTrue(models.getValue("DetailsPatch").patchable)
    assertEquals(
      "DetailsPatch",
      patch.properties
        .single { it.name == "details" }
        .type.name,
    )
    val basePatch = models.getValue(patch.inherits.single().name)
    assertTrue(basePatch.patchable)
    assertEquals("1", basePatch.properties.single { it.name == "count" }.validation["minimum"])
    assertEquals("2", patch.properties.single { it.name == "title" }.validation["minLength"])
    assertTrue(
      patch.properties
        .single { it.name == "description" }
        .type.nullable,
    )
    assertTrue(patch.properties.all { !it.required && it.defaultValue == null })
    assertTrue(basePatch.properties.all { !it.required && it.defaultValue == null })
    val operations = api.services.flatMap { it.operations }
    operations.filter { it.id == "updateRequest" }.forEach { operation ->
      assertEquals("PUT", operation.method)
      assertEquals("SomeRequestPatch", operation.requestBody!!.type.name)
      assertTrue(operation.requestBody.payloads.all { it.type.name == "SomeRequestPatch" })
    }
    operations.filter { it.id in setOf("createRequest", "plainRequest") }.forEach { operation ->
      assertEquals("SomeRequest", operation.requestBody!!.type.name)
    }
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
  }

  @ParameterizedTest
  @ValueSource(strings = ["PATCH", "POST", "PUT", "DELETE"])
  fun `merge patch media automatically selects a companion regardless of HTTP method`(method: String) {
    val ordinary = GeneratedTypeRef.named("Item")
    val source =
      GeneratedApi(
        name = "Automatic",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(GeneratedModel("Item", GeneratedModel.Kind.OBJECT)),
        services =
          listOf(
            GeneratedService(
              "Items",
              operations =
                listOf(
                  GeneratedOperation(
                    "update",
                    method,
                    "/items",
                    requestBody =
                      GeneratedPayload(
                        ordinary,
                        mediaTypes = listOf("Application/Merge-Patch+Json; charset=utf-8", "application/json"),
                      ),
                    responses = listOf(GeneratedResponse(200, ordinary)),
                  ),
                ),
            ),
          ),
      )
    val api = GeneratedPatchModels.materialize(source)
    val operation =
      api.services
        .single()
        .operations
        .single()
    val body = requireNotNull(operation.requestBody)
    assertEquals(GeneratedTypeRef.named("ItemPatch"), body.type)
    assertEquals(listOf("Application/Merge-Patch+Json; charset=utf-8"), body.mediaTypes)
    assertEquals(listOf("ItemPatch", "Item"), body.payloads.map { it.type.name })
    assertEquals(ordinary, operation.responses.single().type)
    assertFalse(api.models.single { it.name == "Item" }.patchable)
    assertTrue(api.models.single { it.name == "ItemPatch" }.patchable)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    val explicit =
      GeneratedPatchModels.materialize(
        source.copy(models = source.models.map { it.copy(patchable = true) }),
        autoPatchable = false,
      )
    assertEquals(api, explicit)
  }

  @ParameterizedTest
  @ValueSource(strings = ["application/json", "application/json-patch+json", "application/vnd.example+json", "*/*"])
  fun `other content types retain ordinary models even for annotated PATCH bodies`(mediaType: String) {
    val ordinary = GeneratedTypeRef.named("Item")
    val source =
      GeneratedApi(
        name = "Ordinary",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(GeneratedModel("Item", GeneratedModel.Kind.OBJECT, patchable = true)),
        services =
          listOf(
            GeneratedService(
              "Items",
              operations =
                listOf(
                  GeneratedOperation(
                    "update",
                    "PATCH",
                    "/items",
                    requestBody = GeneratedPayload(ordinary, listOf(mediaType)),
                  ),
                ),
            ),
          ),
      )
    val api = GeneratedPatchModels.materialize(source)
    assertEquals(
      ordinary,
      api.services
        .single()
        .operations
        .single()
        .requestBody!!
        .type,
    )
    assertTrue(api.models.single { it.name == "ItemPatch" }.patchable)
  }

  @Test
  fun `media alternatives preserve the ordinary primary type and alternatives without explicit media`() {
    val ordinary = GeneratedTypeRef.named("Item")
    val source =
      GeneratedApi(
        name = "Alternatives",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(GeneratedModel("Item", GeneratedModel.Kind.OBJECT)),
        services =
          listOf(
            GeneratedService(
              "Items",
              operations =
                listOf(
                  GeneratedOperation(
                    "update",
                    "POST",
                    "/items",
                    requestBody =
                      GeneratedPayload(
                        ordinary,
                        mediaTypes = listOf("application/json"),
                        payloads =
                          listOf(
                            GeneratedPayloadOption(ordinary, listOf("application/json")),
                            GeneratedPayloadOption(ordinary, listOf("application/merge-patch+json")),
                            GeneratedPayloadOption(GeneratedTypeRef.scalar("binary")),
                          ),
                      ),
                  ),
                ),
            ),
          ),
      )
    val body =
      GeneratedPatchModels
        .materialize(source)
        .services
        .single()
        .operations
        .single()
        .requestBody!!
    assertEquals(ordinary, body.type)
    assertEquals(listOf("application/json"), body.mediaTypes)
    assertEquals(listOf("Item", "ItemPatch", "binary"), body.payloads.map { it.type.name })
    assertTrue(
      body.payloads
        .last()
        .mediaTypes
        .isEmpty(),
    )
  }

  @Test
  fun `patch allocation preserves aliases and existing names`() {
    val source =
      GeneratedApi(
        name = "Aliases",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              "Item",
              GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"))),
            ),
            GeneratedModel("ItemPatch", GeneratedModel.Kind.OBJECT),
            GeneratedModel(
              "ItemAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              patchable = true,
              aliases = listOf(GeneratedTypeRef.named("Item")),
            ),
          ),
      )
    val models = GeneratedPatchModels.materialize(source).models.associateBy { it.name }
    assertFalse(models.getValue("Item").patchable)
    assertFalse(models.getValue("ItemPatch").patchable)
    assertTrue(models.getValue("ItemPatch2").patchable)
    assertTrue(models.getValue("ItemAliasPatch").patchable)
    assertEquals(GeneratedTypeRef.named("ItemPatch2"), models.getValue("ItemAliasPatch").aliases.single())
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inherited companion declarations do not implicitly promote unannotated parent bodies`(automatic: Boolean) {
    val source =
      GeneratedApi(
        name = "Inheritance",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel("Base", GeneratedModel.Kind.OBJECT),
            GeneratedModel(
              "Child",
              GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Base")),
              patchable = true,
            ),
          ),
        services =
          listOf(
            GeneratedService(
              "Items",
              operations =
                listOf("Base", "Child").map { name ->
                  GeneratedOperation(
                    name,
                    "POST",
                    "/$name",
                    requestBody =
                      GeneratedPayload(
                        GeneratedTypeRef.named(name),
                        listOf("application/merge-patch+json"),
                      ),
                  )
                },
            ),
          ),
      )
    val api = GeneratedPatchModels.materialize(source, autoPatchable = automatic)
    val operations =
      api.services
        .single()
        .operations
        .associateBy { it.id }
    assertEquals(
      if (automatic) "BasePatch" else "Base",
      operations
        .getValue("Base")
        .requestBody!!
        .type.name,
    )
    assertEquals(
      "ChildPatch",
      operations
        .getValue("Child")
        .requestBody!!
        .type.name,
    )
    assertEquals(
      "BasePatch",
      api.models
        .single { it.name == "ChildPatch" }
        .inherits
        .single()
        .name,
    )
    assertTrue(api.models.single { it.name == "BasePatch" }.patchable)
  }

  @Test
  fun `invalid patch declarations fail before emitting unusable model types`() {
    assertThrows(Exception::class.java) { GeneratedPatchModels.annotation("true", "test") }
    val source =
      GeneratedApi(
        name = "Invalid",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel("State", GeneratedModel.Kind.ENUM, values = listOf("active"), patchable = true),
          ),
      )
    val error = assertThrows(Exception::class.java) { GeneratedPatchModels.materialize(source) }
    assertTrue(error.message.orEmpty().contains("must be an object schema"))
    val recursiveMap =
      source.copy(
        models =
          listOf(
            GeneratedModel("Map", GeneratedModel.Kind.MAP, aliases = listOf(GeneratedTypeRef.named("Map"))),
            GeneratedModel(
              "Item",
              GeneratedModel.Kind.OBJECT,
              patchable = true,
              properties =
                listOf(
                  GeneratedModelProperty("values", GeneratedTypeRef.named("Map")),
                ),
            ),
          ),
      )
    val mapError = assertThrows(Exception::class.java) { GeneratedPatchModels.materialize(recursiveMap) }
    assertTrue(mapError.message.orEmpty().contains("Recursive map schema 'Map'"))
  }
}
