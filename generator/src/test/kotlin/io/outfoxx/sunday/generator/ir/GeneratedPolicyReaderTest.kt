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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GeneratedPolicyReaderTest {
  @Test
  fun `resolves shared role and profile members without losing unselected declarations`() {
    val policy =
      GeneratedPolicyReader.read(
        mapOf(
          "all" to mapOf("timeout" to "PT5S", "retry" to mapOf("delay" to "PT100MS")),
          "client" to
            mapOf(
              "rateLimit" to mapOf("value" to 3, "window" to "PT1S"),
              "retry" to mapOf("maxRetries" to 3),
            ),
          "server" to mapOf("rateLimit" to mapOf("value" to 7, "window" to "PT1S")),
          "profiles" to
            mapOf(
              "internal" to
                mapOf(
                  "all" to mapOf("timeout" to "PT2S"),
                  "client" to mapOf("retry" to mapOf("maxRetries" to 1, "retryOn" to "java.io.IOException")),
                ),
            ),
        ),
        "x-sunday-policy",
      )
    val context = GenerationContext(GenerationMode.Client, profile = "internal")
    val client = policy.resolve(context) { base, override -> base.merge(override) }!!
    assertEquals(GeneratedPolicyDuration(2), client.timeout?.value)
    assertEquals(3, client.rateLimit?.value?.value)
    assertEquals(1, client.retry?.value?.maxRetries)
    assertEquals(GeneratedPolicyDuration(0, 100_000_000), client.retry?.value?.delay)
    assertEquals(listOf(GeneratedExceptionRef(className = "java.io.IOException")), client.retry?.value?.retryOn)
    val server = policy.resolve(context.copy(role = GenerationMode.Server)) { base, override -> base.merge(override) }!!
    assertEquals(7, server.rateLimit?.value?.value)
    assertEquals(null, server.retry?.value?.maxRetries)
    assertEquals(
      3,
      policy.client
        ?.retry
        ?.value
        ?.maxRetries,
    )
  }

  @Test
  fun `disable and empty exception lists replace inherited members`() {
    val base =
      GeneratedPolicyReader.read(
        mapOf(
          "client" to
            mapOf(
              "timeout" to "PT5S",
              "retry" to
                mapOf(
                  "maxRetries" to 3,
                  "retryOn" to listOf("java.io.IOException"),
                  "abortOn" to mapOf("problem" to "forbidden"),
                ),
            ),
        ),
        "x-sunday-policy",
      )
    val local =
      GeneratedPolicyReader.read(
        mapOf(
          "client" to
            mapOf(
              "timeout" to false,
              "retry" to mapOf("retryOn" to emptyList<String>()),
            ),
        ),
        "x-sunday-policy",
      )
    val result = base.merge(local) { inherited, override -> inherited.merge(override) }.client!!
    assertFalse(result.timeout!!.enabled)
    assertEquals(emptyList<GeneratedExceptionRef>(), result.retry!!.value!!.retryOn)
    assertEquals(listOf(GeneratedExceptionRef(problem = "forbidden")), result.retry.value.abortOn)
    assertEquals(3, result.retry.value.maxRetries)
    val disabled = GeneratedPolicySetting<GeneratedPolicyValues.Retry>(enabled = false)
    val replacement = GeneratedPolicySetting(value = GeneratedPolicyValues.Retry(maxRetries = 1))
    assertEquals(replacement, disabled.merge(replacement) { inherited, _ -> inherited })
  }

  @Test
  fun `peer policies merge disjoint members and reject conflicting declarations`() {
    val first =
      GeneratedPolicyValues(retry = GeneratedPolicySetting(value = GeneratedPolicyValues.Retry(maxRetries = 1)))
    val second =
      GeneratedPolicyValues(
        retry = GeneratedPolicySetting(value = GeneratedPolicyValues.Retry(delay = GeneratedPolicyDuration(1))),
      )
    assertEquals(
      1,
      first
        .merge(second, rejectConflicts = true)
        .retry
        ?.value
        ?.maxRetries,
    )
    assertThrows(GenerationException::class.java) {
      first.merge(first.copy(retry = GeneratedPolicySetting(enabled = false)), rejectConflicts = true)
    }
    assertThrows(GenerationException::class.java) {
      first.merge(
        GeneratedPolicyValues(retry = GeneratedPolicySetting(value = GeneratedPolicyValues.Retry(maxRetries = 2))),
        rejectConflicts = true,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = ["timeout", "retry", "circuitBreaker", "clientRateLimit", "serverRateLimit", "source", "clients"],
  )
  fun `rejects removed flat fields and unknown scopes`(member: String) {
    val error =
      assertThrows(GenerationException::class.java) {
        GeneratedPolicyReader.read(mapOf(member to emptyMap<String, String>()), "x-sunday-policy")
      }
    assertTrue(error.message!!.contains(member))
  }

  @Test
  fun `rejects malformed members in unselected profiles`() {
    val malformed =
      listOf(
        mapOf("retry" to mapOf("maxRetries" to -2)),
        mapOf("retry" to mapOf("maxRetries" to 1.5)),
        mapOf("retry" to mapOf("retryOn" to true)),
        mapOf("retry" to mapOf("abortOn" to listOf(null))),
        mapOf("retry" to mapOf("retryOn" to mapOf("className" to "java.lang.Exception"))),
        mapOf("retry" to mapOf("delay" to "5")),
        mapOf("timeout" to "-PT1S"),
        mapOf("timeout" to true),
        mapOf("rateLimit" to mapOf("value" to 0)),
        mapOf("rateLimit" to mapOf("type" to "sliding")),
        mapOf("circuitBreaker" to mapOf("failureRatio" to Double.NaN)),
        mapOf("circuitBreaker" to mapOf("requestVolume" to 1)),
      )
    malformed.forEach { value ->
      val error =
        assertThrows(GenerationException::class.java) {
          GeneratedPolicyReader.read(
            mapOf("profiles" to mapOf("unselected" to mapOf("client" to value))),
            "x-sunday-policy",
          )
        }
      assertTrue(error.message!!.contains("unselected.client"), error.message)
    }
  }

  @Test
  fun `preserves nanosecond precision and normalizes duration vocabularies`() {
    assertEquals(GeneratedPolicyDuration(0, 1), GeneratedPolicyDuration.parse("PT0.000000001S", "timeout"))
    assertEquals(GeneratedPolicyDuration(1), GeneratedPolicyDuration.parse("PT1000MS", "timeout"))
    assertEquals(GeneratedPolicyDuration(86_400), GeneratedPolicyDuration.parse("P1D", "timeout"))
  }
}
