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

/** Provenance for a declaration and its generated companions. */
data class GeneratedOutputOrigin(
  val category: GeneratedTypeCategory,
  val ownerUris: Set<URI>,
  /** Source declarations used in naming and output collision diagnostics. */
  val declarationUris: Set<String> = emptySet(),
  /** A nested declaration and its companions require every enclosing declaration to be selected. */
  val enclosing: GeneratedOutputOrigin? = null,
  /** Service-owned declarations match any contributing document, independently of their output category. */
  val serviceOwned: Boolean = category == GeneratedTypeCategory.Service,
)
