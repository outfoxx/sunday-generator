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

import io.outfoxx.sunday.generator.tools.patternModelsApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OpenApiPatternPropertiesTest {
  @Test
  fun `retains pattern assertions and closed semantics in composed IR`(
    @TempDir directory: Path,
  ) {
    val api = patternModelsApi(directory, composed = true)
    val model = api.models.single { it.name == "PatternRecord" }
    assertEquals(false, model.additionalProperties?.allowed)
    val patterns = model.patternProperties.associateBy { it.pattern }
    assertEquals(mapOf("minLength" to "2"), patterns.getValue("^x-").validation)
    assertEquals(listOf("yes-value", "no-value"), patterns.getValue("^enum-").allowedValues)
    assertEquals(listOf(3), patterns.getValue("^const-").allowedValues)
    assertEquals(GeneratedTypeRef.named("PatternChild"), patterns.getValue("^r-").type)
    val inline = api.models.single { it.name == patterns.getValue("^inline-").type.name }
    assertEquals(false, inline.additionalProperties?.allowed)
    assertNotNull(inline.properties.single { it.name == "label" })
    assertEquals(GeneratedModel.Kind.OBJECT, api.models.single { it.name == "PatternOnly" }.kind)
    val nestedTypes =
      model.properties
        .filter {
          it.serializationName in setOf("nested-array", "nested-map")
        }.map { it.type.arguments.single() } +
        listOf("PatternArray", "PatternMap").map { name ->
          api.models
            .single { it.name == name }
            .aliases
            .single()
        } +
        listOf(
          api.services
            .flatMap { it.operations }
            .single { it.id == "submitAnonymous" }
            .requestBody!!
            .type.arguments
            .single(),
          api.models
            .single { it.name == "NestedAdditionalPattern" }
            .additionalProperties!!
            .type!!,
        )
    assertEquals(6, nestedTypes.size)
    nestedTypes.forEach { type ->
      assertEquals(GeneratedTypeRef.Kind.NAMED, type.kind)
      val nested = api.models.single { it.name == type.name }
      assertEquals(false, nested.additionalProperties?.allowed)
      assertEquals("^x-", nested.patternProperties.single().pattern)
      assertEquals(mapOf("minLength" to "2"), nested.patternProperties.single().validation)
    }
  }
}
