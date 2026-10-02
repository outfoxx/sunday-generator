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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Local upstream exposing invocation counts independently of client interception. */
class PolicyUpstream : QuarkusTestResourceLifecycleManager {
  private lateinit var server: HttpServer

  override fun start(): Map<String, String> {
    val counts = ConcurrentHashMap<String, AtomicInteger>()
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      val path = exchange.requestURI.path
      val key = path.substringBeforeLast('/')
      val countRequest = path.startsWith("/count/")
      val status = if (countRequest) 200 else path.substringAfterLast('/').toInt()
      val body = if (countRequest) (counts[path.removePrefix("/count")]?.get() ?: 0).toString() else "outcome"
      if (!countRequest) counts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
      val bytes = body.toByteArray()
      exchange.responseHeaders.set("Content-Type", "text/plain")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
      exchange.close()
    }
    server.start()
    return mapOf("quarkus.rest-client.fault-client.url" to "http://127.0.0.1:${server.address.port}")
  }

  override fun stop() {
    server.stop(0)
  }
}
