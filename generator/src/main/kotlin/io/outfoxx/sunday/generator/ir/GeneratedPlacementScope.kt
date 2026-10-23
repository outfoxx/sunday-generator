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

import java.net.URI

/** Original source scope and its unresolved placement, ordered outermost to innermost. */
data class GeneratedPlacementScope(
  val declarationUri: String,
  val placement: GeneratedPlacement,
)

/** Reads placement on one native source object without traversing its references. */
internal fun Map<*, *>.placementScopes(location: String): List<GeneratedPlacementScope> =
  if (containsKey("x-sunday-placement")) {
    listOf(
      GeneratedPlacementScope(
        if ('#' in location) sourceDeclarationUri(location, location.substringAfter('#')) else location,
        GeneratedPlacement.read(this["x-sunday-placement"], location),
      ),
    )
  } else {
    emptyList()
  }

/** Encodes a source JSON Pointer as a URI fragment, retaining JSON Pointer escaping. */
internal fun sourceDeclarationUri(
  document: String,
  pointer: String,
): String = document.substringBefore('#') + "#" + URI(null, "", pointer).rawFragment
