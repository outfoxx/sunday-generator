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

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.PayloadUse
import io.outfoxx.sunday.generator.RequestTolerance

/** Directional override for a schema that declares an unknown enum or discriminator variant. */
enum class GeneratedTolerance {
  RESPONSE,
  ALL,
  ;

  companion object {
    /** Reads the canonical source spelling and rejects unsupported or mistyped values. */
    fun parse(
      value: Any?,
      context: String,
    ): GeneratedTolerance? =
      when (value) {
        null -> null
        "response" -> RESPONSE
        "all" -> ALL
        else -> throw IllegalArgumentException("$context must be 'response' or 'all', got '$value'")
      }
  }
}

/** Resolves direction without making a schema that has no fallback tolerant. */
fun GeneratedTolerance?.allowsUnknown(context: GenerationContext): Boolean =
  context.payloadUse != PayloadUse.Request ||
    when (this) {
      GeneratedTolerance.RESPONSE -> false
      GeneratedTolerance.ALL -> true
      null -> context.requestTolerance == RequestTolerance.Tolerant
    }
