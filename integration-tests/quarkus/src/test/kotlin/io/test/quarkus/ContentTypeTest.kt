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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@QuarkusTest
class ContentTypeTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Test
  fun `generated Quarkus endpoints bind headers without widening body media constraints`() {
    val body = byteArrayOf(0, -1, 10, 13, 123, 125)
    val mediaTypes =
      listOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "text/plain",
        "application/octet-stream",
        "IMAGE/PNG",
        "image/png;profile=test",
      )
    HttpClient.newHttpClient().use { client ->
      for (path in listOf("raw", "image", "fixed", "enum", "constant", "multiple", "tolerant")) {
        for (mediaType in mediaTypes) {
          val baseType = mediaType.substringBefore(';').lowercase()
          val compatible =
            when (path) {
              "raw" -> true
              "fixed" -> baseType == "application/octet-stream"
              "multiple" -> baseType in listOf("image/png", "image/jpeg")
              else -> baseType.startsWith("image/")
            }
          val valid =
            when (path) {
              "enum" -> mediaType in listOf("image/png", "image/jpeg")
              "constant" -> mediaType == "image/png"
              else -> true
            }
          val expected =
            if (!compatible) {
              415
            } else if (!valid) {
              400
            } else {
              204
            }
          val response =
            client.send(
              HttpRequest
                .newBuilder(baseUri.resolve("/uploads/$path"))
                .header("Content-Type", mediaType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
              HttpResponse.BodyHandlers.ofString(),
            )
          assertEquals(expected, response.statusCode(), "$path $mediaType: ${response.body()}")
          if (expected == 204) {
            assertEquals(mediaType, response.headers().firstValue("X-Observed-Type").orElseThrow(), path)
            assertEquals(
              Base64.getEncoder().encodeToString(body),
              response.headers().firstValue("X-Observed-Body").orElseThrow(),
              path,
            )
          } else {
            assertTrue(response.headers().firstValue("X-Observed-Type").isEmpty, path)
          }
        }
      }
    }
  }

  @Test
  fun `missing headers preserve required optional and default behavior`() {
    HttpClient.newHttpClient().use { client ->
      for ((path, expectedHeader) in listOf(
        "raw" to null,
        "optional" to "missing",
        "default" to "application/octet-stream",
      )) {
        val response =
          client.send(
            HttpRequest
              .newBuilder(baseUri.resolve("/uploads/$path"))
              .PUT(HttpRequest.BodyPublishers.ofByteArray(byteArrayOf(0, -1)))
              .build(),
            HttpResponse.BodyHandlers.ofString(),
          )
        assertEquals(if (expectedHeader == null) 400 else 204, response.statusCode(), "$path: ${response.body()}")
        assertEquals(expectedHeader, response.headers().firstValue("X-Observed-Type").orElse(null), path)
      }
    }
  }
}
