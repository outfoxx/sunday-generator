/*
 * Copyright 2020 Outfox, Inc.
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

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

/**
 * Extension methods for KotlinPoet classes/types
 */


fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addAnnotation(
  annotation: ClassName,
  listValue: List<String>,
) {
  addAnnotation(
    AnnotationSpec.builder(annotation)
      .addMember("value = [${listValue.joinToString(", ") { "%S" }}]", *listValue.toTypedArray())
      .build(),
  )
}

fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addAnnotation(
  annotation: ClassName,
  value: String,
) {
  addAnnotation(
    AnnotationSpec.builder(annotation)
      .addMember("value = %S", value)
      .build(),
  )
}

fun TypeName.immutable(): TypeName {
  return when (this) {
    MutableList::class.asTypeName() -> List::class.asTypeName()
    MutableSet::class.asTypeName() -> Set::class.asTypeName()
    MutableMap::class.asTypeName() -> Map::class.asTypeName()
    MutableCollection::class.asTypeName() -> Collection::class.asTypeName()
    is ParameterizedTypeName ->
      (this.rawType.immutable() as ClassName).parameterizedBy(*this.typeArguments.toTypedArray())
    else -> this
  }
}

val TypeName.isImmutableCollection: Boolean
  get() = when (this) {
    List::class.asTypeName() -> true
    Set::class.asTypeName() -> true
    Map::class.asTypeName() -> true
    Collection::class.asTypeName() -> true
    is ParameterizedTypeName -> this.rawType.isImmutableCollection
    else -> false
  }

val TypeName.isMutableCollection: Boolean
  get() = when (this) {
    MutableList::class.asTypeName() -> true
    MutableSet::class.asTypeName() -> true
    MutableMap::class.asTypeName() -> true
    MutableCollection::class.asTypeName() -> true
    is ParameterizedTypeName -> this.rawType.isMutableCollection
    else -> false
  }

val TypeName.isMapLike: Boolean
  get() = when (this.rawType) {
    Map::class.asTypeName(),
    MutableMap::class.asTypeName(),
    -> true
    else -> false
  }

val TypeName.isCollectionLike: Boolean
  get() {
    val rawType = this.rawType
    return (rawType.isImmutableCollection || rawType.isMutableCollection) && !rawType.isMapLike
  }

fun TypeName.withTypeArgument(index: Int, typeName: TypeName): TypeName {
  if (this !is ParameterizedTypeName || index !in this.typeArguments.indices) {
    return this
  }
  val newArguments =
    this.typeArguments.mapIndexed { argIndex, argument ->
      if (argIndex == index) typeName else argument
    }
  return (this.rawType as ClassName)
    .parameterizedBy(*newArguments.toTypedArray())
    .copy(nullable = this.isNullable, annotations = this.annotations)
}

fun TypeName.withAnnotatedTypeArgument(index: Int, annotation: AnnotationSpec): TypeName {
  if (this !is ParameterizedTypeName || index !in this.typeArguments.indices) {
    return this
  }
  val newArguments =
    this.typeArguments.mapIndexed { argIndex, argument ->
      if (argIndex == index) {
        argument.copy(annotations = argument.annotations + annotation)
      } else {
        argument
      }
    }
  return (this.rawType as ClassName)
    .parameterizedBy(*newArguments.toTypedArray())
    .copy(nullable = this.isNullable, annotations = this.annotations)
}

fun TypeName.mutable(): TypeName {
  return when (this) {
    List::class.asTypeName() -> MutableList::class.asTypeName()
    Set::class.asTypeName() -> MutableSet::class.asTypeName()
    Map::class.asTypeName() -> MutableMap::class.asTypeName()
    Collection::class.asTypeName() -> MutableCollection::class.asTypeName()
    is ParameterizedTypeName ->
      (this.rawType.mutable() as ClassName).parameterizedBy(*this.typeArguments.toTypedArray())
    else -> this
  }
}

val TypeName.isArray: Boolean
  get() =
    when (this.copy(nullable = false)) {
      BOOLEAN_ARRAY,
      BYTE_ARRAY, CHAR_ARRAY, SHORT_ARRAY, INT_ARRAY, LONG_ARRAY,
      U_BYTE_ARRAY, U_SHORT_ARRAY, U_INT_ARRAY, U_LONG_ARRAY,
      FLOAT_ARRAY, DOUBLE_ARRAY,
      -> true
      else -> false
    }

fun TypeName.array(): TypeName {
  return ARRAY.parameterizedBy(this)
}

val TypeName.rawType: TypeName
  get() = when (this) {
    is ParameterizedTypeName -> this.rawType
    else -> this
  }
