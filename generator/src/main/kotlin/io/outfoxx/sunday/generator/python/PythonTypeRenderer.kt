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

import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

internal fun GeneratedTypeRef.renderPythonType(nullable: Boolean = true): PythonCodeBlock {
  val type = renderNonNullablePythonType()
  return if (nullable && this.nullable) {
    PythonCodeBlock.of("%C | None", type)
  } else {
    type
  }
}

private fun GeneratedTypeRef.renderNonNullablePythonType(): PythonCodeBlock =
  when (kind) {
    GeneratedTypeRef.Kind.SCALAR -> renderScalarPythonType()
    GeneratedTypeRef.Kind.NAMED -> PythonCodeBlock.of("%L", name.pythonTypeName)
    GeneratedTypeRef.Kind.ARRAY ->
      PythonCodeBlock.of(
        "list[%C]",
        arguments.firstOrNull()?.renderPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
      )
    GeneratedTypeRef.Kind.MAP ->
      PythonCodeBlock.of(
        "dict[str, %C]",
        arguments.firstOrNull()?.renderPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
      )
    GeneratedTypeRef.Kind.UNION ->
      if (arguments.isEmpty()) {
        PythonCodeBlock.of("object")
      } else {
        PythonCodeBlock.join(arguments.map { it.renderPythonType(nullable = false) }, separator = " | ")
      }
  }

private fun GeneratedTypeRef.renderScalarPythonType(): PythonCodeBlock {
  val formattedType = format?.lowercase()?.ifBlank { null } ?: name.lowercase()
  return when (formattedType) {
    "date" -> PythonCodeBlock.of("%T", PythonSymbol("datetime", "date"))
    "date-time",
    "datetime",
    "date-time-only",
    "datetime-only",
    -> PythonCodeBlock.of("%T", PythonSymbol("datetime", "datetime"))
    "time", "time-only" -> PythonCodeBlock.of("%T", PythonSymbol("datetime", "time"))
    "uri", "url" -> PythonCodeBlock.of("%T", PythonSymbol("pydantic", "AnyUrl"))
    "uuid" -> PythonCodeBlock.of("%T", PythonSymbol("uuid", "UUID"))
    "binary", "byte" -> PythonCodeBlock.of("bytes")
    else ->
      when (name) {
        "boolean" -> PythonCodeBlock.of("bool")
        "integer" -> PythonCodeBlock.of("int")
        "number" -> PythonCodeBlock.of("float")
        "nil" -> PythonCodeBlock.of("None")
        "string" -> PythonCodeBlock.of("str")
        "file" -> PythonCodeBlock.of("bytes")
        else -> PythonCodeBlock.of("object")
      }
  }
}

internal fun String.renderPythonLiteralType(): PythonCodeBlock =
  PythonCodeBlock.of("%T[%S]", PythonSymbol("typing", "Literal"), this)
