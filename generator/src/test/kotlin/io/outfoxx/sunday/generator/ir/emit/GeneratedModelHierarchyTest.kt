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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeneratedModelHierarchyTest {
  @Test
  fun `mapped descendants retain unmapped sibling cases without duplicate dispatch`() {
    val root =
      GeneratedModel(
        "Pet",
        GeneratedModel.Kind.OBJECT,
        discriminatorMappings = mapOf("cat" to GeneratedTypeRef.named("WrappedCat")),
      )
    val cat = GeneratedModel("Cat", GeneratedModel.Kind.OBJECT, inherits = listOf(GeneratedTypeRef.named("Pet")))
    val wrapped =
      GeneratedModel("WrappedCat", GeneratedModel.Kind.OBJECT, inherits = listOf(GeneratedTypeRef.named("Cat")))
    val other = GeneratedModel("OtherCat", GeneratedModel.Kind.OBJECT, inherits = listOf(GeneratedTypeRef.named("Cat")))
    val models = listOf(root, cat, wrapped, other)
    val index =
      GeneratedApiIndex(
        GeneratedApi(
          name = "Variants",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          models = models,
        ),
      )

    fun children(model: GeneratedModel): List<GeneratedModel> =
      models.filter { child -> child.inherits.any { index.modelOrNull(it) == model } }
    assertEquals(setOf(cat, root), wrapped.ancestorModels(index))
    assertEquals(listOf(wrapped, other), root.discriminatorChildren(index, ::children))
    val mappedParent =
      root.copy(
        discriminatorMappings =
          root.discriminatorMappings + ("base" to GeneratedTypeRef.named("Cat")),
      )
    assertEquals(listOf(cat), mappedParent.discriminatorChildren(index) { children(root) })
  }
}
