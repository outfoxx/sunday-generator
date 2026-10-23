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

import io.test.jaxrs.sse.EventsAPI
import io.test.jaxrs.sse.EventsAPIResource
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.MediaType
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SseResponseModeTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = GrizzlyTestContainerFactory()

  override fun configure(): Application {
    forceSet(TestProperties.CONTAINER_PORT, "0")
    return ResourceConfig(EventsAPIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(EventsAPIResource(Events())).to(EventsAPIResource::class.java)
          }
        },
      )
  }

  @Test
  fun `portable generated sink sends standard envelope fields over HTTP`() {
    target("portable-events").request("text/event-stream").get().use { response ->
      assertEquals(200, response.status)
      val body = response.readEntity(String::class.java)
      assertTrue(body.endsWith("\n\n"), body)
      val fields =
        body.lineSequence().filter { ':' in it }.associate {
          it.substringBefore(':') to
            it.substringAfter(':').trim()
        }
      assertEquals("heartbeat", fields[""])
      assertEquals("event-one", fields["id"])
      assertEquals("sample", fields["event"])
      assertEquals("1000", fields["retry"])
      assertEquals("payload", fields["data"])
    }
  }

  private class Events : EventsAPI {
    override fun events(
      sse: Sse,
      sseEvents: SseEventSink,
    ) {
      sseEvents
        .send(
          sse
            .newEventBuilder()
            .id("event-one")
            .name("sample")
            .comment("heartbeat")
            .reconnectDelay(1000)
            .mediaType(MediaType.TEXT_PLAIN_TYPE)
            .data(String::class.java, "payload")
            .build(),
        ).toCompletableFuture()
        .join()
      sseEvents.close()
    }
  }
}
