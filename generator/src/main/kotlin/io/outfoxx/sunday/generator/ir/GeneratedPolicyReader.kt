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
import io.outfoxx.sunday.generator.ir.GeneratedEnvironmentReader.objectValue

/** Converts canonical scoped source metadata into typed policy declarations without resolving an environment. */
internal object GeneratedPolicyReader {
  fun read(
    value: Any?,
    location: String,
  ): GeneratedEnvironment<GeneratedPolicyValues> = GeneratedEnvironmentReader.read(value, location, ::values)

  private fun values(
    value: Any?,
    location: String,
  ): GeneratedPolicyValues {
    val members = objectValue(value, location, setOf("rateLimit", "timeout", "retry", "circuitBreaker"))

    fun <T> setting(
      name: String,
      parse: (Any?, String) -> T,
    ): GeneratedPolicySetting<T>? {
      if (!members.containsKey(name)) return null
      val input = members[name]
      if (input == false) return GeneratedPolicySetting(enabled = false)
      return GeneratedPolicySetting(
        value =
          runCatching { parse(input, "$location.$name") }.getOrElse { error ->
            genError("Invalid $location.$name: ${error.message}")
          },
      )
    }
    return GeneratedPolicyValues(
      timeout = setting("timeout", ::duration),
      retry = setting("retry", ::retry),
      circuitBreaker = setting("circuitBreaker", ::circuitBreaker),
      rateLimit = setting("rateLimit", ::rateLimit),
    )
  }

  private fun retry(
    value: Any?,
    location: String,
  ): GeneratedPolicyValues.Retry {
    val members =
      objectValue(value, location, setOf("maxRetries", "delay", "maxDuration", "jitter", "retryOn", "abortOn"))
    return GeneratedPolicyValues.Retry(
      maxRetries = members.optional("maxRetries", location, ::integer),
      delay = members.optional("delay", location, ::duration),
      maxDuration = members.optional("maxDuration", location, ::duration),
      jitter = members.optional("jitter", location, ::duration),
      retryOn = members.optional("retryOn", location, ::exceptions),
      abortOn = members.optional("abortOn", location, ::exceptions),
    )
  }

  private fun circuitBreaker(
    value: Any?,
    location: String,
  ): GeneratedPolicyValues.CircuitBreaker {
    val members =
      objectValue(
        value,
        location,
        setOf("requestVolumeThreshold", "successThreshold", "failureRatio", "delay", "failOn", "skipOn"),
      )
    return GeneratedPolicyValues.CircuitBreaker(
      requestVolumeThreshold = members.optional("requestVolumeThreshold", location, ::integer),
      successThreshold = members.optional("successThreshold", location, ::integer),
      failureRatio = members.optional("failureRatio", location, ::number),
      delay = members.optional("delay", location, ::duration),
      failOn = members.optional("failOn", location, ::exceptions),
      skipOn = members.optional("skipOn", location, ::exceptions),
    )
  }

  private fun rateLimit(
    value: Any?,
    location: String,
  ): GeneratedPolicyValues.RateLimit {
    val members = objectValue(value, location, setOf("value", "window", "minSpacing", "type"))
    return GeneratedPolicyValues.RateLimit(
      value = members.optional("value", location, ::integer),
      window = members.optional("window", location, ::duration),
      minSpacing = members.optional("minSpacing", location, ::duration),
      type =
        members.optional("type", location) { input, path ->
          val text = input as? String ?: genError("$path must be fixed, rolling, or smooth")
          GeneratedPolicyValues.RateLimit.WindowType.entries
            .firstOrNull { it.name.equals(text, ignoreCase = true) }
            ?: genError("$path must be fixed, rolling, or smooth")
        },
    )
  }

  private fun duration(
    value: Any?,
    location: String,
  ): GeneratedPolicyDuration =
    GeneratedPolicyDuration.parse(value as? String ?: genError("$location must be a duration string"), location)

  private fun integer(
    value: Any?,
    location: String,
  ): Int {
    if (value !is Number && value !is String) genError("$location must be an integer")
    return runCatching { value.toString().toBigDecimal().intValueExact() }
      .getOrElse { genError("$location must be an integer in the supported range") }
  }

  private fun number(
    value: Any?,
    location: String,
  ): Double {
    if (value !is Number && value !is String) genError("$location must be a finite number")
    return value.toString().toDoubleOrNull()?.takeIf { it.isFinite() }
      ?: genError("$location must be a finite number")
  }

  private fun exceptions(
    value: Any?,
    location: String,
  ): List<GeneratedExceptionRef> =
    (if (value is List<*>) value else listOf(value))
      .mapIndexed { index, item ->
        when (item) {
          is String -> GeneratedExceptionRef(className = item)
          is Map<*, *> -> {
            val reference = objectValue(item, "$location[$index]", setOf("problem"))
            GeneratedExceptionRef(
              problem =
                reference["problem"] as? String ?: genError("$location[$index].problem must name a generated problem"),
            )
          }
          else -> genError("$location[$index] must be an exception class name or {problem: name}")
        }
      }.distinct()

  private fun <T> Map<String, Any?>.optional(
    name: String,
    location: String,
    parse: (Any?, String) -> T,
  ): T? = if (containsKey(name)) parse(this[name], "$location.$name") else null
}
