/*
 * Copyright 2020 Outfox, Inc.
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@QuarkusTest
class ResourceAdapterTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var ramlAccess: RamlAccess

  @Test
  fun `RAML anonymous alternatives and protected overrides enforce the contract`() {
    val initialCalls = ramlAccess.calls.get()
    listOf("public", "mixed", "resource", "trait").forEach { path ->
      assertEquals(204, request("/raml/" + path).statusCode(), path)
    }
    val protected =
      listOf(
        "GET" to "inherited",
        "POST" to "resource",
        "GET" to "resource/nested",
        "GET" to "replacement",
      )
    protected.forEach { (method, path) ->
      assertEquals(401, request("/raml/" + path, method).statusCode(), path)
    }
    assertEquals(initialCalls + 4, ramlAccess.calls.get())
    protected.forEach { (method, path) ->
      assertEquals(204, request("/raml/" + path, method, authenticated = true).statusCode(), path)
    }
    assertEquals(initialCalls + 8, ramlAccess.calls.get())
  }

  @Test
  fun `anonymous requests reach explicitly public delegates`() {
    val response = request("/users", "POST")
    assertEquals(201, response.statusCode(), response.body())
    assertEquals("created", response.body())
    assertTrue(
      response
        .headers()
        .firstValue("Location")
        .orElseThrow()
        .endsWith("/users"),
    )
    assertEquals(200, request("/users/optional").statusCode())
    assertEquals(200, request("/users/mixed").statusCode())
    assertEquals(204, request("/health").statusCode())
  }

  @Test
  fun `anonymous requests to authenticated endpoints are rejected`() {
    assertEquals(401, request("/users/private").statusCode())
    assertEquals(401, request("/accounts/private").statusCode())
  }

  @Test
  fun `aggregate locator path parameters reach the operation delegate`() {
    val response = request("/accounts/account-1", authenticated = true)
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("account-1", response.body())
  }

  @Test
  fun `validation executes on the generated endpoint`() {
    assertEquals(400, request("/users/x", authenticated = true).statusCode())
  }

  @Test
  fun `authenticated requests reach the protected delegate with context and path parameters`() {
    val response = request("/users/private", authenticated = true)
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("private:alice", response.body())
  }

  private fun request(
    path: String,
    method: String = "GET",
    authenticated: Boolean = false,
  ): HttpResponse<String> {
    val request = HttpRequest.newBuilder(baseUri.resolve(path)).method(method, HttpRequest.BodyPublishers.noBody())
    if (authenticated) {
      val credentials = Base64.getEncoder().encodeToString("alice:test-password".toByteArray())
      request.header("Authorization", "Basic $credentials")
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
  }
}
