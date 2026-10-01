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

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.test.quarkus.policies.client.ForbiddenProblem
import io.test.quarkus.policies.client.GuardedAPI
import io.test.quarkus.policies.client.UnauthorizedProblem
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
@QuarkusTestResource(PolicyUpstream::class)
class ScopedPolicyTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var endpoint: PolicyEndpoint

  @Inject
  @RestClient
  lateinit var client: GuardedAPI

  @Test
  fun `resource retries typed 401 twice and never retries 403`() {
    val before = endpoint.count("server-retry")
    assertEquals(401, request(baseUri.resolve("/fault/server-retry/401")).statusCode())
    assertEquals(before + 3, endpoint.count("server-retry"))
    assertEquals(403, request(baseUri.resolve("/fault/server-retry/403")).statusCode())
    assertEquals(before + 4, endpoint.count("server-retry"))
  }

  @Test
  fun `client selected profile retries typed 401 once and never retries 403`() {
    val before = upstreamCount("client-retry")
    assertThrows(UnauthorizedProblem::class.java) { client.clientRetry(401) }
    assertEquals(before + 2, upstreamCount("client-retry"))
    assertThrows(ForbiddenProblem::class.java) { client.clientRetry(403) }
    assertEquals(before + 3, upstreamCount("client-retry"))
  }

  @Test
  fun `resource breaker counts skipped authentication failures as successes`() {
    val before = endpoint.count("server-breaker")
    for (status in listOf(500, 500, 401, 403, 500, 500, 200)) {
      assertEquals(status, request(baseUri.resolve("/fault/server-breaker/$status")).statusCode())
    }
    assertEquals(before + 7, endpoint.count("server-breaker"))
    request(baseUri.resolve("/fault/server-breaker/500"))
    val openedAt = endpoint.count("server-breaker")
    request(baseUri.resolve("/fault/server-breaker/200"))
    assertEquals(openedAt, endpoint.count("server-breaker"))
  }

  @Test
  fun `client breaker counts skipped authentication failures as successes`() {
    val before = upstreamCount("client-breaker")
    for (status in listOf(500, 500, 401, 403, 500, 500)) {
      assertThrows(RuntimeException::class.java) { client.clientBreaker(status) }
    }
    assertEquals("outcome", client.clientBreaker(200))
    assertEquals(before + 7, upstreamCount("client-breaker"))
    assertThrows(RuntimeException::class.java) { client.clientBreaker(500) }
    val openedAt = upstreamCount("client-breaker")
    assertThrows(CircuitBreakerOpenException::class.java) { client.clientBreaker(200) }
    assertEquals(openedAt, upstreamCount("client-breaker"))
  }

  private fun upstreamCount(operation: String): Int {
    val url = ConfigProvider.getConfig().getValue("quarkus.rest-client.fault-client.url", String::class.java)
    return request(URI.create("$url/count/fault/$operation")).body().toInt()
  }

  private fun request(uri: URI): HttpResponse<String> =
    HttpClient.newHttpClient().use {
      it.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
    }
}
