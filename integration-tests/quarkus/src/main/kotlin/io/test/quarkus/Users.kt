/*
 * Copyright 2020 Outfox, Inc.
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

import io.test.quarkus.api.UsersAPI
import jakarta.inject.Singleton
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.UriInfo
import org.jboss.resteasy.reactive.RestResponse

/** Application-owned behavior used to exercise the generated Quarkus adapter. */
@Singleton
class Users : UsersAPI {
  override fun getUser(
    userId: String,
    securityContext: SecurityContext,
  ): RestResponse<String> = RestResponse.ok("$userId:${securityContext.userPrincipal.name}")

  override fun createUser(uriInfo: UriInfo): RestResponse<String> =
    RestResponse.ResponseBuilder
      .create(RestResponse.Status.CREATED, "created")
      .location(uriInfo.absolutePath)
      .build()

  override fun optionalUser(): RestResponse<String> = RestResponse.ok("optional")

  override fun mixedUser(): RestResponse<String> = RestResponse.ok("mixed")
}
