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

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit

@QuarkusTest
class SseServerResponseModeTest {
  @TestHTTPResource lateinit var baseUri: URI

  @Inject lateinit var delegate: SseEvents

  @Test
  fun `typed streams retain data framing`() {
    val response = request("/sse/typed")
    assertEquals(200, response.statusCode())
    assertEquals(
      "data:: policy\ndata:retry: 1000\ndata:retry-max: 10000\ndata:keepalive: 2000\ndata:\ndata:\n\n",
      response.body(),
    )
  }

  @Test
  fun `standard envelopes preserve metadata comment heartbeat and blank line framing`() {
    val response = request("/sse/envelope")
    assertEquals(200, response.statusCode())
    assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElseThrow())
    assertEquals(
      ":heartbeat\n\ncontent-type:text/plain\nid:event-one\nevent:sample\nretry:1000\ndata:payload\n\n",
      response.body(),
    )
  }

  @Test
  fun `envelope serialization and neighboring ordinary JSON routes retain their payloads`() {
    val events = request("/sse/json")
    assertEquals(200, events.statusCode())
    assertEquals("content-type:application/json\ndata:{\"value\":\"payload\"}\n\n", events.body())
    val ordinary = request("/sse/value", accept = "application/json")
    assertEquals(200, ordinary.statusCode())
    assertEquals("{\"value\":\"ordinary\"}", ordinary.body())
    assertEquals(406, request("/sse/value").statusCode())
  }

  @Test
  fun `raw streams preserve exact extension fields status headers and bound parameters`() {
    val response = request("/sse/raw/policy?count=2", authenticated = true, lastId = "previous")
    assertEquals(202, response.statusCode(), response.body())
    assertEquals(SseEvents.POLICY.repeat(2), response.body())
    assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElseThrow())
    assertEquals("policy", response.headers().firstValue("X-Stream-Name").orElseThrow())
    assertEquals("previous", response.headers().firstValue("X-Last-Event-ID").orElseThrow())
  }

  @Test
  fun `raw streams retain authentication and request validation before delegation`() {
    val before = delegate.calls.get()
    assertEquals(401, request("/sse/raw/policy?count=1").statusCode())
    assertEquals(400, request("/sse/raw/x?count=1", authenticated = true).statusCode())
    assertEquals(400, request("/sse/raw/policy?count=0", authenticated = true).statusCode())
    assertEquals(before, delegate.calls.get())
  }

  @Test
  fun `raw streams propagate disconnect and request bounded demand`() {
    HttpClient.newHttpClient().use { client ->
      val response =
        client.send(
          builder("/sse/raw/live?count=1", true).build(),
          HttpResponse.BodyHandlers.ofInputStream(),
        )
      assertEquals(202, response.statusCode())
      assertFalse(delegate.cancelled.get())
      assertTrue(response.body().read() >= 0)
      response.body().close()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (!delegate.cancelled.get() && System.nanoTime() < deadline) Thread.sleep(10)
      assertTrue(delegate.cancelled.get(), "Disconnect must cancel the application publisher")
      assertEquals(1, delegate.maximumDemand.get())
    }
  }

  private fun request(
    path: String,
    authenticated: Boolean = false,
    accept: String = "text/event-stream",
    lastId: String? = null,
  ): HttpResponse<String> =
    HttpClient.newHttpClient().use { client ->
      val request = builder(path, authenticated).header("Accept", accept)
      lastId?.let { request.header("Last-Event-ID", it) }
      client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

  private fun builder(
    path: String,
    authenticated: Boolean,
  ): HttpRequest.Builder =
    HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10)).GET().apply {
      if (authenticated) {
        header("Authorization", "Basic " + Base64.getEncoder().encodeToString("alice:test-password".toByteArray()))
      }
    }
}
