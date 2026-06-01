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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.util.IdentityHashMap

internal data class KotlinEnumEntry(
  val name: String,
  val value: String,
)

internal class KotlinEnumEntriesResolver {

  private val entriesByModel = IdentityHashMap<GeneratedModel, List<KotlinEnumEntry>>()

  fun entries(model: GeneratedModel): List<KotlinEnumEntry> =
    entriesByModel.getOrPut(model) {
      model.kotlinEnumEntries()
    }

  fun constantNameForValue(
    model: GeneratedModel,
    value: String,
  ): String? = entries(model).singleOrNull { entry -> entry.value == value }?.name
}

internal fun GeneratedModel.kotlinEnumEntries(): List<KotlinEnumEntry> {
  if (enumValueNames.isNotEmpty() && enumValueNames.size != values.size) {
    genError(
      "Kotlin enum '$name' has ${enumValueNames.size} enum value names for ${values.size} enum values. " +
        "Fix x-enum-varnames so it has one entry per enum value.",
    )
  }

  val entries =
    values.mapIndexed { index, value ->
      val constantName =
        if (enumValueNames.isNotEmpty()) {
          enumValueNames[index].kotlinEnumConstantName()
        } else {
          value.kotlinEnumConstantName()
        }
      validateKotlinEnumConstantName(
        constantName,
        value,
        enumValueNames.getOrNull(index),
      )
      KotlinEnumEntry(constantName, value)
    }

  entries
    .groupBy { entry -> entry.name }
    .filterValues { duplicates -> duplicates.size > 1 }
    .forEach { (constantName, duplicates) ->
      genError(
        "Kotlin enum '$name' constant name '$constantName' is used for multiple values " +
          duplicates.joinToString(", ") { entry -> "'${entry.value}'" } +
          ". Add x-enum-varnames to disambiguate them.",
      )
    }

  return entries
}

internal fun String.kotlinEnumConstantName(): String =
  split(enumSplitRegex)
    .joinToString("") { part -> part.replaceFirstChar { it.titlecase() } }
    .toUpperCamelCase()

private fun GeneratedModel.validateKotlinEnumConstantName(
  constantName: String,
  value: String,
  explicitName: String?,
) {
  if (!kotlinEnumConstantIdentifierRegex.matches(constantName)) {
    if (explicitName != null) {
      genError(
        "Kotlin enum '$name' x-enum-varnames entry '$explicitName' for value '$value' " +
          "maps to invalid constant name '$constantName'. Fix x-enum-varnames with a valid " +
          "Kotlin enum constant name.",
      )
    }
    genError(
      "Kotlin enum '$name' value '$value' maps to invalid constant name '$constantName'. " +
        "Add x-enum-varnames with a valid Kotlin enum constant name.",
    )
  }
}

private val enumSplitRegex = """\W""".toRegex()
private val kotlinEnumConstantIdentifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")
