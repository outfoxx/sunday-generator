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
import java.time.Duration

/** Exact, non-negative policy duration retained independently of a target annotation's unit. */
data class GeneratedPolicyDuration(
  val seconds: Long = 0,
  val nanos: Int = 0,
) {
  init {
    require(seconds >= 0 && nanos in 0..999_999_999) { "Policy durations must be non-negative and normalized" }
  }

  companion object {
    /** Parses ISO-8601 durations and the existing PT{n}MS millisecond vocabulary. */
    fun parse(
      value: String,
      location: String,
    ): GeneratedPolicyDuration {
      val milliseconds = Regex("PT([0-9]+)MS", RegexOption.IGNORE_CASE).matchEntire(value)?.groupValues?.get(1)
      val duration =
        runCatching {
          if (milliseconds != null) Duration.ofMillis(milliseconds.toLong()) else Duration.parse(value)
        }.getOrElse { genError("$location must be an ISO-8601 duration or PT{n}MS milliseconds literal") }
      if (duration.isNegative) genError("$location must be a non-negative duration")
      return GeneratedPolicyDuration(duration.seconds, duration.nano)
    }
  }
}
