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

package io.test.quarkus

import io.quarkiverse.resteasy.problem.HttpProblem
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.faulttolerance.api.RateLimitException
import io.test.quarkus.status.MissingProblem
import io.test.quarkus.status.OtherProblem
import io.test.quarkus.status.RebuildingProblem
import io.test.quarkus.status.StatusAPI
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.system.measureTimeMillis

@QuarkusTest
@QuarkusTestResource(StatusPolicyUpstream::class)
class StatusPolicyTest {
  @Inject
  @RestClient
  lateinit var client: StatusAPI

  @Test
  fun `excluded statuses retain generic and registered identities in JSON and CBOR`() =
    runBlocking {
      for (format in listOf("json", "cbor")) {
        for (scenario in listOf("401", "403", "404", "missing")) {
          repeat(3) {
            val key = key()
            val problem = failure<HttpProblem> { client.read(scenario, format, key) }
            assertEquals(if (scenario == "missing") 404 else scenario.toInt(), problem.statusCode)
            if (scenario == "missing") assertInstanceOf(MissingProblem::class.java, problem)
            assertEquals(key, problem.parameters["trace"])
            assertEquals(1, StatusPolicyUpstream.count(key))
          }
        }
        assertEquals("ok", client.read("200", format, key()))
      }
    }

  @Test
  fun `status families and named problems retry without broadening other conflicts`() =
    runBlocking {
      for (format in listOf("json", "cbor")) {
        val exhausted = key()
        val problem = failure<HttpProblem> { client.read("503", format, exhausted) }
        assertEquals(503, problem.statusCode)
        assertEquals("read attempt 3", problem.detail)
        assertEquals(3, StatusPolicyUpstream.count(exhausted))
        val recovered = key()
        assertEquals("ok", client.read("recover", format, recovered))
        assertEquals(3, StatusPolicyUpstream.count(recovered))
        val named = key()
        failure<RebuildingProblem> { client.read("rebuilding", format, named) }
        assertEquals(3, StatusPolicyUpstream.count(named))
        for (scenario in listOf("409", "other")) {
          val key = key()
          val conflict = failure<HttpProblem> { client.read(scenario, format, key) }
          if (scenario == "other") assertInstanceOf(OtherProblem::class.java, conflict)
          assertEquals(1, StatusPolicyUpstream.count(key))
        }
        val single = key()
        failure<HttpProblem> { client.noRetry("503", format, single) }
        assertEquals(1, StatusPolicyUpstream.count(single))
      }
    }

  @Test
  fun `cause chains retain their wrapper and apply status exclusions`() =
    runBlocking {
      val retry = key()
      val wrapped = failure<IllegalStateException> { client.read("wrapped", "json", retry) }
      assertInstanceOf(HttpProblem::class.java, wrapped.cause)
      assertEquals(3, StatusPolicyUpstream.count(retry))
      val excluded = key()
      failure<IllegalStateException> { client.read("wrapped-missing", "json", excluded) }
      assertEquals(1, StatusPolicyUpstream.count(excluded))
    }

  @Test
  fun `overlapping exclusions win over retry and breaker selectors`() =
    runBlocking {
      for (format in listOf("json", "cbor")) {
        for (scenario in listOf("503", "wrapped")) {
          // More invocations than the breaker's threshold prove skipped failures never open it.
          repeat(3) {
            val key = key()
            val error = failure<RuntimeException> { client.overlap(scenario, format, key) }
            val problem = assertInstanceOf(HttpProblem::class.java, if (scenario == "wrapped") error.cause else error)
            assertEquals(503, problem.statusCode)
            assertEquals(key, problem.parameters["trace"])
            assertEquals(1, StatusPolicyUpstream.count(key))
          }
          assertEquals("ok", client.overlap("200", format, key()))
        }
      }
    }

  @Test
  fun `transport failures use explicit class selectors`() =
    runBlocking {
      val selected = key()
      failure<java.io.IOException> { client.read("closed", "json", selected) }
      assertEquals(3, StatusPolicyUpstream.count(selected))
      val unselected = key()
      failure<java.io.IOException> { client.override("closed", "json", unselected) }
      assertEquals(1, StatusPolicyUpstream.count(unselected))
    }

  @Test
  fun `breaker state is per operation and recovers after half open`() =
    runBlocking {
      repeat(3) { failure<MissingProblem> { client.breaker("missing", "json", key()) } }
      repeat(2) { failure<HttpProblem> { client.breaker("503", "json", key()) } }
      val blocked = key()
      failure<CircuitBreakerOpenException> { client.breaker("200", "json", blocked) }
      assertEquals(0, StatusPolicyUpstream.count(blocked))
      assertEquals("ok", client.mutate("200", "json", key()))
      delay(300)
      assertEquals("ok", client.breaker("200", "json", key()))
      assertEquals("ok", client.breaker("200", "json", key()))
    }

  @Test
  fun `delay jitter duration timeout cancellation rate limit and configuration survive lowering`() =
    runBlocking {
      val jitter = key()
      val elapsed = measureTimeMillis { failure<HttpProblem> { client.jitter("503", "json", jitter) } }
      assertEquals(3, StatusPolicyUpstream.count(jitter))
      assertTrue(elapsed >= 340, "Retry delays were lost: $elapsed ms")
      val duration = key()
      val bounded = measureTimeMillis { failure<HttpProblem> { client.duration("503", "json", duration) } }
      assertTrue(StatusPolicyUpstream.count(duration) in 1..3)
      assertTrue(bounded < 1500, "Maximum duration was lost: $bounded ms")
      failure<TimeoutException> { client.timeout("slow", "json", key()) }
      val cancel = key()
      failure<kotlinx.coroutines.TimeoutCancellationException> {
        withTimeout(
          150,
        ) { client.cancel("503", "json", cancel) }
      }
      val attempts = StatusPolicyUpstream.count(cancel)
      delay(300)
      assertEquals(attempts, StatusPolicyUpstream.count(cancel))
      val override = key()
      failure<HttpProblem> { client.override("503", "json", override) }
      assertEquals(4, StatusPolicyUpstream.count(override))
      repeat(2) { assertEquals("ok", client.rate("200", "json", key())) }
      failure<RateLimitException> { client.rate("200", "json", key()) }
      Unit
    }

  private fun key(): String = UUID.randomUUID().toString()

  private suspend inline fun <reified T : Throwable> failure(crossinline action: suspend () -> Unit): T {
    try {
      action()
    } catch (error: Throwable) {
      return assertInstanceOf(T::class.java, error)
    }
    throw AssertionError("Expected ${T::class.java.name}")
  }
}
