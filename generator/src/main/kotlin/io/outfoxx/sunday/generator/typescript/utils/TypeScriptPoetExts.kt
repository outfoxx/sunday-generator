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

package io.outfoxx.sunday.generator.typescript.utils

import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.ANY
import io.outfoxx.typescriptpoet.TypeName.Companion.BIGINT
import io.outfoxx.typescriptpoet.TypeName.Companion.BIGINT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.NULL
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.SYMBOL
import io.outfoxx.typescriptpoet.TypeName.Companion.SYMBOL_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.UNDEFINED
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID

fun TypeName.box() =
  when (this) {
    BOOLEAN -> BOOLEAN_CLASS
    NUMBER -> NUMBER_CLASS
    BIGINT -> BIGINT_CLASS
    STRING -> STRING_CLASS
    SYMBOL -> SYMBOL_CLASS
    ANY, NULL, OBJECT, UNDEFINED, VOID, RECORD, UNKNOWN -> OBJECT_CLASS
    else -> this
  }

fun TypeName.typeInitializer(): CodeBlock {
  val builder = CodeBlock.builder().add("[")
  internalTypeInitializer(builder)
  builder.add("]")
  return builder.build()
}

fun TypeName.internalTypeInitializer(builder: CodeBlock.Builder) {
  when (this) {
    is TypeName.Parameterized -> {
      builder.add("%T, [", this.rawType)
      typeArgs.mapIndexed { idx, typeName ->
        typeName.internalTypeInitializer(builder)
        if (idx < typeArgs.size - 1) {
          builder.add(", ")
        }
      }
      builder.add("]")
    }

    is TypeName.Union ->
      if (isOptionalUnion) {
        nonOptional.internalTypeInitializer(builder)
      } else {
        builder.add("%T", OBJECT_CLASS) // proper unions are not currently supported
      }

    else -> builder.add("%T", this.box())
  }
}

val TypeName.isOptional: Boolean
  get() =
    when (this) {
      is TypeName.Union -> typeChoices.any { it == NULL || it == UNDEFINED }
      else -> false
    }

val TypeName.Union.isOptionalUnion: Boolean
  get() =
    when (typeChoices.size) {
      3 -> typeChoices.contains(NULL) && typeChoices.contains(UNDEFINED)
      2 -> typeChoices.any { it == NULL || it == UNDEFINED }
      else -> false
    }

val TypeName.nonOptional: TypeName
  get() =
    when {
      this is TypeName.Union && isOptionalUnion -> typeChoices.first { it != NULL && it != UNDEFINED }
      this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != NULL && it != UNDEFINED }.toTypedArray())
      else -> this
    }

val TypeName.undefinable: TypeName
  get() =
    when (this) {
      is TypeName.Union -> TypeName.unionType(*typeChoices.plus(UNDEFINED).toSet().toTypedArray())
      else -> TypeName.unionType(this, UNDEFINED)
    }

val TypeName.isUndefinable: Boolean
  get() =
    when (this) {
      is TypeName.Union -> typeChoices.contains(UNDEFINED)
      else -> false
    }

val TypeName.Union.isUndefinableUnion: Boolean
  get() = typeChoices.contains(UNDEFINED) && typeChoices.size == 2

val TypeName.nonUndefinable: TypeName
  get() =
    when {
      this is TypeName.Union && isUndefinableUnion -> typeChoices.first { it != UNDEFINED }
      this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != UNDEFINED }.toTypedArray())
      else -> this
    }

val TypeName.nullable: TypeName
  get() =
    when (this) {
      is TypeName.Union -> TypeName.unionType(*typeChoices.plus(NULL).toSet().toTypedArray())
      else -> TypeName.unionType(this, NULL)
    }

val TypeName.isNullable: Boolean
  get() =
    when (this) {
      is TypeName.Union -> typeChoices.contains(NULL)
      else -> false
    }

val TypeName.Union.isNullableUnion: Boolean
  get() = typeChoices.contains(NULL) && typeChoices.size == 2

val TypeName.nonNullable: TypeName
  get() =
    when {
      this is TypeName.Union && isNullableUnion -> typeChoices.first { it != NULL }
      this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != NULL }.toTypedArray())
      else -> this
    }
