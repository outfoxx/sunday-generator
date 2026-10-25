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

import com.fasterxml.jackson.databind.ObjectMapper
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

@QuarkusTest
class InheritedProblemTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var mapper: ObjectMapper

  @Test
  fun `inherited problem extensions reach the HTTP response`() {
    HttpClient.newHttpClient().use { client ->
      val response =
        client.send(
          HttpRequest.newBuilder(baseUri.resolve("/inherited-problems")).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      assertEquals(409, response.statusCode(), response.body())
      assertTrue(
        response
          .headers()
          .firstValue("Content-Type")
          .orElse("")
          .startsWith("application/problem+json"),
      )
      assertEquals(
        mapper.readTree(
          """{"type":"https://example.com/problems/rebuilding","title":"Resource Rebuilding","status":409,
            "detail":"Resource A is rebuilding","instance":"/inherited-problems","traceId":"trace-A",
            "nullableParent":null,"resourceKey":"A","retryAfterSeconds":1,"nullableChild":null,
            "metadata":{"region":"west"},"queuePosition":2,"queue-name":"rebuilds"}""",
        ),
        mapper.readTree(response.body()),
      )
    }
  }
}
