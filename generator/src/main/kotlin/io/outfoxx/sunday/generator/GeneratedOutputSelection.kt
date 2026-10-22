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

import java.net.URI

/** Output-only filters. Empty categories or owner URIs independently select everything. */
class GeneratedOutputSelection(
  val categories: Set<GeneratedTypeCategory> = emptySet(),
  ownerUris: Set<URI> = emptySet(),
) {
  /** Canonical absolute document identities; source fragments never identify owners. */
  val ownerUris: Set<URI> = ownerUris.map(GeneratedDocumentUri::canonical).toSet()

  /** Tests the category before resolving ownership. */
  fun includes(
    category: GeneratedTypeCategory,
    owners: Set<URI>,
  ): Boolean {
    if (categories.isNotEmpty() && category !in categories) return false
    if (ownerUris.isEmpty()) return true
    require(owners.isNotEmpty()) { "Cannot filter generated declaration without document ownership" }
    val canonicalOwners = owners.map(GeneratedDocumentUri::canonical).toSet()
    if (canonicalOwners.none { it in ownerUris }) return false
    require(category == GeneratedTypeCategory.Service || canonicalOwners.size == 1) {
      "Ambiguous declaration ownership: ${canonicalOwners.sortedBy(URI::toString).joinToString()}. " +
        "Reference one shared declaration or disambiguate the declarations before filtering output."
    }
    return canonicalOwners.any { it in ownerUris }
  }
}
