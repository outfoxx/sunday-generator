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

import io.test.quarkus.closed.API
import io.test.quarkus.closed.ClosedRecord
import io.test.quarkus.closed.OpenRecord
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse

/** Accepts decoded requests so tests can distinguish decoding failures from handler results. */
@Singleton
class ClosedModelEndpoint : API {
  override fun acceptClosed(body: ClosedRecord): RestResponse<Unit> = RestResponse.noContent()

  override fun acceptOpen(body: OpenRecord): RestResponse<Unit> = RestResponse.noContent()
}
