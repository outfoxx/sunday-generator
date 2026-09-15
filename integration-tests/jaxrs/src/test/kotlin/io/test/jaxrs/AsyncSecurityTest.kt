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

package io.test.jaxrs

import io.test.jaxrs.asyncapi.AsyncEventsAPI
import io.test.jaxrs.asyncapi.AsyncEventsAPIResource
import io.test.jaxrs.asyncapi.AsyncKeysAPI
import io.test.jaxrs.asyncapi.AsyncKeysAPIResource
import io.test.jaxrs.asyncapi.OpenAPISecurity
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.TestProperties
import org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.Principal

class AsyncSecurityTest : JerseyTest() {
  private val delegate = Events()

  override fun getTestContainerFactory(): TestContainerFactory = GrizzlyTestContainerFactory()

  override fun configure(): Application {
    forceSet(TestProperties.CONTAINER_PORT, "0")
    return ResourceConfig(AsyncEventsAPIResource::class.java, AsyncKeysAPIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            val security = security()
            bind(AsyncEventsAPIResource(delegate, security)).to(AsyncEventsAPIResource::class.java)
            bind(AsyncKeysAPIResource(delegate, security)).to(AsyncKeysAPIResource::class.java)
          }
        },
      )
  }

  @Test
  fun `AsyncAPI requirements validate credentials and permissions before delegation`() {
    listOf("header", "query", "cookie", "scoped", "bearer", "combined").forEach { expect(401, "/async3/$it") }
    val key = "X-API-Key" to "valid-key"
    val reader = "Authorization" to "Bearer reader"
    expect(200, "/async3/header", key)
    expect(401, "/async3/header", "X-API-Key" to "invalid")
    expect(200, "/async3/query?api_key=valid-key")
    expect(401, "/async3/query?wrong_name=valid-key")
    expect(200, "/async3/cookie", "Cookie" to "session_key=valid-key")
    expect(200, "/async3/bearer", "Authorization" to "Bearer valid-token")
    expect(200, "/async3/scoped", reader)
    val before = delegate.calls
    expect(403, "/async3/scoped", "Authorization" to "Bearer unscoped")
    expect(401, "/async3/combined", reader)
    expect(401, "/async3/combined", key)
    assertEquals(before, delegate.calls)
    expect(200, "/async3/combined", reader, key)
    expect(401, "/async2/keys?api_key=valid-key", key)
    expect(200, "/async2/keys?api_key=valid-key", key, "Cookie" to "session_key=valid-key")
  }

  private fun expect(
    status: Int,
    path: String,
    vararg headers: Pair<String, String>,
  ) {
    val request = client().target(target("/").uri.resolve(path)).request()
    headers.forEach { (name, value) -> request.header(name, value) }
    request.get().use { assertEquals(status, it.status, path + ": " + it.readEntity(String::class.java)) }
  }

  private fun security(): OpenAPISecurity =
    OpenAPISecurity(
      OpenAPISecurity.schemes.mapValues { (name, _) ->
        OpenAPISecurity.Authenticator { _, _, credential ->
          val permissions =
            when {
              name.endsWith("Key") && credential == "valid-key" -> emptySet<String>()
              name.startsWith("inline_") && credential == "valid-token" -> emptySet()
              name == "eventToken" && credential == "reader" -> setOf("read")
              name == "eventToken" && credential == "unscoped" -> emptySet()
              else -> null
            }
          permissions?.let { OpenAPISecurity.Identity(Principal { "alice" }, it) }
        }
      },
    )

  private class Events :
    AsyncEventsAPI,
    AsyncKeysAPI {
    var calls = 0

    override fun receiveHeader(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveQuery(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveCookie(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveScoped(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveBearer(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveCombined(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    override fun receiveKeys(
      sse: Sse,
      sseEvents: SseEventSink,
    ) = respond(sse, sseEvents)

    private fun respond(
      sse: Sse,
      sink: SseEventSink,
    ) {
      calls += 1
      sink.send(sse.newEvent("event")).toCompletableFuture().join()
      sink.close()
    }
  }
}
