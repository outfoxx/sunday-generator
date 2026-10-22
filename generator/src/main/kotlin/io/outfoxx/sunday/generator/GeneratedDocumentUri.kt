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

package io.outfoxx.sunday.generator

import io.outfoxx.sunday.generator.ir.OpenApiUriResolver
import java.net.URI
import java.nio.file.Path

/** Canonical document identities shared by source resolution and output selection. */
object GeneratedDocumentUri {

  /** Canonicalizes an absolute URI without using the process working directory. */
  fun canonical(uri: URI): URI {
    require(uri.isAbsolute) { "Document URI must be absolute: $uri" }
    val document = URI(uri.toString().substringBefore('#'))
    return if (document.scheme.equals("file", ignoreCase = true)) {
      Path
        .of(document)
        .toFile()
        .canonicalFile
        .toPath()
        .toUri()
    } else {
      OpenApiUriResolver.resolve(URI(""), document.toString())
    }
  }

  /** Resolves a configuration identifier; identifiers without a scheme name local files. */
  fun configuration(
    identifier: String,
    baseDirectory: Path,
  ): URI {
    val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(identifier)
    return if (hasScheme) {
      canonical(URI(identifier))
    } else {
      canonical(baseDirectory.resolve(identifier).toAbsolutePath().toUri())
    }
  }
}
