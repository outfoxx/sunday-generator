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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Retains wire shape and model identity independently of native constraint metadata. */
internal class KotlinNativeShape(
  private val properties: GeneratedModelProperties,
  private val types: BeanValidationTypes,
  private val typeName: (GeneratedTypeRef) -> TypeName,
) {
  private val kindType = types.dynamicProperties.nestedClass("Kind")

  private fun kind(reference: GeneratedTypeRef): CodeBlock {
    val declaration = properties.declarationType(reference)
    val kind =
      when (declaration.kind) {
        GeneratedTypeRef.Kind.ARRAY -> "ARRAY"
        GeneratedTypeRef.Kind.MAP -> "OBJECT"
        GeneratedTypeRef.Kind.NAMED ->
          when (properties.declarationModel(declaration)?.kind) {
            GeneratedModel.Kind.ENUM -> "STRING"
            GeneratedModel.Kind.OBJECT -> "OBJECT"
            GeneratedModel.Kind.UNION ->
              if (properties.declarationModel(declaration)?.let(properties::hasUnionCommonRules) ==
                true
              ) {
                "OBJECT"
              } else {
                "ANY"
              }
            else -> "ANY"
          }
        GeneratedTypeRef.Kind.SCALAR ->
          when (declaration.name) {
            "string", "date", "time", "datetime", "datetime-only", "file" -> "STRING"
            "integer", "int32", "int64", "long" -> "INTEGER"
            "number", "float", "double" -> "NUMBER"
            "boolean" -> "BOOLEAN"
            "object" -> "OBJECT"
            else -> "ANY"
          }
        else -> "ANY"
      }
    return CodeBlock.of("%T.%L", kindType, kind)
  }

  fun annotation(reference: GeneratedTypeRef): CodeBlock {
    val values = mutableListOf<CodeBlock>()
    var current = reference
    while (true) {
      val declaration = properties.declarationType(current)
      val model = properties.declarationModel(current)
      val common = KotlinNativeSchema.commonSchema(current, properties, typeName)
      val identity =
        model?.takeIf {
          it.kind in setOf(GeneratedModel.Kind.ENUM, GeneratedModel.Kind.OBJECT) ||
            it.nominal
        }
      values +=
        CodeBlock.of(
          "%T(%L, nullable = %L%L)",
          types.dynamicProperties.nestedClass("Shape"),
          kind(current),
          properties.acceptsNull(current),
          common?.let { CodeBlock.of(", model = %T::class", it) }
            ?: identity?.let { CodeBlock.of(", model = %T::class", typeName(current).copy(nullable = false)) }
            ?: CodeBlock.of(""),
        )
      if (declaration.kind !in setOf(GeneratedTypeRef.Kind.ARRAY, GeneratedTypeRef.Kind.MAP)) break
      current = declaration.arguments.lastOrNull() ?: break
    }
    return values.joinToCode(", ", "[", "]")
  }
}
