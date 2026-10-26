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
import io.outfoxx.sunday.generator.tools.closedModelsApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class UnevaluatedPropertiesTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `explicit storage declarations remain distinguishable from implicit openness`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val models = closedModelsApi(frontend, directory).models.associateBy { it.name }
    assertNull(models.getValue("OpenRecord").additionalProperties, frontend)
    assertEquals(true, models.getValue("ExplicitRecord").additionalProperties?.allowed, frontend)
    assertEquals(true, models.getValue("ExplicitChild").additionalProperties?.allowed, frontend)
  }

  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi"])
  fun `unevaluated closure retains named inline empty and inherited objects`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val models = models(frontend, directory)
    for (name in listOf("Closed", "Empty", "Child", "Pattern", "Both")) {
      val model = models.getValue(name)
      assertEquals(GeneratedModel.Kind.OBJECT, model.kind, name)
      assertEquals(true, model.closed, name)
      assertEquals(false, model.additionalProperties?.allowed, name)
    }
    val nested =
      models
        .getValue("Closed")
        .properties
        .single { it.name == "nested" }
        .type
    assertEquals(true, models.getValue(nested.name).closed)
    assertEquals(listOf("^x-"), models.getValue("Pattern").patternProperties.map { it.pattern })
    for (name in listOf("Open", "ExplicitOpen", "TypedOpen", "InheritedOpen", "InheritedUnevaluatedOpen")) {
      assertNull(models.getValue(name).closed, name)
    }
    assertEquals(true, models.getValue("ExplicitOpen").additionalProperties?.allowed)
    assertEquals(
      "string",
      models
        .getValue("TypedOpen")
        .additionalProperties
        ?.type
        ?.name,
    )
  }

  @Test
  fun `an inner unevaluated closure cannot be extended by an outer conjunction`() {
    val closed = mapOf("properties" to mapOf("id" to mapOf("type" to "string")), "unevaluatedProperties" to false)
    val extra = mapOf("properties" to mapOf("name" to mapOf("type" to "string")))
    assertThrows(GenerationException::class.java) {
      OpenApiSchemaComposition(emptyMap()).resolve(mapOf("allOf" to listOf(closed, extra)))
    }
    val outer =
      OpenApiSchemaComposition(emptyMap()).resolve(
        mapOf("allOf" to listOf(closed - "unevaluatedProperties", extra), "unevaluatedProperties" to false),
      )
    assertEquals(setOf("id", "name"), (outer["properties"] as Map<*, *>).keys)
    assertEquals(false, outer["additionalProperties"])
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `union closure is applied after its branch properties are evaluated`(keyword: String) {
    val leaf = mapOf("type" to "object", "properties" to mapOf("id" to mapOf("type" to "string")))
    val analysis = OpenApiSchemaAnalysis(mapOf("Leaf" to leaf))
    val reference = mapOf("${'$'}ref" to "#/components/schemas/Leaf")
    val union = mapOf(keyword to listOf(reference), "unevaluatedProperties" to false)
    val branch = analysis.unionBranch(analysis.analyze(union).effective, reference)
    assertEquals(leaf["properties"], branch["properties"])
    assertEquals(false, branch["additionalProperties"])
  }

  private fun models(
    frontend: String,
    directory: Path,
  ): Map<String, GeneratedModel> {
    val source = directory.resolve("api.yaml")
    val header = if (frontend == "openapi") "openapi: 3.1.0\npaths: {}" else "asyncapi: 3.0.0\nchannels: {}"
    source.writeText(
      header + "\n" +
        """
        info: {title: Unevaluated properties, version: 1.0.0}
        components:
          schemas:
            Closed:
              type: object
              unevaluatedProperties: false
              properties:
                id: {type: string}
                nested: {type: object, unevaluatedProperties: false, properties: {value: {type: string}}}
            Empty: {unevaluatedProperties: false}
            Base:
              type: object
              properties: {id: {type: string}}
            Child:
              allOf: [{${'$'}ref: '#/components/schemas/Base'}]
              properties: {name: {type: string}}
              unevaluatedProperties: false
            Pattern:
              type: object
              patternProperties: {'^x-': {type: string}}
              unevaluatedProperties: false
            Both: {type: object, additionalProperties: false, unevaluatedProperties: true}
            Open: {type: object, unevaluatedProperties: true, properties: {id: {type: string}}}
            ExplicitOpen:
              type: object
              properties: {id: {type: string}}
              additionalProperties: true
              unevaluatedProperties: false
            InheritedOpen:
              allOf: [{${'$'}ref: '#/components/schemas/ExplicitOpen'}]
              properties: {name: {type: string}}
              unevaluatedProperties: false
            InheritedUnevaluatedOpen:
              allOf: [{${'$'}ref: '#/components/schemas/Open'}]
              properties: {name: {type: string}}
              unevaluatedProperties: false
            TypedOpen:
              type: object
              properties: {id: {type: string}}
              additionalProperties: {type: string}
              unevaluatedProperties: false
        """.trimIndent(),
    )
    return GeneratedApiIrExporter().export(listOf(source.toUri())).models.associateBy { it.name }
  }
}
