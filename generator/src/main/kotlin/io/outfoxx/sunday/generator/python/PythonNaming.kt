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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

private val nonIdentifierChars = "[^A-Za-z0-9]+".toRegex()
private val camelBoundary = "(?<=[a-z0-9])(?=[A-Z])".toRegex()
private val pythonKeywords =
  setOf(
    "False",
    "None",
    "True",
    "and",
    "as",
    "assert",
    "async",
    "await",
    "break",
    "class",
    "continue",
    "def",
    "del",
    "elif",
    "else",
    "except",
    "finally",
    "for",
    "from",
    "global",
    "if",
    "import",
    "in",
    "is",
    "lambda",
    "nonlocal",
    "not",
    "or",
    "pass",
    "raise",
    "return",
    "try",
    "while",
    "with",
    "yield",
  )

/** Converts a source name to a Python class/type name. */
val String.pythonTypeName: String
  get() = toUpperCamelCase()

/** Converts a source name to a Python value identifier. */
val String.pythonIdentifierName: String
  get() =
    replace(camelBoundary, "_")
      .replace(nonIdentifierChars, "_")
      .trim('_')
      .lowercase()
      .ifBlank { "value" }
      .let { if (it.first().isDigit()) "_$it" else it }
      .let { if (it in pythonKeywords) "${it}_" else it }

/** Converts an enum wire value to a Python enum member name. */
val String.pythonEnumMemberName: String
  get() =
    replace(camelBoundary, "_")
      .replace(nonIdentifierChars, "_")
      .trim('_')
      .uppercase()
      .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }

internal val GeneratedService.pythonServiceBaseName: String
  get() = name.removeSuffix("Service").removeSuffix("API")

internal val GeneratedService.pythonServiceIdentifierName: String
  get() = pythonServiceBaseName.pythonIdentifierName

internal val GeneratedService.pythonServiceModuleName: String
  get() = pythonServiceIdentifierName

internal val GeneratedService.pythonServiceServerModuleName: String
  get() = "${pythonServiceBaseName}_server".pythonIdentifierName

internal val GeneratedService.pythonServiceRouterFactoryName: String
  get() = "create_${pythonServiceBaseName}_router".pythonIdentifierName
