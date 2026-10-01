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
import io.outfoxx.sunday.generator.genError

/** Typed fault-tolerance declarations applying to one shared, role, or profile scope. */
data class GeneratedPolicyValues(
  val timeout: GeneratedPolicySetting<GeneratedPolicyDuration>? = null,
  val retry: GeneratedPolicySetting<Retry>? = null,
  val circuitBreaker: GeneratedPolicySetting<CircuitBreaker>? = null,
  val rateLimit: GeneratedPolicySetting<RateLimit>? = null,
) {
  /** Retry members; missing values inherit and explicitly empty exception lists clear inherited lists. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class Retry(
    val maxRetries: Int? = null,
    val delay: GeneratedPolicyDuration? = null,
    val maxDuration: GeneratedPolicyDuration? = null,
    val jitter: GeneratedPolicyDuration? = null,
    val retryOn: List<GeneratedExceptionRef>? = null,
    val abortOn: List<GeneratedExceptionRef>? = null,
  ) {
    init {
      require(maxRetries == null || maxRetries >= -1) { "maxRetries must be -1 or greater" }
    }
  }

  /** Circuit-breaker members; skipOn classifies a matching failure as a successful breaker outcome. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class CircuitBreaker(
    val requestVolumeThreshold: Int? = null,
    val successThreshold: Int? = null,
    val failureRatio: Double? = null,
    val delay: GeneratedPolicyDuration? = null,
    val failOn: List<GeneratedExceptionRef>? = null,
    val skipOn: List<GeneratedExceptionRef>? = null,
  ) {
    init {
      require(
        requestVolumeThreshold == null || requestVolumeThreshold > 0,
      ) { "requestVolumeThreshold must be positive" }
      require(successThreshold == null || successThreshold > 0) { "successThreshold must be positive" }
      require(failureRatio == null || failureRatio.isFinite() && failureRatio in 0.0..1.0) {
        "failureRatio must be between 0 and 1"
      }
    }
  }

  /** Rate-limit members; a value may be inherited but is required after environment resolution. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class RateLimit(
    val value: Int? = null,
    val window: GeneratedPolicyDuration? = null,
    val minSpacing: GeneratedPolicyDuration? = null,
    val type: WindowType? = null,
  ) {
    init {
      require(value == null || value > 0) { "rateLimit.value must be positive" }
    }

    /** Native SmallRye rate-limit window strategies. */
    enum class WindowType { FIXED, ROLLING, SMOOTH }
  }

  /** Merges independently inherited members, optionally rejecting conflicting peer declarations. */
  fun merge(
    overrides: GeneratedPolicyValues,
    rejectConflicts: Boolean = false,
  ): GeneratedPolicyValues {
    fun <T> choose(
      base: T?,
      override: T?,
      member: String,
    ): T? {
      if (rejectConflicts &&
        base != null &&
        override != null &&
        base != override
      ) {
        genError("Conflicting policy member '$member'")
      }
      return override ?: base
    }

    fun <T> setting(
      base: GeneratedPolicySetting<T>?,
      override: GeneratedPolicySetting<T>?,
      member: String,
      merge: (T, T) -> T,
    ): GeneratedPolicySetting<T>? {
      if (base == null) return override
      if (override == null) return base
      if (rejectConflicts && base.enabled != override.enabled) genError("Conflicting policy member '$member'")
      return base.merge(override, merge)
    }
    return GeneratedPolicyValues(
      timeout =
        setting(
          timeout,
          overrides.timeout,
          "timeout",
        ) { base, override -> choose(base, override, "timeout")!! },
      retry =
        setting(retry, overrides.retry, "retry") { base, override ->
          Retry(
            maxRetries = choose(base.maxRetries, override.maxRetries, "retry.maxRetries"),
            delay = choose(base.delay, override.delay, "retry.delay"),
            maxDuration = choose(base.maxDuration, override.maxDuration, "retry.maxDuration"),
            jitter = choose(base.jitter, override.jitter, "retry.jitter"),
            retryOn = choose(base.retryOn, override.retryOn, "retry.retryOn"),
            abortOn = choose(base.abortOn, override.abortOn, "retry.abortOn"),
          )
        },
      circuitBreaker =
        setting(circuitBreaker, overrides.circuitBreaker, "circuitBreaker") { base, override ->
          CircuitBreaker(
            requestVolumeThreshold =
              choose(
                base.requestVolumeThreshold,
                override.requestVolumeThreshold,
                "circuitBreaker.requestVolumeThreshold",
              ),
            successThreshold =
              choose(
                base.successThreshold,
                override.successThreshold,
                "circuitBreaker.successThreshold",
              ),
            failureRatio = choose(base.failureRatio, override.failureRatio, "circuitBreaker.failureRatio"),
            delay = choose(base.delay, override.delay, "circuitBreaker.delay"),
            failOn = choose(base.failOn, override.failOn, "circuitBreaker.failOn"),
            skipOn = choose(base.skipOn, override.skipOn, "circuitBreaker.skipOn"),
          )
        },
      rateLimit =
        setting(rateLimit, overrides.rateLimit, "rateLimit") { base, override ->
          RateLimit(
            value = choose(base.value, override.value, "rateLimit.value"),
            window = choose(base.window, override.window, "rateLimit.window"),
            minSpacing = choose(base.minSpacing, override.minSpacing, "rateLimit.minSpacing"),
            type = choose(base.type, override.type, "rateLimit.type"),
          )
        },
    )
  }
}
