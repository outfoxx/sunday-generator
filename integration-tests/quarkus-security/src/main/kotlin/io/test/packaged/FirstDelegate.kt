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

package io.test.packaged

import io.quarkus.security.identity.SecurityIdentity
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse

/** Application behavior behind a resource discovered from a dependency jar. */
@Singleton
class FirstDelegate(
  private val identity: SecurityIdentity,
) : io.test.packaged.first.API {
  override fun protectedCall(): RestResponse<String> = RestResponse.ok(identity.principal.name)

  override fun publicCall(): RestResponse<String> = RestResponse.ok("public")
}
