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

package io.test.packaged

import com.sun.net.httpserver.HttpServer
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Local token endpoint and downstream service for real Quarkus OIDC client filters. */
class MockOidcResource : QuarkusTestResourceLifecycleManager {
  private lateinit var server: HttpServer
  private val executor = Executors.newCachedThreadPool()

  override fun start(): Map<String, String> {
    val issued = AtomicInteger()
    val rejected = AtomicInteger()
    val lastRejected =
      java.util.concurrent.atomic
        .AtomicReference("")
    val rejectedPaths = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = executor
    server.createContext("/token") { exchange ->
      val request = exchange.requestBody.bufferedReader().readText()
      val grant = if (request.contains("subject_token=")) "exchange" else "service"
      val scope =
        request
          .split("&")
          .firstOrNull { it.startsWith("scope=") }
          ?.removePrefix("scope=")
          .orEmpty()
      val token = "$grant-$scope-${issued.incrementAndGet()}"
      val body = """{"access_token":"$token","expires_in":10,"token_type":"Bearer"}""".toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.createContext("/stats") { exchange ->
      val body = rejected.get().toString().toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.createContext("/last-rejected") { exchange ->
      val body = lastRejected.get().toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.createContext("/api/reject") { exchange ->
      rejected.incrementAndGet()
      val auth = exchange.requestHeaders.getFirst("Authorization").orEmpty()
      if (rejectedPaths.computeIfAbsent(exchange.requestURI.path) { AtomicInteger() }.incrementAndGet() == 1) {
        lastRejected.set(auth)
        exchange.sendResponseHeaders(401, -1)
        exchange.close()
      } else {
        val body = auth.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/plain")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
      }
    }
    server.createContext("/") { exchange ->
      if (exchange.requestURI.path !in
        setOf("/api/service", "/api/write", "/api/user", "/api/exchange", "/api/public")
      ) {
        exchange.sendResponseHeaders(404, -1)
        exchange.close()
        return@createContext
      }
      val body = (exchange.requestHeaders.getFirst("Authorization") ?: "anonymous").toByteArray()
      exchange.responseHeaders.add("Content-Type", "text/plain")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    val browser =
      if (System.getProperty("fixture.native-only") ==
        "true"
      ) {
        emptyMap()
      } else {
        BrowserOidcFixture(server).install()
      }
    server.start()
    val address = "http://127.0.0.1:${server.address.port}"
    return buildMap {
      putAll(browser)
      put("fixture.idp.url", address)
      put("fixture.idp.authority", "127.0.0.1:${server.address.port}")
      listOf("service", "exchange").forEach { name ->
        put("quarkus.oidc-client.$name.client-id", name)
        put("quarkus.oidc-client.$name.credentials.secret", "test-secret")
      }
    }
  }

  override fun stop() {
    server.stop(0)
    executor.shutdownNow()
  }
}
