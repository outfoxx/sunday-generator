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

import io.test.quarkus.secure.SecurityAPI
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse

/** Public delegate registered beside the protected resource. */
@Singleton
class SecureHealth : SecurityAPI {
  override fun health(): RestResponse<Unit> = RestResponse.noContent()
}
