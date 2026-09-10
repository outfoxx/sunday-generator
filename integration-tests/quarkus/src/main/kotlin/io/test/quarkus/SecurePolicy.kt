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

import io.test.quarkus.secure.PolicyAPI
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicInteger

/** Application delegate with an invocation counter for the real HTTP security tests. */
@Singleton
class SecurePolicy : PolicyAPI {
  val calls = AtomicInteger()

  override fun inherited(): RestResponse<String> = respond()

  override fun basic(): RestResponse<String> = respond()

  override fun bearer(): RestResponse<String> = respond()

  override fun `header`(): RestResponse<String> = respond()

  override fun query(): RestResponse<String> = respond()

  override fun cookie(): RestResponse<String> = respond()

  override fun allScopes(): RestResponse<String> = respond()

  override fun scoped(): RestResponse<String> = respond()

  override fun oidc(): RestResponse<String> = respond()

  override fun combined(): RestResponse<String> = respond()

  override fun alternative(): RestResponse<String> = respond()

  override fun scopeAlternative(): RestResponse<String> = respond()

  override fun role(): RestResponse<String> = respond()

  override fun anonymous(): RestResponse<String> = respond()

  override fun optional(): RestResponse<String> = respond()

  override fun tls(): RestResponse<String> = respond()

  private fun respond(): RestResponse<String> {
    calls.incrementAndGet()
    return RestResponse.ok("authorized")
  }
}
