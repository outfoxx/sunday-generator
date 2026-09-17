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

import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.vertx.http.runtime.security.annotation.BasicAuthentication
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

/** Exercises application routes outside generated security, including Quarkus's fallback mechanism chain. */
@Path("/unselected")
class UnselectedResource {
  @Inject lateinit var identity: SecurityIdentity

  /** Requests authentication without selecting any generated strategy. */
  @GET
  @Path("/fallback")
  @Authenticated
  fun fallback(): String = identity.principal.name

  /** Selects the application's Basic mechanism in the presence of generated strategies. */
  @GET
  @Path("/basic")
  @BasicAuthentication
  @Authenticated
  fun basic(): String = identity.principal.name
}
