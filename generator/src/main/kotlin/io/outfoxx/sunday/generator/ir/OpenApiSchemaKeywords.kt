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

package io.outfoxx.sunday.generator.ir

/** Schema locations are shared by traversal and comparison; unrecognized values remain literal data. */
internal object OpenApiSchemaKeywords {
  enum class Shape { VALUE, MAP, LIST }

  val maps = setOf("properties", "patternProperties", "\$defs", "definitions", "dependentSchemas")
  val lists = setOf("allOf", "oneOf", "anyOf", "prefixItems")
  val values =
    setOf(
      "items",
      "additionalProperties",
      "not",
      "if",
      "then",
      "else",
      "contains",
      "propertyNames",
      "unevaluatedItems",
      "unevaluatedProperties",
    )
  val documentaryAnnotations =
    setOf("description", "summary", "title", "example", "examples", "externalDocs", "\$comment")

  // These annotations belong to declarations, independently of documentary contract compatibility.
  val declarationAnnotations =
    setOf("discriminator", "description", "summary", "title", "example", "examples", "deprecated")
  val resourceKeywords = setOf("\$id", "\$anchor", "\$dynamicAnchor")
  val lowerBounds = setOf("minimum", "exclusiveMinimum", "minLength", "minItems", "minProperties", "minContains")
  val upperBounds = setOf("maximum", "exclusiveMaximum", "maxLength", "maxItems", "maxProperties", "maxContains")
  val numericAssertions = lowerBounds + upperBounds + "multipleOf"
  private val assertions =
    setOf(
      "type",
      "enum",
      "const",
      "required",
      "properties",
      "patternProperties",
      "additionalProperties",
      "items",
      "prefixItems",
      "allOf",
      "oneOf",
      "anyOf",
      "not",
      "if",
      "then",
      "else",
      "contains",
      "propertyNames",
      "dependentSchemas",
      "dependentRequired",
      "unevaluatedItems",
      "unevaluatedProperties",
      "multipleOf",
      "uniqueItems",
      "pattern",
      "format",
      "nullable",
    ) + lowerBounds + upperBounds

  fun shape(keyword: String): Shape? =
    when (keyword) {
      in maps -> Shape.MAP
      in lists -> Shape.LIST
      in values -> Shape.VALUE
      else -> null
    }

  fun isAssertion(keyword: String): Boolean = keyword in assertions
}
