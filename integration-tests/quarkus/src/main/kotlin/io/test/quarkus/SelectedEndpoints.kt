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

import io.quarkus.security.identity.SecurityIdentity
import io.test.quarkus.selected.SelectedBearerAPI
import io.test.quarkus.selected.SelectedKeyAPI
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicInteger

/** Reports the native identity established before the selected resource delegates. */
@Singleton
class SelectedEndpoints :
  SelectedBearerAPI,
  SelectedKeyAPI {
  @Inject lateinit var identity: SecurityIdentity
  val calls = AtomicInteger()

  override fun readBearer(): RestResponse<String> = respond()

  override fun readSecondBearer(): RestResponse<String> = respond()

  override fun readKey(): RestResponse<String> = respond()

  private fun respond(): RestResponse<String> {
    calls.incrementAndGet()
    return RestResponse.ok(identity.principal.name)
  }
}
