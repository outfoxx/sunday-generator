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

/** Supplies captured source documents without interpreting schema identifiers or fragments. */
fun interface OpenApiDocumentLoader {
  /** Loads a fragment-free URI, retaining the effective retrieval URI after redirects. */
  fun load(uri: URI): OpenApiLoadedDocument

  /** Factories for document loading sessions. */
  companion object {
    /** Creates a session that reads files and revalidates each remote URI at most once. */
    fun create(options: OpenApiReferenceOptions = OpenApiReferenceOptions()): OpenApiDocumentLoader =
      DefaultOpenApiDocumentLoader(options)
  }
}

internal fun URI.openApiDocumentUri(): URI = OpenApiUriResolver.resolve(URI(""), toString().substringBefore('#'))
