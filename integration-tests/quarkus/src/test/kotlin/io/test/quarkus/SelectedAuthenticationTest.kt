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
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID

@QuarkusTest
class SelectedAuthenticationTest {
  @TestHTTPResource lateinit var baseUri: URI

  @Inject lateinit var bindings: SelectedSecurityBindings

  @Inject lateinit var delegate: SelectedEndpoints
  private val client = HttpClient.newHttpClient()

  @Test
  fun `uniform bearer policy invokes only its selected binding before delegation`() {
    listOf("/selected/bearer", "/selected/bearer/second").forEach { path ->
      val before = delegate.calls.get()
      assertEquals(401, request(path).statusCode())
      assertEquals(401, request(path, bearer = "invalid").statusCode())
      assertEquals(before, delegate.calls.get())
      val id = UUID.randomUUID().toString()
      val response = request(path, id, "selected-token", "selected-key")
      assertEquals(200, response.statusCode(), response.body())
      assertEquals("bearerAuth", response.body())
      assertEquals(1, bindings.validations.getValue("$id:bearerAuth").get())
      assertFalse(bindings.validations.containsKey("$id:apiKey"))
      assertEquals(before + 1, delegate.calls.get())
    }
  }

  @Test
  fun `different strategy selects its own binding despite credentials for both`() {
    val before = delegate.calls.get()
    assertEquals(401, request("/selected/key").statusCode())
    assertEquals(401, request("/selected/key", key = "invalid").statusCode())
    assertEquals(before, delegate.calls.get())
    val id = UUID.randomUUID().toString()
    val response = request("/selected/key", id, "selected-token", "selected-key")
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("apiKey", response.body())
    assertEquals(1, bindings.validations.getValue("$id:apiKey").get())
    assertFalse(bindings.validations.containsKey("$id:bearerAuth"))
    assertEquals(before + 1, delegate.calls.get())
  }

  @Test
  fun `generated bindings never claim fallback or other selected mechanisms`() {
    listOf("/unselected/fallback", "/unselected/basic").forEach { path ->
      val id = UUID.randomUUID().toString()
      assertEquals(401, request(path, id, key = "selected-key").statusCode())
      val basic = Base64.getEncoder().encodeToString("alice:test-password".toByteArray())
      val response =
        client.send(
          HttpRequest
            .newBuilder(baseUri.resolve(path))
            .header("X-Request-ID", id)
            .header("X-Selected-Key", "selected-key")
            .header("Authorization", "Basic $basic")
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      assertEquals(200, response.statusCode(), response.body())
      assertEquals("alice", response.body())
      assertFalse(bindings.validations.containsKey("$id:apiKey"))
      assertFalse(bindings.validations.containsKey("$id:bearerAuth"))
    }
  }

  private fun request(
    path: String,
    id: String = UUID.randomUUID().toString(),
    bearer: String? = null,
    key: String? = null,
  ): HttpResponse<String> =
    client.send(
      HttpRequest
        .newBuilder(baseUri.resolve(path))
        .header("X-Request-ID", id)
        .apply {
          bearer?.let { header("Authorization", "Bearer $it") }
          key?.let { header("X-Selected-Key", it) }
        }.GET()
        .build(),
      HttpResponse.BodyHandlers.ofString(),
    )
}
