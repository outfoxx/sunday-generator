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

package io.outfoxx.sunday.generator.utils

private val enumWordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
private val enumWordSeparator = Regex("[^A-Za-z0-9]+")

/**
 * Finds enum words across separator, camel, and acronym boundaries before applying target casing.
 * Digits stay with their preceding word; an undelimited uppercase token is one word because its
 * original word boundaries cannot be recovered (for example, `HTTP` becomes `http`).
 */
internal fun String.enumNameWords(): List<String> =
  replace(enumWordBoundary, "_")
    .split(enumWordSeparator)
    .filter { it.isNotEmpty() }
    .map { it.lowercase() }

/** Converts an enum wire value to UpperCamelCase without changing general identifier naming. */
internal fun String.enumUpperCamelCase(): String =
  enumNameWords().joinToString("") { it.replaceFirstChar(Char::titlecase) }
