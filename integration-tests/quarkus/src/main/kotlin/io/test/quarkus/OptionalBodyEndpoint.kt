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

import io.test.quarkus.optional.server.API
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Records optional body delivery through generated coroutine resource adapters. */
@Singleton
class OptionalBodyEndpoint : API {
  /** Number of calls accepted by transport and validation. */
  val calls = AtomicInteger()

  /** Whether the most recent accepted request contained a body. */
  val bodyPresent = AtomicBoolean()

  override suspend fun optionalPost(body: io.test.quarkus.optional.server.OptionalInput?): RestResponse<Unit> =
    accepted(body)

  override suspend fun optionalDelete(body: io.test.quarkus.optional.server.OptionalInput?): RestResponse<Unit> =
    accepted(body)

  override suspend fun nullablePost(body: io.test.quarkus.optional.server.OptionalInput?): RestResponse<Unit> =
    accepted(body)

  override suspend fun requiredPost(body: io.test.quarkus.optional.server.OptionalInput): RestResponse<Unit> =
    accepted(body)

  private fun accepted(body: io.test.quarkus.optional.server.OptionalInput?): RestResponse<Unit> {
    calls.incrementAndGet()
    bodyPresent.set(body != null)
    return RestResponse.ResponseBuilder
      .create<Unit>(204)
      .header("X-Body-Present", (body != null).toString())
      .build()
  }
}
