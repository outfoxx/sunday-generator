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
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
class AsyncSecurityTest {
  @TestHTTPResource lateinit var baseUri: URI

  @Inject lateinit var delegate: AsyncEvents

  @Inject lateinit var bindings: AsyncSecurityBindings

  @Test
  fun `AsyncAPI requirements validate credentials and permissions before delegation`() {
    listOf("header", "query", "cookie", "scoped", "bearer", "combined").forEach { expect(401, "/async3/$it") }
    val key = "X-API-Key" to "valid-key"
    val reader = "Authorization" to "Bearer reader"
    expect(200, "/async3/header", key)
    expect(401, "/async3/header", "X-API-Key" to "invalid")
    expect(200, "/async3/query?api_key=valid-key")
    expect(401, "/async3/query?wrong_name=valid-key")
    expect(200, "/async3/cookie", "Cookie" to "session_key=valid-key")
    expect(200, "/async3/bearer", "Authorization" to "Bearer valid-token")
    expect(200, "/async3/scoped", reader)
    val before = delegate.calls.get()
    expect(403, "/async3/scoped", "Authorization" to "Bearer unscoped")
    expect(401, "/async3/combined", reader)
    expect(401, "/async3/combined", key)
    assertEquals(before, delegate.calls.get())
    expect(200, "/async3/combined", reader, key)
    expect(401, "/async2/keys?api_key=valid-key", key)
    expect(200, "/async2/keys?api_key=valid-key", key, "Cookie" to "session_key=valid-key")
    val keyBefore = bindings.validations.getValue("headerKey").get()
    val tokenBefore = bindings.validations.getValue("eventToken").get()
    expect(200, "/async3/combined", reader, key)
    assertEquals(keyBefore + 1, bindings.validations.getValue("headerKey").get())
    assertEquals(tokenBefore + 1, bindings.validations.getValue("eventToken").get())
  }

  private fun expect(
    status: Int,
    path: String,
    vararg headers: Pair<String, String>,
  ) {
    val builder = HttpRequest.newBuilder(baseUri.resolve(path)).GET()
    headers.forEach { (name, value) -> builder.header(name, value) }
    val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    assertEquals(status, response.statusCode(), path + ": " + response.body())
  }
}
