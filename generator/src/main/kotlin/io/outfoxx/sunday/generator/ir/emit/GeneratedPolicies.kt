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

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedPolicyValues
import io.outfoxx.sunday.generator.ir.GeneratedService

/** True when the resolved settings require runtime enforcement. */
val GeneratedPolicyValues.hasEnabledPolicies: Boolean
  get() = listOf(timeout, retry, circuitBreaker, rateLimit).any { it?.enabled == true }

/** Rejects selected policies that this target cannot enforce instead of silently omitting them. */
fun List<GeneratedService>.requireNoUnsupportedPolicies(
  context: GenerationContext,
  target: String,
) {
  for (service in this) {
    for (operation in service.operations) {
      val policy = operation.policy?.resolve(context) { base, override -> base.merge(override) }
      if (policy?.hasEnabledPolicies == true) {
        genError(
          "Operation '${operation.id}' selects fault-tolerance policies unsupported by $target; " +
            "choose a profile without these policies or generate a Quarkus JAX-RS target",
        )
      }
    }
  }
}
