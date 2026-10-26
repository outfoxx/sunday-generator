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
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
@TestProfile(StrictUnknownFieldsTest.StrictMapper::class)
class StrictUnknownFieldsTest {
  /** Enables the application policy that generated no-op catch-all handlers used to bypass. */
  class StrictMapper : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
      mapOf("quarkus.jackson.fail-on-unknown-properties" to "true")
  }

  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var mapper: ObjectMapper

  @Test
  fun `open models without storage return 400 for unknown field names`() {
    assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    HttpClient.newHttpClient().use { client ->
      for ((json, status) in listOf(
        """{"name":"valid"}""" to 204,
        """{"name":"valid","misspelled":1}""" to 400,
        """{"name":"valid","misspelled":null}""" to 400,
        """{"name":"valid","misspelled":{}}""" to 400,
        """{"name":"valid","misspelled":[]}""" to 400,
      )) {
        val response =
          client.send(
            HttpRequest
              .newBuilder(baseUri.resolve("/open"))
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
