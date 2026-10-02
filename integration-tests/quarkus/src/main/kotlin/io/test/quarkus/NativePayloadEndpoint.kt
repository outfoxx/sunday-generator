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

import io.test.quarkus.payloads.server.State
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicInteger
import io.test.quarkus.payloads.server.API as NativePayloadsAPI

/** Counts requests reaching application code after generated native validation. */
@Singleton
class NativePayloadEndpoint : NativePayloadsAPI {
  /** Successful application invocations, independent of transport failures. */
  val calls = AtomicInteger()

  override fun codes(body: List<String>): RestResponse<List<String>> {
    calls.incrementAndGet()
    return RestResponse.ok(if (body == listOf("ZZ", "ZZ")) emptyList() else body)
  }

  override fun states(body: List<State>): RestResponse<List<State>> {
    calls.incrementAndGet()
    return RestResponse.ok(listOf(State.Unknown("future")))
  }

  override fun parameters(
    pathState: State,
    queryStates: List<State>?,
    openState: io.test.quarkus.payloads.server.OpenState?,
    cookieState: State?,
    headerState: State?,
  ): RestResponse<List<State>> {
    calls.incrementAndGet()
    return RestResponse.ok(listOf(pathState))
  }
}
