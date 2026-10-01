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
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Exercises native token acquisition and rotation against an application-selected endpoint. */
class ProfiledOidcUpstream : QuarkusTestResourceLifecycleManager {
  private lateinit var server: HttpServer
  private val executor = Executors.newCachedThreadPool()

  override fun start(): Map<String, String> {
    val counts = ConcurrentHashMap<String, AtomicInteger>()
    val requests = ConcurrentHashMap<String, AtomicInteger>()
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = executor
    server.createContext("/") { exchange ->
      val path = exchange.requestURI.path
      var status = 200
      val body =
        when {
          path == "/token" -> {
            val form =
              exchange.requestBody.bufferedReader().use { it.readText() }.split('&').associate { part ->
                part.substringBefore('=') to URLDecoder.decode(part.substringAfter('='), Charsets.UTF_8)
              }
            val scope = form["scope"] ?: "items:renew"
            val count = counts.computeIfAbsent(scope) { AtomicInteger() }.incrementAndGet()
            val credentials = "Basic " + Base64.getEncoder().encodeToString("test-client:test-secret".toByteArray())
            val valid =
              exchange.requestHeaders.getFirst("Authorization") == credentials &&
                if (count > 1 && scope == "items:renew") {
                  form["grant_type"] == "refresh_token" && form["refresh_token"] == "rotation-${count - 1}"
                } else {
                  form["grant_type"] == "client_credentials"
                }
            if (!valid) {
              status = 400
              """{"error":"invalid_request"}"""
            } else {
              val expires = if (scope == "items:renew") -1 else 3600
              """{"access_token":"$scope-$count","token_type":"Bearer","expires_in":$expires,"refresh_token":"rotation-$count","refresh_expires_in":3600}"""
            }
          }
          path.startsWith("/recover/") -> {
            requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            status =
              when {
                path.endsWith("/forbidden") -> 403
                path.endsWith("/once") || path.endsWith("/default") -> if (authorization.endsWith("-1")) 401 else 200
                else -> 401
              }
            if (status == 401 && !path.endsWith("/unchallenged")) {
              exchange.responseHeaders.set("WWW-Authenticate", "Bearer error=invalid_token")
            }
            authorization
          }
          path.startsWith(
            "/requests/",
          ) -> (requests["/recover/" + path.removePrefix("/requests/")]?.get() ?: 0).toString()
          path.startsWith("/count/") -> (counts[path.removePrefix("/count/")]?.get() ?: 0).toString()
          else -> exchange.requestHeaders.getFirst("Authorization") ?: "public"
        }
      val bytes = body.toByteArray()
      exchange.responseHeaders.set("Content-Type", if (path == "/token") "application/json" else "text/plain")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
      exchange.close()
    }
    server.start()
    val base = "http://127.0.0.1:${server.address.port}"
    return mapOf(
      "quarkus.rest-client.profiled-client.url" to base,
      "quarkus.oidc-client.service.token-path" to "$base/token",
      "quarkus.oidc-client.service.discovery-enabled" to "false",
      "quarkus.oidc-client.service.early-tokens-acquisition" to "false",
      "quarkus.oidc-client.service.client-id" to "test-client",
      "quarkus.oidc-client.service.credentials.secret" to "test-secret",
    )
  }

  override fun stop() {
    server.stop(0)
    executor.shutdownNow()
  }
}
