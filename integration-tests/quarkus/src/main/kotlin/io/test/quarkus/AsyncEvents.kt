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
import io.test.quarkus.asyncapi.AsyncEventsAPI
import io.test.quarkus.asyncapi.AsyncKeysAPI
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/** Finite event streams make authenticated delegation observable in the HTTP fixture. */
@Singleton
class AsyncEvents :
  AsyncEventsAPI,
  AsyncKeysAPI {
  val calls = AtomicInteger()

  override fun receiveHeader(): Multi<String> = events()

  override fun receiveQuery(): Multi<String> = events()

  override fun receiveCookie(): Multi<String> = events()

  override fun receiveScoped(): Multi<String> = events()

  override fun receiveBearer(): Multi<String> = events()

  override fun receiveCombined(): Multi<String> = events()

  override fun receiveKeys(): Multi<String> = events()

  private fun events(): Multi<String> {
    calls.incrementAndGet()
    return Multi.createFrom().item("event")
  }
}
