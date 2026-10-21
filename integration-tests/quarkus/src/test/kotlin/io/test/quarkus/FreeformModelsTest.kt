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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
class FreeformModelsTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Test
  fun `freeform dictionaries accept JSON objects and reject strings and explicit null`() {
    val value =
      """{"name":"example","enabled":true,"count":3,"nullValue":null,"nested":{"value":null},"items":[1,"two",null]}"""
    HttpClient.newHttpClient().use { client ->
      for ((json, status) in listOf(
        "{}" to 204,
        """{"child":{"id":"one","enabled":true}}""" to 204,
        """{"child":{"enabled":true}}""" to 400,
        """{"properties":$value,"named":$value,"items":[$value]}""" to 204,
        """{"properties":{},"named":{},"items":[]}""" to 204,
        """{"properties":"{}"}""" to 400,
        """{"named":"{}"}""" to 400,
        """{"properties":null}""" to 400,
        """{"named":null}""" to 400,
      )) {
        val response =
          client.send(
            HttpRequest
              .newBuilder(baseUri.resolve("/documents"))
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
