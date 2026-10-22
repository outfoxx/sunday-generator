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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GeneratedDocumentUri
import java.net.URI

/**
 * Source specification metadata for a generated IR document.
 */
data class GeneratedSourceSpec(
  val kind: Kind,
  val location: String,
) {

  /** Document identity is independent of the frontend that read a shared schema. */
  internal fun documentIdentity(): String {
    // Library-created IR can use descriptive source names; those retain their exact identity.
    val uri = runCatching { URI(location) }.getOrNull()?.takeIf { it.isAbsolute } ?: return location
    return GeneratedDocumentUri.canonical(uri).toString()
  }

  /** Supported source specification formats. */
  enum class Kind {
    RAML,
    OPENAPI,
    ASYNCAPI,
  }
}
