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

package io.test.jaxrs

import io.test.jaxrs.secure.OpenAPISecurity
import io.test.jaxrs.secure.PolicyAPI
import io.test.jaxrs.secure.PolicyAPIResource
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.Principal

class SecuritySchemeTest : JerseyTest() {
  private val delegate = Policy()

  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(PolicyAPIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(PolicyAPIResource(delegate, security())).to(PolicyAPIResource::class.java)
          }
        },
      ).register(
        // An identity from an unrelated authentication mechanism must not satisfy a named scheme.
        ContainerRequestFilter { request ->
          request.securityContext =
            object : SecurityContext {
              override fun getUserPrincipal(): Principal = Principal { "unrelated-user" }

              override fun isUserInRole(role: String): Boolean = true

              override fun isSecure(): Boolean = false

              override fun getAuthenticationScheme(): String = "unrelated"
            }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `missing validators fail setup and a generic principal cannot bypass credential checks`() {
    assertThrows(IllegalArgumentException::class.java) { OpenAPISecurity(emptyMap()) }
    val before = delegate.calls
    listOf("basic", "bearer", "header", "query", "cookie", "scoped", "oidc", "combined", "tls").forEach {
      expect(401, it)
    }
    assertEquals(before, delegate.calls)
    expect(200, "anonymous")
    expect(200, "optional")
    request("basic").use { assertTrue(it.getHeaderString("WWW-Authenticate").contains("Basic")) }
    request("bearer").use { assertTrue(it.getHeaderString("WWW-Authenticate").contains("Bearer")) }
  }

  @Test
  fun `Jersey resources enforce schemes permissions and all alternatives`() {
    val basic = "Authorization" to "Basic YWxpY2U6cGFzc3dvcmQ="
    val bearer = "Authorization" to "Bearer valid-token"
    val reader = "Authorization" to "Bearer reader-token"
    val key = "X-API-Key" to "valid-key"
    expect(200, "basic", basic)
    expect(200, "bearer", bearer)
    expect(200, "inherited", bearer)
    expect(401, "bearer", basic)
    expect(401, "basic", bearer)
    expect(401, "header", bearer)
    expect(200, "header", key)
    expect(200, "query?api_key=valid-key")
    expect(401, "query?wrong_name=valid-key")
    expect(401, "query?api_key=valid-key&api_key=invalid")
    expect(200, "cookie", "Cookie" to "session_key=valid-key")
    expect(401, "cookie", key)
    expect(200, "scoped", reader)
    expect(403, "allScopes", reader)
    expect(200, "allScopes", "Authorization" to "Bearer super-token")
    expect(200, "oidc", reader)
    expect(403, "scoped", "Authorization" to "Bearer unprivileged-token")
    expect(401, "scoped", bearer)
    expect(401, "combined", reader)
    expect(401, "combined", key)
    expect(200, "combined", reader, key)
    expect(403, "alternative", reader)
    expect(200, "alternative", reader, key)
    expect(200, "alternative", key)
    expect(200, "scopeAlternative", reader)
    expect(403, "role", key)
    expect(200, "role", "X-API-Key" to "admin-key")
    expect(401, "bearer", bearer, "Authorization" to "Bearer invalid")

    val before = delegate.calls
    request("scoped", "Authorization" to "Bearer unprivileged-token").use { response ->
      assertEquals(403, response.status)
      assertTrue(response.getHeaderString("WWW-Authenticate").contains("insufficient_scope"))
    }
    assertEquals(before, delegate.calls)
  }

  private fun expect(
    status: Int,
    operation: String,
    vararg headers: Pair<String, String>,
  ) {
    request(operation, *headers).use { response ->
      assertEquals(status, response.status, operation + ": " + response.readEntity(String::class.java))
    }
  }

  private fun request(
    operation: String,
    vararg headers: Pair<String, String>,
  ): Response {
    val request = client().target(target("/").uri.resolve("/strict/" + operation)).request()
    headers.forEach { (name, value) -> request.header(name, value) }
    return request.get()
  }

  private fun security(): OpenAPISecurity =
    OpenAPISecurity(
      OpenAPISecurity.schemes.mapValues { (_, definition) ->
        OpenAPISecurity.Authenticator { _, scheme, credential ->
          check(scheme == definition)
          val permissions =
            when (scheme.name) {
              "basicAuth" -> emptySet<String>().takeIf { credential == "YWxpY2U6cGFzc3dvcmQ=" }
              "bearerAuth" -> emptySet<String>().takeIf { credential == "valid-token" }
              "headerKey", "queryKey", "cookieKey" ->
                when (credential) {
                  "valid-key" -> emptySet()
                  "admin-key" -> setOf("admin")
                  else -> null
                }
              "oauth", "oidc" ->
                when (credential) {
                  "reader-token" -> setOf("read")
                  "super-token" -> setOf("read", "admin")
                  "unprivileged-token" -> emptySet()
                  else -> null
                }
              else -> null
            }
          permissions?.let { OpenAPISecurity.Identity(Principal { "alice" }, it) }
        }
      },
    )

  private class Policy : PolicyAPI {
    var calls = 0

    override fun inherited(): Response = respond()

    override fun basic(): Response = respond()

    override fun bearer(): Response = respond()

    override fun `header`(): Response = respond()

    override fun query(): Response = respond()

    override fun cookie(): Response = respond()

    override fun allScopes(): Response = respond()

    override fun scoped(): Response = respond()

    override fun oidc(): Response = respond()

    override fun combined(): Response = respond()

    override fun alternative(): Response = respond()

    override fun scopeAlternative(): Response = respond()

    override fun role(): Response = respond()

    override fun anonymous(): Response = respond()

    override fun optional(): Response = respond()

    override fun tls(): Response = respond()

    private fun respond(): Response {
      calls += 1
      return Response.ok("authorized").build()
    }
  }
}
