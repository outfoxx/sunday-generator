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

import java.nio.file.Path
import java.time.Duration

/** Controls retrieval of native OpenAPI documents and their external references. */
data class OpenApiReferenceOptions(
  /** Persistent HTTP document cache; each online resolution session revalidates its entries. */
  val cacheDirectory: Path = Path.of(System.getProperty("user.home"), ".cache", "sunday-generator", "openapi"),
  /** Allows remote documents only when already cached, without making HTTP requests. */
  val offline: Boolean = false,
  /** Maximum time to establish an HTTP connection. */
  val connectionTimeout: Duration = Duration.ofSeconds(10),
  /** Maximum wait for DNS preflight or an entire HTTP response. */
  val requestTimeout: Duration = Duration.ofSeconds(30),
  /** Maximum document size in bytes. */
  val maximumDocumentBytes: Int = 16 * 1024 * 1024,
  /** Maximum number of HTTP redirects for one retrieval. */
  val maximumRedirects: Int = 5,
  /** Allows private network destinations and configured proxies for trusted specifications. */
  val allowPrivateNetwork: Boolean = false,
) {
  init {
    require(!connectionTimeout.isNegative && !connectionTimeout.isZero)
    require(!requestTimeout.isNegative && !requestTimeout.isZero)
    require(maximumDocumentBytes > 0 && maximumDocumentBytes < Int.MAX_VALUE)
    require(maximumRedirects >= 0)
  }
}
