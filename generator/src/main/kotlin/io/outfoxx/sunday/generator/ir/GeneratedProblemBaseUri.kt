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

import com.damnhandy.uri.template.UriTemplate
import java.net.URI
import java.net.URISyntaxException

/** Expands the same problem-base templates for RAML and OpenAPI, relative to their API or library fallback. */
internal fun resolveProblemBaseUri(
  template: String?,
  params: Map<String, String>,
  fallback: URI,
): URI =
  if (template == null) {
    fallback
  } else {
    try {
      fallback.resolve(URI(UriTemplate.expand(template, params)))
    } catch (error: URISyntaxException) {
      throw IllegalArgumentException(
        "Problem URI is not a valid URI; use problemBaseUri and problemUriParams to provide a valid URI",
        error,
      )
    }
  }
