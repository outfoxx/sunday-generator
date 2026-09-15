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

package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy
import java.security.MessageDigest

/** Specializes effective policies before emission; runtime beans receive constant policies, never routes. */
internal class KotlinQuarkusSecurityPlan(
  policies: List<GeneratedEndpointPolicy>,
) {

  data class Policy(
    val alternatives: List<Map<String, Set<String>>>,
  ) {
    val schemes = alternatives.flatMap { it.keys }.distinct().sorted()
    val simple = alternatives.size == 1 && schemes.size == 1 && alternatives.single().values.all { it.isEmpty() }
    val id =
      digest(
        alternatives.joinToString(";") { alternative ->
          "${alternative.size}:" +
            alternative.entries.joinToString(",") { (name, permissions) ->
              encode(name) + "${permissions.size}:" + permissions.joinToString("") { encode(it) }
            }
        },
      )
    val authenticationId = digest(schemes.joinToString("") { encode(it) })
  }

  val policies = policies.filter { it.requirements.isNotEmpty() }.map(::policy).distinct()
  val authentication = this.policies.distinctBy { it.authenticationId }
  val permissionSchemes =
    this.policies
      .flatMap { it.alternatives }
      .flatMap { it.entries }
      .filter { it.value.isNotEmpty() }
      .map { it.key }
      .toSortedSet()
  val subjectRequirements =
    this.policies
      .flatMap { it.alternatives }
      .map { it.keys }
      .filter { it.size > 1 }
      .distinct()

  companion object {
    fun policy(value: GeneratedEndpointPolicy): Policy =
      Policy(
        value.requirements.map { requirement ->
          requirement.schemes
            .distinct()
            .sorted()
            .associateWith { requirement.permissions[it].orEmpty().toSortedSet() }
        },
      )

    private fun encode(value: String) = "${value.length}:$value"

    private fun digest(value: String) =
      MessageDigest
        .getInstance(
          "SHA-256",
        ).digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
  }
}
