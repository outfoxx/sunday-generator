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

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder
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
class OptionalRequestBodyTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var endpoint: OptionalBodyEndpoint

  @Test
  fun `generated REST client can omit optional bodies`() {
    val client =
      QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(baseUri)
        .build(io.test.quarkus.optional.client.API::class.java)
    try {
      client.optionalPost()
      assertEquals(false, endpoint.bodyPresent.get())
      client.optionalDelete()
      assertEquals(false, endpoint.bodyPresent.get())
      client.optionalPost(
        io.test.quarkus.optional.client
          .OptionalInput(target = "ok"),
      )
      assertEquals(true, endpoint.bodyPresent.get())
    } finally {
      (client as AutoCloseable).close()
    }
  }

  @Test
  fun `optional bodies preserve absence and supplied validation`() {
    HttpClient.newHttpClient().use { client ->
      for (chunked in listOf(false, true)) {
        for ((operation, method) in listOf(
          "optionalPost" to "POST",
          "optionalDelete" to "DELETE",
          "requiredPost" to "POST",
          "nullablePost" to "POST",
        )) {
          for (contentType in listOf(null, "application/json", "text/plain")) {
            for (body in listOf(null, "null", " \n null \t", "{}", "{\"target\":\"ok\"}", "{\"target\":\"x\"}")) {
              if (body == null && contentType == "text/plain") continue
              val request = HttpRequest.newBuilder(baseUri.resolve("/native/$operation"))
              contentType?.let { request.header("Content-Type", it) }
              request.method(
                method,
                if (chunked) {
                  HttpRequest.BodyPublishers.ofInputStream { (body ?: "").byteInputStream() }
                } else {
                  body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
                },
              )
              val before = endpoint.calls.get()
              val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
              val expected =
                when {
                  body == null && operation != "requiredPost" -> 204
                  body != null && contentType == "text/plain" -> 415
                  body == null -> 400
                  body.trim() == "null" -> if (operation == "nullablePost") 204 else 400
                  body.contains("\"x\"") -> 400
                  else -> 204
                }
              val context = "$operation $contentType $body chunked=$chunked: ${response.body()}"
              assertEquals(expected, response.statusCode(), context)
              assertEquals(before + if (expected == 204) 1 else 0, endpoint.calls.get(), context)
              if (expected == 204) {
                assertEquals(
                  (body != null && body.trim() != "null").toString(),
                  response.headers().firstValue("X-Body-Present").orElse(null),
                  context,
                )
              }
            }
          }
        }
      }
    }
  }
}
