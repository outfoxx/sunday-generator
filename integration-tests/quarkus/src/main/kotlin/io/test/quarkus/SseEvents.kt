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

import io.smallrye.mutiny.Multi
import io.test.quarkus.sse.EventPayload
import io.test.quarkus.sse.EventsAPI
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.sse.OutboundSseEvent
import jakarta.ws.rs.sse.Sse
import org.jboss.resteasy.reactive.RestMulti
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Application delegate used to exercise the generated SSE transport modes over HTTP. */
@Singleton
class SseEvents : EventsAPI {
  companion object {
    const val POLICY = ": policy\nretry: 1000\nretry-max: 10000\nkeepalive: 2000\n\n"
  }

  @Inject lateinit var sse: Sse

  val calls = AtomicInteger()
  val cancelled = AtomicBoolean()
  val maximumDemand = AtomicLong()

  override fun typedEvents(): Multi<String> = Multi.createFrom().item(POLICY)

  override fun envelopeEvents(): Multi<OutboundSseEvent> =
    Multi.createFrom().items(
      sse.newEventBuilder().comment("heartbeat").build(),
      sse
        .newEventBuilder()
        .id("event-one")
        .name("sample")
        .reconnectDelay(1000)
        .mediaType(MediaType.TEXT_PLAIN_TYPE)
        .data(String::class.java, "payload")
        .build(),
    )

  override fun jsonEvents(): Multi<OutboundSseEvent> =
    Multi.createFrom().item(
      sse
        .newEventBuilder()
        .mediaType(MediaType.APPLICATION_JSON_TYPE)
        .data(EventPayload::class.java, EventPayload("payload"))
        .build(),
    )

  override fun jsonValue(): RestResponse<EventPayload> = RestResponse.ok(EventPayload("ordinary"))

  override fun rawEvents(
    name: String,
    count: Int,
    lastEventID: String?,
  ): RestMulti<ByteArray> {
    calls.incrementAndGet()
    val data =
      if (name == "live") {
        cancelled.set(false)
        maximumDemand.set(0)
        Multi.createFrom().publisher(PolicyPublisher())
      } else {
        Multi.createFrom().iterable(POLICY.repeat(count).chunked(7).map { it.encodeToByteArray() })
      }
    return RestMulti
      .fromMultiData(data)
      .status(202)
      .header("Content-Type", "text/event-stream")
      .header("X-Stream-Name", name)
      .header("X-Last-Event-ID", lastEventID ?: "none")
      .build()
  }

  /** Leaves the stream open after three frames so closing the HTTP client must cancel the publisher. */
  private inner class PolicyPublisher : Flow.Publisher<ByteArray> {
    override fun subscribe(subscriber: Flow.Subscriber<in ByteArray>) {
      subscriber.onSubscribe(
        object : Flow.Subscription {
          private var sent = 0

          override fun request(n: Long) {
            maximumDemand.accumulateAndGet(n, ::maxOf)
            if (sent++ < 3) subscriber.onNext(POLICY.encodeToByteArray())
          }

          override fun cancel() {
            cancelled.set(true)
          }
        },
      )
    }
  }
}
