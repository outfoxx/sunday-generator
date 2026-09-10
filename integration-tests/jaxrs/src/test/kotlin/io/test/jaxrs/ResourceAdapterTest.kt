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

package io.test.jaxrs

import io.test.jaxrs.api.UsersAPI
import io.test.jaxrs.api.UsersAPIResource
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.UriInfo
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.Principal

class ResourceAdapterTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(UsersAPIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(UsersAPIResource(Users())).to(UsersAPIResource::class.java)
          }
        },
      ).register(
        ContainerRequestFilter { request ->
          if (request.getHeaderString("Authorization") == "Bearer test-token") {
            request.securityContext =
              object : SecurityContext {
                override fun getUserPrincipal(): Principal = Principal { "alice" }

                override fun isUserInRole(role: String): Boolean = false

                override fun isSecure(): Boolean = false

                override fun getAuthenticationScheme(): String = "Bearer"
              }
          }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `anonymous requests reach public operations`() {
    target("/users").request().method("POST").use { response ->
      assertEquals(201, response.status)
      assertEquals("created", response.readEntity(String::class.java))
    }
    assertEquals(200, target("/users/optional").request().get().use { it.status })
    assertEquals(200, target("/users/mixed").request().get().use { it.status })
  }

  @Test
  fun `anonymous requests cannot reach protected operations`() {
    assertEquals(401, target("/users/private").request().get().use { it.status })
  }

  @Test
  fun `authentication supplied by the application reaches the protected delegate`() {
    target("/users/private").request().header("Authorization", "Bearer test-token").get().use { response ->
      assertEquals(200, response.status)
      assertEquals("private:alice", response.readEntity(String::class.java))
    }
  }

  private class Users : UsersAPI {
    override fun getUser(
      userId: String,
      securityContext: SecurityContext,
    ): Response = Response.ok("$userId:${securityContext.userPrincipal.name}").build()

    override fun createUser(uriInfo: UriInfo): Response =
      Response.created(uriInfo.absolutePath).entity("created").build()

    override fun optionalUser(): Response = Response.ok("optional").build()

    override fun mixedUser(): Response = Response.ok("mixed").build()
  }
}
