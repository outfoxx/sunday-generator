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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
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

@QuarkusTest
class ClosedModelsTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var mapper: ObjectMapper

  @Test
  fun `closed schemas reject extra fields despite the lenient Quarkus mapper`() {
    assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    HttpClient.newHttpClient().use { client ->
      for ((path, json, status) in listOf(
        Triple("/closed", """{"display-name":"valid"}""", 204),
        Triple("/closed", """{"display-name":"valid","extra":1}""", 400),
        Triple("/closed", """{"display-name":"valid","extra":null}""", 400),
        Triple("/closed", """{"display-name":"valid","nested":{"display-name":"nested","extra":1}}""", 400),
        Triple("/open", """{"name":"valid","extra":1}""", 204),
      )) {
        val response =
          client.send(
            HttpRequest
              .newBuilder(baseUri.resolve(path))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build(),
            HttpResponse.BodyHandlers.ofString(),
          )
        assertEquals(status, response.statusCode(), "$json: ${response.body()}")
      }
    }
  }
}
