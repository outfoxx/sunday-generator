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
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.sun.net.httpserver.HttpServer
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** HTTP responses and independent attempt counters for generated status-policy clients. */
class StatusPolicyUpstream : QuarkusTestResourceLifecycleManager {
  private lateinit var server: HttpServer
  private val executor = Executors.newCachedThreadPool()

  override fun start(): Map<String, String> {
    counts.clear()
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = executor
    server.createContext("/status/") { exchange ->
      val (operation, scenario, format, key) =
        exchange.requestURI.path
          .removePrefix("/status/")
          .split('/')
      val count = counts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
      val status =
        when (scenario) {
          "recover" -> if (count < 3) 503 else 200
          "rebuilding", "other" -> 409
          "wrapped" -> 503
          "wrapped-missing" -> 404
          "missing" -> 404
          "slow" -> {
            Thread.sleep(1000)
            200
          }
          "closed" -> {
            exchange.close()
            return@createContext
          }
          else -> scenario.toInt()
        }
      if (scenario.startsWith("wrapped")) exchange.responseHeaders.set("X-Wrap-Problem", "true")
      val type =
        if (scenario in
          listOf("missing", "rebuilding", "other")
        ) {
          "https://example.test/status/$scenario"
        } else {
          "about:blank"
        }
      val body =
        mapOf(
          "type" to type,
          "title" to
            if (scenario in
              listOf("missing", "rebuilding", "other")
            ) {
              scenario.replaceFirstChar { it.uppercase() }
            } else {
              "Failure"
            },
          "status" to status,
          "detail" to "$operation attempt $count",
          "trace" to key,
        )
      val bytes =
        if (status ==
          200
        ) {
          "ok".toByteArray()
        } else {
          (
            if (format ==
              "cbor"
            ) {
              ObjectMapper(CBORFactory())
            } else {
              ObjectMapper()
            }
          ).writeValueAsBytes(body)
        }
      exchange.responseHeaders.set("Content-Type", if (status == 200) "text/plain" else "application/problem+$format")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
      exchange.close()
    }
    server.start()
    return mapOf(
      "quarkus.rest-client.status-client.url" to "http://127.0.0.1:${server.address.port}",
      "smallrye.faulttolerance.\"io.test.quarkus.status.StatusAPI.Guards.override_\".retry.max-retries" to "3",
    )
  }

  override fun stop() {
    server.stop(0)
    executor.shutdownNow()
  }

  companion object {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** Returns actual HTTP attempts for a unique test invocation key. */
    fun count(key: String): Int = counts[key]?.get() ?: 0
  }
}
