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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

/** A complete scheme-and-scope alternative that applications can select without splitting conjunctions. */
data class GeneratedClientFactoryAlternative(
  val name: String,
  val requirement: GeneratedSecurityRequirement,
)

/** Shares alternative identities across profiles and operations while retaining their exact required scopes. */
fun Collection<Map<String, List<GeneratedClientSecurity>>>.clientFactoryAlternatives():
  List<GeneratedClientFactoryAlternative> {
  val names = mutableSetOf<String>()
  return flatMap { it.values.flatten() }
    .map { security ->
      val schemes = security.requirement.schemes.sorted()
      GeneratedSecurityRequirement(
        schemes,
        schemes.associateWith {
          security.requirement.permissions[it]
            .orEmpty()
            .sorted()
        },
      )
    }.distinct()
    .mapIndexed { index, requirement ->
      val candidate =
        requirement.schemes
          .joinToString("And") {
            it.replace(Regex("[^A-Za-z0-9_]"), "_").toUpperCamelCase()
          }.ifEmpty { "Public" }
          .let { if (it.first().isLetter()) it else "Scheme$it" }
      val name =
        if (names.add(candidate)) {
          candidate
        } else {
          "Alternative${index + 1}".also {
            require(names.add(it)) { "Client security alternative name collision: $it" }
          }
        }
      GeneratedClientFactoryAlternative(name, requirement)
    }
}
