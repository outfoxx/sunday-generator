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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions

/** Keeps export and language commands aligned on OpenAPI retrieval flags and defaults. */
internal class OpenApiReferenceOptionGroup : OptionGroup() {
  val cacheDirectory by option(
    "--openapi-reference-cache-dir",
    help = "Cache directory for public HTTP(S) OpenAPI documents",
  ).file(mustExist = false, canBeFile = false, canBeDir = true)

  val offline by option(
    "--openapi-offline",
    help = "Resolve remote OpenAPI documents from the cache without HTTP requests",
  ).flag(default = false)

  val allowPrivateNetwork by option(
    "--openapi-allow-private-network",
    help = "Allow private network destinations and configured proxies for trusted OpenAPI specifications",
  ).flag(default = false)

  fun options(): OpenApiReferenceOptions =
    OpenApiReferenceOptions(
      cacheDirectory = cacheDirectory?.toPath() ?: OpenApiReferenceOptions().cacheDirectory,
      offline = offline,
      allowPrivateNetwork = allowPrivateNetwork,
    )
}
