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

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

private val enumSplitRegex = """[^A-Za-z0-9]+""".toRegex()

val String.swiftEnumCaseName: String
  get() =
    split(enumSplitRegex)
      .filter { segment -> segment.isNotBlank() }
      .joinToString("") { segment -> segment.normalizedEnumSegment().replaceFirstChar { it.titlecase() } }
      .replaceFirstChar { it.lowercase() }

private fun String.normalizedEnumSegment(): String =
  if (any { it.isLetter() } && all { !it.isLetter() || it.isUpperCase() }) {
    lowercase()
  } else {
    this
  }

val String.swiftTypeName: String get() = toUpperCamelCase()
val String.swiftIdentifierName: String get() = toLowerCamelCase()
