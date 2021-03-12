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

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.U_BYTE_ARRAY
import com.squareup.kotlinpoet.U_INT_ARRAY
import com.squareup.kotlinpoet.U_LONG_ARRAY
import com.squareup.kotlinpoet.U_SHORT_ARRAY
import com.squareup.kotlinpoet.asTypeName

/**
 * Extension methods for KotlinPoet classes/types
 */

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
      BOOLEAN_ARRAY, BYTE_ARRAY, CHAR_ARRAY, SHORT_ARRAY, INT_ARRAY, LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, U_BYTE_ARRAY, U_SHORT_ARRAY, U_INT_ARRAY, U_LONG_ARRAY -> true
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
