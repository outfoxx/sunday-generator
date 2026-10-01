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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedExceptionRef
import io.outfoxx.sunday.generator.ir.GeneratedPolicyDuration
import io.outfoxx.sunday.generator.ir.GeneratedPolicyValues
import java.math.BigInteger
import java.time.temporal.ChronoUnit

/** Emits native fault-tolerance annotations from an already resolved environment. */
internal class KotlinPolicyAnnotations(
  private val exceptionType: (GeneratedExceptionRef) -> ClassName,
) {
  fun annotations(
    policy: GeneratedPolicyValues,
    additionalAbortOn: List<ClassName> = emptyList(),
  ): List<AnnotationSpec> =
    buildList {
      policy.timeout?.value?.let { duration ->
        if (duration == GeneratedPolicyDuration()) genError("Quarkus timeout must be positive")
        add(AnnotationSpec.builder(timeout).apply { duration("value", "unit", duration) }.build())
      }
      policy.retry?.value?.let { retry ->
        add(
          AnnotationSpec
            .builder(retryType)
            .apply {
              retry.maxRetries?.let { addMember("maxRetries = %L", it) }
              retry.delay?.let { duration("delay", "delayUnit", it) }
              retry.maxDuration?.let { duration("maxDuration", "durationUnit", it) }
              retry.jitter?.let { duration("jitter", "jitterDelayUnit", it) }
              exceptions("retryOn", retry.retryOn)
              exceptions("abortOn", retry.abortOn, additionalAbortOn)
            }.build(),
        )
      }
      policy.circuitBreaker?.value?.let { breaker ->
        add(
          AnnotationSpec
            .builder(circuitBreaker)
            .apply {
              breaker.requestVolumeThreshold?.let { addMember("requestVolumeThreshold = %L", it) }
              breaker.successThreshold?.let { addMember("successThreshold = %L", it) }
              breaker.failureRatio?.let { addMember("failureRatio = %L", it) }
              breaker.delay?.let { duration("delay", "delayUnit", it) }
              exceptions("failOn", breaker.failOn)
              exceptions("skipOn", breaker.skipOn)
            }.build(),
        )
      }
      policy.rateLimit?.value?.let { rate ->
        val value = rate.value ?: genError("Quarkus rateLimit policy requires integer key 'value'")
        if (rate.window == GeneratedPolicyDuration()) genError("Quarkus rateLimit.window must be positive")
        add(
          AnnotationSpec
            .builder(rateLimit)
            .apply {
              addMember("value = %L", value)
              rate.window?.let { duration("window", "windowUnit", it) }
              rate.minSpacing?.let { duration("minSpacing", "minSpacingUnit", it) }
              rate.type?.let { addMember("type = %T.%L", rateLimitType, it.name) }
            }.build(),
        )
      }
    }

  private fun AnnotationSpec.Builder.exceptions(
    name: String,
    values: List<GeneratedExceptionRef>?,
    additional: List<ClassName> = emptyList(),
  ) {
    if (values == null && additional.isEmpty()) return
    val classes = CodeBlock.builder()
    (values.orEmpty().map(exceptionType) + additional).distinct().forEachIndexed { index, value ->
      if (index > 0) classes.add(", ")
      classes.add("%T::class", value)
    }
    addMember("%L = [%L]", name, classes.build())
  }

  private fun AnnotationSpec.Builder.duration(
    valueMember: String,
    unitMember: String,
    value: GeneratedPolicyDuration,
  ) {
    // Choose an exact unit; milliseconds would silently truncate submillisecond policy values.
    val nanos = BigInteger.valueOf(value.seconds) * billion + BigInteger.valueOf(value.nanos.toLong())
    val (unit, amount) =
      units.firstNotNullOfOrNull { unit ->
        val parts = nanos.divideAndRemainder(BigInteger.valueOf(unit.duration.toNanos()))
        if (parts[1] == BigInteger.ZERO && parts[0] <= maxLong) unit to parts[0] else null
      } ?: genError("Policy duration cannot be represented exactly by a Quarkus annotation")
    addMember("%L = %L", valueMember, amount)
    addMember("%L = %T.%L", unitMember, ChronoUnit::class, unit.name)
  }

  companion object {
    private val timeout = ClassName("org.eclipse.microprofile.faulttolerance", "Timeout")
    private val retryType = ClassName("org.eclipse.microprofile.faulttolerance", "Retry")
    private val circuitBreaker = ClassName("org.eclipse.microprofile.faulttolerance", "CircuitBreaker")
    private val rateLimit = ClassName("io.smallrye.faulttolerance.api", "RateLimit")
    private val rateLimitType = ClassName("io.smallrye.faulttolerance.api", "RateLimitType")
    private val billion = BigInteger.valueOf(1_000_000_000)
    private val maxLong = BigInteger.valueOf(Long.MAX_VALUE)
    private val units =
      listOf(
        ChronoUnit.HOURS,
        ChronoUnit.MINUTES,
        ChronoUnit.SECONDS,
        ChronoUnit.MILLIS,
        ChronoUnit.MICROS,
        ChronoUnit.NANOS,
      )
  }
}
