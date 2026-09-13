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

import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.Shape

/** Recognized object children used identically when indexing, discovering, and normalizing references. */
internal object OpenApiReferenceLocations {
  enum class Kind(
    val label: String,
  ) {
    DOCUMENT("document"),
    SCHEMA("schema"),
    PARAMETER("parameter"),
    REQUEST_BODY("request body"),
    RESPONSE("response"),
    HEADER("header"),
    SECURITY_SCHEME("security scheme"),
    EXAMPLE("example"),
    PATH_ITEM("path item"),
    OPERATION("operation"),
    MEDIA("media type"),
  }

  data class Child(
    val kind: Kind,
    val shape: Shape,
  )

  val componentKinds =
    mapOf(
      "schemas" to Kind.SCHEMA,
      "parameters" to Kind.PARAMETER,
      "requestBodies" to Kind.REQUEST_BODY,
      "responses" to Kind.RESPONSE,
      "headers" to Kind.HEADER,
      "securitySchemes" to Kind.SECURITY_SCHEME,
      "examples" to Kind.EXAMPLE,
      "pathItems" to Kind.PATH_ITEM,
    )
  private val httpMethods = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

  fun child(
    kind: Kind,
    name: String,
  ): Child? =
    when {
      kind == Kind.SCHEMA -> OpenApiSchemaKeywords.shape(name)?.let { Child(Kind.SCHEMA, it) }
      kind == Kind.PATH_ITEM && name in httpMethods -> Child(Kind.OPERATION, Shape.VALUE)
      kind in setOf(Kind.PATH_ITEM, Kind.OPERATION) && name == "parameters" -> Child(Kind.PARAMETER, Shape.LIST)
      kind == Kind.OPERATION && name == "requestBody" -> Child(Kind.REQUEST_BODY, Shape.VALUE)
      kind == Kind.OPERATION && name == "responses" -> Child(Kind.RESPONSE, Shape.MAP)
      kind in setOf(Kind.PARAMETER, Kind.HEADER, Kind.MEDIA) && name == "schema" -> Child(Kind.SCHEMA, Shape.VALUE)
      kind in setOf(Kind.PARAMETER, Kind.HEADER, Kind.REQUEST_BODY, Kind.RESPONSE) && name == "content" ->
        Child(Kind.MEDIA, Shape.MAP)
      kind == Kind.RESPONSE && name == "headers" -> Child(Kind.HEADER, Shape.MAP)
      kind in setOf(Kind.PARAMETER, Kind.HEADER, Kind.MEDIA) && name == "examples" -> Child(Kind.EXAMPLE, Shape.MAP)
      else -> null
    }
}
