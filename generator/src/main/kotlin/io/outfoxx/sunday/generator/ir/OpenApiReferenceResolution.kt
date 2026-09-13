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

import java.net.URI

/** A normalized OpenAPI document together with the exact documents used to resolve it. */
data class OpenApiReferenceResolution(
  /** OpenAPI objects with resolved references and imported, named schema declarations. */
  val document: Map<String, Any?>,
  /** Retrieval URIs, including redirect aliases, mapped to captured source content. */
  val documents: Map<URI, OpenApiLoadedDocument>,
) {
  internal val schemas: Map<String, Map<*, *>> =
    ((document["components"] as? Map<*, *>)?.get("schemas") as? Map<*, *>)
      .orEmpty()
      .entries
      .mapNotNull { (name, schema) -> (name as? String)?.let { it to (schema as? Map<*, *>).orEmpty() } }
      .toMap()

  // Kept out of the public result fields and serialized snapshots; the converter reuses discovery's completed work.
  internal val analysis: OpenApiSchemaAnalysis by lazy { OpenApiSchemaAnalysis(schemas) }
}
