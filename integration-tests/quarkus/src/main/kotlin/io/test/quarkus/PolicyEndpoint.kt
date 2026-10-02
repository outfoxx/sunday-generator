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

import io.test.quarkus.policies.server.ForbiddenProblem
import io.test.quarkus.policies.server.GuardedAPI
import io.test.quarkus.policies.server.UnauthorizedProblem
import jakarta.inject.Singleton
import jakarta.ws.rs.WebApplicationException
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Counts application calls after native fault-tolerance interception on generated resources. */
@Singleton
class PolicyEndpoint : GuardedAPI {
  private val calls = ConcurrentHashMap<String, AtomicInteger>()

  /** Returns actual delegate invocation counts for one generated operation. */
  fun count(operation: String): Int = calls[operation]?.get() ?: 0

  override fun serverRetry(status: Int): RestResponse<String> = respond("server-retry", status)

  override fun serverBreaker(status: Int): RestResponse<String> = respond("server-breaker", status)

  override fun clientRetry(status: Int): RestResponse<String> = respond("client-retry", status)

  override fun clientBreaker(status: Int): RestResponse<String> = respond("client-breaker", status)

  private fun respond(
    operation: String,
    status: Int,
  ): RestResponse<String> {
    calls.computeIfAbsent(operation) { AtomicInteger() }.incrementAndGet()
    return when (status) {
      200 -> RestResponse.ok("ok")
      401 -> throw UnauthorizedProblem()
      403 -> throw ForbiddenProblem()
      else -> throw WebApplicationException(status)
    }
  }
}
