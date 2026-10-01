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

import com.fasterxml.jackson.annotation.JsonInclude

/** Endpoint acquisition overrides and selection of one complete wire-security alternative. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeneratedSecuritySelection(
  val alternative: GeneratedSecurityRequirement? = null,
  val bindings: Map<String, GeneratedSecurityBinding> = emptyMap(),
) {
  /** Replaces a selected alternative atomically so an AND requirement is never partially inherited. */
  fun merge(other: GeneratedSecuritySelection): GeneratedSecuritySelection =
    GeneratedSecuritySelection(
      other.alternative ?: alternative,
      bindings + other.bindings.mapValues { (name, binding) -> bindings[name]?.merge(binding) ?: binding },
    )
}
