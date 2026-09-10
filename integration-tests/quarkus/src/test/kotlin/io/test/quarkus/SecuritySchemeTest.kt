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
import io.test.quarkus.secure.OpenAPISecurity
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
class SecuritySchemeTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var delegate: SecurePolicy

  @Test
  fun `missing validators fail setup and missing credentials cannot invoke delegates`() {
    assertThrows(IllegalArgumentException::class.java) { OpenAPISecurity(emptyMap()) }
    val before = delegate.calls.get()
    listOf("basic", "bearer", "header", "query", "cookie", "scoped", "oidc", "combined", "tls").forEach {
      expect(401, it)
    }
    assertEquals(before, delegate.calls.get())
    expect(200, "anonymous")
    expect(200, "optional")
    expect(204, "health")
    assertTrue(
      request("basic")
        .headers()
        .firstValue("WWW-Authenticate")
        .orElseThrow()
        .contains("Basic"),
    )
    assertTrue(
      request("bearer")
        .headers()
        .firstValue("WWW-Authenticate")
        .orElseThrow()
        .contains("Bearer"),
    )
  }

  @Test
  fun `Quarkus aggregate resources enforce schemes permissions and all alternatives`() {
    val basic = "Authorization" to "Basic YWxpY2U6cGFzc3dvcmQ="
    val bearer = "Authorization" to "Bearer valid-token"
    val reader = "Authorization" to "Bearer reader-token"
    val key = "X-API-Key" to "valid-key"
    expect(200, "basic", basic)
    expect(200, "bearer", bearer)
    expect(200, "inherited", bearer)
    expect(401, "bearer", basic)
    expect(401, "basic", bearer)
    expect(401, "header", bearer)
    expect(200, "header", key)
    expect(200, "query?api_key=valid-key")
    expect(401, "query?wrong_name=valid-key")
    expect(401, "query?api_key=valid-key&api_key=invalid")
    expect(200, "cookie", "Cookie" to "session_key=valid-key")
    expect(401, "cookie", key)
    expect(200, "scoped", reader)
    expect(403, "allScopes", reader)
    expect(200, "allScopes", "Authorization" to "Bearer super-token")
    expect(200, "oidc", reader)
    expect(403, "scoped", "Authorization" to "Bearer unprivileged-token")
    expect(401, "scoped", bearer)
    expect(401, "combined", reader)
    expect(401, "combined", key)
    expect(200, "combined", reader, key)
    expect(403, "alternative", reader)
    expect(200, "alternative", reader, key)
    expect(200, "alternative", key)
    expect(200, "scopeAlternative", reader)
    expect(403, "role", key)
    expect(200, "role", "X-API-Key" to "admin-key")
    expect(401, "bearer", bearer, "Authorization" to "Bearer invalid")

    val before = delegate.calls.get()
    val denied = request("scoped", "Authorization" to "Bearer unprivileged-token")
    assertEquals(403, denied.statusCode())
    assertTrue(
      denied
        .headers()
        .firstValue("WWW-Authenticate")
        .orElseThrow()
        .contains("insufficient_scope"),
    )
    assertEquals(before, delegate.calls.get())
  }

  private fun expect(
    status: Int,
    operation: String,
    vararg headers: Pair<String, String>,
  ) {
    val response = request(operation, *headers)
    assertEquals(status, response.statusCode(), operation + ": " + response.body())
  }

  private fun request(
    operation: String,
    vararg headers: Pair<String, String>,
  ): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(baseUri.resolve("/strict/" + operation)).GET()
    headers.forEach { (name, value) -> builder.header(name, value) }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }
}
