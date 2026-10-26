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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/** Adds one value policy after model storage, defaults, and fallback bodies have been finalized. */
internal fun addModelValueEquality(
  models: Map<ClassName, TypeSpec.Builder>,
  problems: Set<ClassName>,
  reservedTypes: Collection<ClassName>,
  inheritedSchemaProperties: (ClassName) -> List<PropertySpec>,
) {
  fun isProblem(name: ClassName): Boolean =
    name in problems || (models[name]?.build()?.superclass as? ClassName)?.let(::isProblem) == true

  fun properties(name: ClassName): List<PropertySpec> {
    val type = models[name]?.build() ?: return emptyList()
    val inherited = (type.superclass as? ClassName)?.let(::properties).orEmpty()
    return (
      inherited +
        // Custom-mapped parents have schema fields but no emitted builder to traverse.
        inheritedSchemaProperties(name) +
        type.propertySpecs
          .filterNot {
            KModifier.PRIVATE in it.modifiers || KModifier.PROTECTED in it.modifiers
          }
    ).distinctBy { it.name }
  }

  models.forEach { (name, builder) ->
    if (builder.build().kind != TypeSpec.Kind.CLASS || isProblem(name)) return@forEach
    val modelProperties = properties(name)
    val fields = modelProperties.map { it.name }
    val patchValues = modelProperties.any { (it.type as? ParameterizedTypeName)?.rawType in setOf(PATCH_OP, UPDATE_OP) }
    val names = NameAllocator()
    (
      fields + name.simpleNames + builder.typeSpecs.mapNotNull { it.name } +
        reservedTypes
          .filter {
            it.enclosingClassName() == name
          }.map { it.simpleName }
    ).distinct().forEach { names.newName(it) }
    val helper = names.newName("ValueEquality")
    builder.addFunction(
      FunSpec
        .builder("equals")
        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
        .addKdoc("Compares retained values of the same runtime type. Do not mutate values used as hash keys.\n")
        .addParameter("other", ANY.copy(nullable = true))
        .returns(BOOLEAN)
        // KotlinPoet's non-breaking spaces keep return values attached when long guards wrap.
        .addStatement("if (this === other) return·true")
        .addStatement("if (other !is %T || this::class != other::class) return·false", name)
        .apply {
          fields.forEach { field ->
            addStatement("if (!%N.same(this.%N, other.%N)) return·false", helper, field, field)
          }
        }.addStatement("return true")
        .build(),
    )
    builder.addFunction(
      FunSpec
        .builder("hashCode")
        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
        .addKdoc("Hashes the runtime type and retained values; mutation can change this hash.\n")
        .returns(INT)
        .apply {
          if (fields.isEmpty()) {
            addStatement("return this::class.hashCode()")
          } else {
            addStatement("var result = this::class.hashCode()")
            fields.forEach { field -> addStatement("result = 31 * result + %N.hash(this.%N)", helper, field) }
            addStatement("return result")
          }
        }.build(),
    )
    if (fields.isNotEmpty()) builder.addType(valueEqualityHelper(helper, patchValues))
  }
}

private fun valueEqualityHelper(
  name: String,
  patchValues: Boolean,
): TypeSpec =
  TypeSpec
    .objectBuilder(name)
    .addModifiers(KModifier.PRIVATE)
    .addFunction(
      FunSpec
        .builder("same")
        .addParameter("left", ANY.copy(nullable = true))
        .addParameter("right", ANY.copy(nullable = true))
        .returns(BOOLEAN)
        .apply {
          if (patchValues) {
            addStatement(
              "if (left is %1T<*> && right is %1T<*>) return·same(left.value, right.value)",
              PATCH_SET_OP,
            )
          }
        }.addCode(
          """
          if (left === right) return·true
          return when {
            left is %T<*> && right is %T<*> ->
              left.size == right.size && left.indices.all { same(left[it], right[it]) }
            left is %T<*, *> && right is %T<*, *> ->
              left.size == right.size && left.all { (key, value) -> right.containsKey(key) && same(value, right[key]) }
            left is %T<*> && right is %T<*> ->
              left.size == right.size && left.indices.all { same(left[it], right[it]) }
            else -> %T.deepEquals(left, right)
          }
          """.trimIndent(),
          List::class,
          List::class,
          Map::class,
          Map::class,
          Array<Any>::class,
          Array<Any>::class,
          java.util.Objects::class,
        ).build(),
    ).addFunction(
      FunSpec
        .builder("hash")
        .addParameter("value", ANY.copy(nullable = true))
        .returns(INT)
        .apply {
          if (patchValues) addStatement("if (value is %T<*>) return·hash(value.value)", PATCH_SET_OP)
        }.addCode(
          """
          return when (value) {
            is %T<*> -> value.fold(1) { result, item -> 31 * result + hash(item) }
            is %T<*, *> -> value.entries.sumOf { (key, item) ->
              (key?.hashCode() ?: 0).xor(hash(item))
            }
            is %T<*> -> value.fold(1) { result, item -> 31 * result + hash(item) }
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            is %T -> value.contentHashCode()
            else -> value?.hashCode() ?: 0
          }
          """.trimIndent(),
          List::class,
          Map::class,
          Array<Any>::class,
          ByteArray::class,
          ShortArray::class,
          IntArray::class,
          LongArray::class,
          FloatArray::class,
          DoubleArray::class,
          CharArray::class,
          BooleanArray::class,
        ).build(),
    ).build()
