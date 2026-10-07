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

import io.quarkus.security.Authenticated
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.inject.RestClient

/** Exercises the generated client's method-specific native bindings. */
@Path("/client")
@Produces("text/plain")
class ClientEndpoint(
  @RestClient private val client: io.test.packaged.client.API,
) {
  @GET
  @Path("/reject-get")
  fun rejectedGet(): Response = rejection { client.rejectedGet() }

  @GET
  @Path("/reject-post")
  fun rejectedPost(): Response = rejection { client.rejectedPost() }

  @GET
  @Path("/service")
  fun service(): String = client.serviceCall()

  @GET
  @Path("/write")
  fun write(): String = client.writeCall()

  @GET
  @Path("/public")
  fun publicCall(): String = client.publicCall()

  @GET
  @Path("/user")
  @Authenticated
  fun user(): String = client.userCall()

  @GET
  @Path("/user-without-login")
  fun userWithoutLogin(): Response = rejection { client.userCall() }

  @GET
  @Path("/exchange")
  @Authenticated
  fun exchange(): String = client.exchangeCall()

  private fun rejection(call: () -> String): Response =
    try {
      Response.ok(call()).build()
    } catch (failure: WebApplicationException) {
      Response.status(failure.response.status).build()
    } catch (failure: ProcessingException) {
      val response = failure.cause as? WebApplicationException ?: throw failure
      Response.status(response.response.status).build()
    }
}
