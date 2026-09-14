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

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.ir.GeneratedModel
import java.math.BigDecimal
import java.net.URI

/** Renders scalar defaults using the inherited declaration type, including canonical enum values. */
internal object KotlinModelDefaults {
  fun code(
    value: String?,
    typeName: TypeName,
    enumModel: GeneratedModel?,
    enumEntries: KotlinEnumEntriesResolver,
  ): CodeBlock? {
    if (value == null) return null
    val type = typeName.copy(nullable = false)
    if (enumModel?.kind == GeneratedModel.Kind.ENUM) {
      return if (enumModel.unknownValue != null) {
        CodeBlock.of("%T.fromValue(%S)", type, value)
      } else {
        CodeBlock.of("%T.%N", type, enumEntries.requireConstantNameForValue(enumModel, value, "default"))
      }
    }
    return when (type) {
      STRING -> CodeBlock.of("%S", value)
      BOOLEAN -> value.toBooleanStrictOrNull()?.let { CodeBlock.of("%L", it) }
      BYTE, SHORT, INT -> value.toIntOrNull()?.let { CodeBlock.of("%L", it) }
      LONG ->
        value.toLongOrNull()?.let {
          if (it == Long.MIN_VALUE) CodeBlock.of("%T.MIN_VALUE", LONG) else CodeBlock.of("%LL", it)
        }
      FLOAT -> value.toFloatOrNull()?.let { CodeBlock.of("%Lf", it) }
      DOUBLE -> value.toDoubleOrNull()?.let { CodeBlock.of("%L", it) }
      BigDecimal::class.asTypeName() -> CodeBlock.of("%T(%S)", type, value)
      URI::class.asTypeName() -> CodeBlock.of("%T.create(%S)", type, value)
      else -> null
    }
  }
}
