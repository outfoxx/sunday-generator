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

import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.validation.jakarta.ModelMode
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.test.quarkus.payloads.client.State
import io.test.quarkus.payloads.server.APIResource
import jakarta.inject.Inject
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import io.test.quarkus.payloads.client.API as NativePayloadsAPI

@QuarkusTest
class NativePayloadTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var endpoint: NativePayloadEndpoint

  @Inject
  lateinit var validator: Validator

  @Inject
  lateinit var resource: APIResource

  @Test
  fun `collection defaults agree between generated clients and native server injection`() {
    val client = QuarkusRestClientBuilder.newBuilder().baseUri(baseUri).build(NativePayloadsAPI::class.java)
    try {
      assertEquals(listOf("general"), client.collectionDefaults())
      assertEquals(listOf("general"), client.collectionDefaults(null, null))
      assertEquals(
        listOf("one", "two", "explicit"),
        client.collectionDefaults(listOf("one", "two"), listOf("explicit")),
      )
      assertEquals(listOf("general"), client.collectionDefaults(emptyList(), emptyList()))
    } finally {
      (client as AutoCloseable).close()
    }
  }

  @Test
  fun `defaulted client parameters can be omitted and server injection supplies defaults`() {
    val client = QuarkusRestClientBuilder.newBuilder().baseUri(baseUri).build(NativePayloadsAPI::class.java)
    try {
      assertEquals(listOf("explicit", "5", "cookie", "header"), client.defaultParameters("explicit", null, null, null))
      assertEquals(listOf("fallback", "5", "cookie", "header"), client.defaultParameters())
      assertThrows(NullPointerException::class.java) { client.defaultParameters(null, null, null, null) }
    } finally {
      (client as AutoCloseable).close()
    }
  }

  @Test
  fun `native request group reaches root collection elements`() {
    val unknown =
      io.test.quarkus.payloads.server.State
        .Unknown("future")
    assertTrue(validator.validate(unknown, ModelMode.Request::class.java).isNotEmpty(), "fallback metadata")
    val method = APIResource::class.java.getMethod("states", List::class.java)
    assertTrue(
      validator.forExecutables().validateParameters(resource, method, arrayOf(listOf(unknown))).isNotEmpty(),
      "resource group conversion",
    )
  }

  @Test
  fun `server validates root collections and fallback elements before delegate invocation`() {
    val before = endpoint.calls.get()
    for ((path, json) in listOf(
      "codes" to "[]",
      "codes" to "[\"AB\"]",
      "codes" to "[\"AB\",\"bad\"]",
      "states" to "[\"future\"]",
    )) {
      val response = send(path, json)
      assertEquals(400, response.statusCode(), response.body())
      assertEquals(before, endpoint.calls.get())
    }
    assertEquals(200, send("codes", "[\"AB\",\"CD\"]").statusCode())
    val response = send("states", "[\"ready\"]")
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("[\"future\"]", response.body())
    assertEquals(before + 2, endpoint.calls.get())
  }

  @Test
  fun `client checks each execution and accepts tolerant root responses`() {
    val client = QuarkusRestClientBuilder.newBuilder().baseUri(baseUri).build(NativePayloadsAPI::class.java)
    try {
      val before = endpoint.calls.get()
      val codes = mutableListOf("AB", "CD")
      assertEquals(codes, client.codes(codes))
      codes[1] = "bad"
      assertNativeFailure { client.codes(codes) }
      assertNativeFailure { client.codes(emptyList()) }
      assertNativeFailure { client.states(listOf(State.Unknown("ready"))) }
      assertEquals(before + 1, endpoint.calls.get())
      assertEquals(listOf(State.Unknown("future")), client.states(listOf(State.Ready)))
      assertEquals(before + 2, endpoint.calls.get())
    } finally {
      (client as AutoCloseable).close()
    }
  }

  @Test
  fun `server validates application output before encoding`() {
    val before = endpoint.calls.get()
    val response = send("codes", "[\"ZZ\",\"ZZ\"]")
    assertEquals(500, response.statusCode(), response.body())
    assertEquals(before + 1, endpoint.calls.get())
  }

  @Test
  fun `client validates root response constraints after decoding`() {
    val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    upstream.createContext("/") { exchange ->
      exchange.requestBody.use { it.readAllBytes() }
      exchange.responseHeaders.set("Content-Type", "application/json")
      val body = "[]".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
      exchange.close()
    }
    upstream.start()
    val client =
      QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create("http://127.0.0.1:${upstream.address.port}"))
        .build(NativePayloadsAPI::class.java)
    try {
      assertNativeFailure { client.codes(listOf("AB", "CD")) }
    } finally {
      (client as AutoCloseable).close()
      upstream.stop(0)
    }
  }

  @Test
  fun `typed request parameters fail before the server delegate`() {
    val before = endpoint.calls.get()
    HttpClient.newHttpClient().use { client ->
      for ((path, header) in listOf(
        "future" to ("headerState" to "ready"),
        "ready?queryStates=ready&queryStates=future" to ("headerState" to "ready"),
        "ready" to ("headerState" to "future"),
        "ready" to ("Cookie" to "cookieState=future"),
      )) {
        val request =
          HttpRequest
            .newBuilder(baseUri.resolve("/native/parameters/$path"))
            .header(header.first, header.second)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode(), response.body())
        assertEquals(before, endpoint.calls.get())
      }
      val request = HttpRequest.newBuilder(baseUri.resolve("/native/parameters/ready?openState=future")).GET().build()
      assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
      assertEquals(before + 1, endpoint.calls.get())
    }
  }

  @Test
  fun `typed client parameters fail before transmission`() {
    val sends =
      java.util.concurrent.atomic
        .AtomicInteger()
    val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    upstream.createContext("/") { exchange ->
      sends.incrementAndGet()
      exchange.responseHeaders.set("Content-Type", "application/json")
      val body = "[\"ready\"]".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
      exchange.close()
    }
    upstream.start()
    val client =
      QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create("http://127.0.0.1:${upstream.address.port}"))
        .build(NativePayloadsAPI::class.java)
    try {
      val future = State.Unknown("future")
      assertNativeFailure { client.parameters(future, null, null, null, null) }
      assertNativeFailure { client.parameters(State.Ready, listOf(future), null, null, null) }
      assertNativeFailure { client.parameters(State.Ready, null, null, null, future) }
      assertNativeFailure { client.parameters(State.Ready, null, null, future, null) }
      assertEquals(0, sends.get())
      client.parameters(
        State.Ready,
        null,
        io.test.quarkus.payloads.client.OpenState
          .Unknown("future"),
        null,
        null,
      )
      assertEquals(1, sends.get())
    } finally {
      (client as AutoCloseable).close()
      upstream.stop(0)
    }
  }

  private fun assertNativeFailure(call: () -> Unit) {
    val failure = assertThrows(RuntimeException::class.java, call)
    assertTrue(
      generateSequence<Throwable>(failure) {
        it.cause
      }.any { it is ConstraintViolationException },
      failure.toString(),
    )
  }

  private fun send(
    path: String,
    json: String,
  ): HttpResponse<String> =
    HttpClient.newHttpClient().use { client ->
      client.send(
        HttpRequest
          .newBuilder(baseUri.resolve("/native/$path"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    }
}
