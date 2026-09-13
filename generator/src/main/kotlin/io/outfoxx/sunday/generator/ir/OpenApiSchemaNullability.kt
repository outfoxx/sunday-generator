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

import io.outfoxx.sunday.generator.genError
import java.util.Collections
import java.util.IdentityHashMap

/** Examines schema metadata without expanding recursive properties or allocating models. */
internal class OpenApiSchemaNullability(
  private val composition: OpenApiSchemaComposition,
) {
  fun isNullable(schema: Map<*, *>): Boolean =
    when (acceptance(schema)) {
      Acceptance.Accepted -> true
      Acceptance.Rejected, Acceptance.Unspecified -> false
      Acceptance.Unknown -> {
        val message =
          "Unsupported OpenAPI schema nullability: null acceptance cannot be determined from schema metadata"
        (schema as? OpenApiSchema)?.error(message) ?: genError(message)
      }
    }

  fun acceptsNull(schema: Map<*, *>): Boolean? =
    when (acceptance(schema)) {
      Acceptance.Accepted -> true
      Acceptance.Rejected -> false
      Acceptance.Unspecified, Acceptance.Unknown -> null
    }

  private fun acceptance(
    schema: Map<*, *>,
    visited: MutableSet<Map<*, *>> = Collections.newSetFromMap(IdentityHashMap()),
  ): Acceptance {
    if (!visited.add(schema)) return Acceptance.Unknown
    try {
      val effective = composition.resolve(schema)
      val assertions = mutableListOf<Acceptance>()
      when (val type = effective["type"]) {
        is String -> assertions += accepted(type == "null")
        is List<*> -> assertions += accepted("null" in type)
        else -> if (effective["nullable"] == true) assertions += Acceptance.Accepted
      }
      if (effective.containsKey("const")) assertions += accepted(effective["const"] == null)
      (effective["enum"] as? List<*>)?.let { assertions += accepted(null in it) }
      for (keyword in listOf("oneOf", "anyOf")) {
        val branches = effective[keyword] as? List<*> ?: continue
        val branchesAccepted =
          branches.map { branch ->
            when (branch) {
              is Map<*, *> -> acceptance(branch, visited)
              is Boolean -> accepted(branch)
              else -> Acceptance.Unknown
            }
          }
        assertions +=
          when {
            keyword == "anyOf" && Acceptance.Accepted in branchesAccepted -> Acceptance.Accepted
            keyword == "oneOf" && branchesAccepted.count { it == Acceptance.Accepted } > 1 -> Acceptance.Rejected
            Acceptance.Unknown in branchesAccepted -> Acceptance.Unknown
            Acceptance.Unspecified in branchesAccepted -> Acceptance.Unspecified
            keyword == "oneOf" -> accepted(branchesAccepted.count { it == Acceptance.Accepted } == 1)
            else -> Acceptance.Rejected
          }
      }
      // Conditional evaluation is outside the metadata projection supported by the IR.
      if (effective.keys.any { it in setOf("not", "if", "then", "else") }) assertions += Acceptance.Unknown
      return when {
        Acceptance.Rejected in assertions -> Acceptance.Rejected
        Acceptance.Unknown in assertions -> Acceptance.Unknown
        assertions.isEmpty() || Acceptance.Unspecified in assertions -> Acceptance.Unspecified
        else -> Acceptance.Accepted
      }
    } finally {
      visited.remove(schema)
    }
  }

  private fun accepted(value: Boolean): Acceptance = if (value) Acceptance.Accepted else Acceptance.Rejected

  // Unspecified retains the generator's unconstrained-schema convention; Unknown cannot choose a field type.
  private enum class Acceptance {
    Accepted,
    Rejected,
    Unspecified,
    Unknown,
  }
}
