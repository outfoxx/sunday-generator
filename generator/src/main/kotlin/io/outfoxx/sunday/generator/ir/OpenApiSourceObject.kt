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

import io.outfoxx.sunday.generator.GeneratedDocumentUri
import java.net.URI

/** Original location and lexical placement for a normalized protocol object or schema. */
internal open class OpenApiSourceObject(
  fields: Map<String, Any?>,
  private val source: OpenApiSchema.Source,
  private val pointer: String,
) : LinkedHashMap<String, Any?>(fields) {
  val declarationUri: String get() =
    sourceDeclarationUri(
      GeneratedDocumentUri.canonical(URI(source.uri)).toString(),
      pointer,
    )
  val placementScopes: List<GeneratedPlacementScope> get() = source.placementScopes(pointer)
}
