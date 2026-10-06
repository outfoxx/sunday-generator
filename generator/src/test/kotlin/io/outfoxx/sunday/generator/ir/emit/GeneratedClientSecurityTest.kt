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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedEnvironment
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedSecuritySelection
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedClientSecurityTest {
  private val context = GenerationContext(GenerationMode.Client)
  private val operation = GeneratedOperation("fetch", "GET", "/items")
  private val service = GeneratedService("Items", operations = listOf(operation))
  private val bearer = GeneratedSecurityScheme("token", "http", scheme = "bearer")
  private val provider = GeneratedEnvironment(client = GeneratedSecurityBinding(provider = "identity"))

  @Test
  fun `bearer metadata never implies token acquisition`() {
    assertNull(api(listOf(requirement("token")), listOf(bearer)).clientSecurity(service, operation, context))
    val selected =
      api(
        listOf(requirement("token")),
        listOf(bearer.copy(bindings = provider)),
      ).clientSecurity(service, operation, context)!!
    assertEquals(GeneratedSecurityBinding.Flow.EXTERNAL, selected.bindings.getValue("token").flow)
    assertNull(selected.bindings.getValue("token").tokenUrl)
    assertThrows(GenerationException::class.java) {
      bearer
        .copy(
          bindings =
            GeneratedEnvironment(
              client = GeneratedSecurityBinding(provider = "identity", tokenUrl = "https://identity/token"),
            ),
        ).resolveSecurityBinding(context)
    }
  }

  @Test
  fun `explicit OAuth providers may supply deployment endpoints at runtime`() {
    val scheme =
      bearer.copy(
        bindings =
          GeneratedEnvironment(
            client =
              GeneratedSecurityBinding(
                provider = "identity",
                flow = GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS,
              ),
          ),
      )
    val binding = scheme.resolveSecurityBinding(context)!!
    assertEquals(GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS, binding.flow)
    assertNull(binding.tokenUrl)
    assertNull(binding.discoveryUrl)
  }

  @Test
  fun `multiple configured alternatives including anonymous require explicit selection`() {
    val api = api(listOf(requirement("token"), requirement()), listOf(bearer.copy(bindings = provider)))
    assertThrows(GenerationException::class.java) { api.clientSecurity(service, operation, context) }
    val selected =
      api.copy(
        auth = api.auth!!.copy(selection = GeneratedEnvironment(client = GeneratedSecuritySelection(requirement()))),
      )
    assertEquals(
      emptyMap<String, GeneratedSecurityBinding>(),
      selected.clientSecurity(service, operation, context)!!.bindings,
    )
    assertEquals(api.auth.requirements, selected.auth!!.requirements)
  }

  @Test
  fun `same scheme with different scopes can be selected without ambiguity`() {
    val read = GeneratedSecurityRequirement(listOf("token"), mapOf("token" to listOf("read")))
    val write = GeneratedSecurityRequirement(listOf("token"), mapOf("token" to listOf("write")))
    val api = api(listOf(read, write), listOf(bearer.copy(bindings = provider)))
    assertThrows(GenerationException::class.java) { api.clientSecurity(service, operation, context) }
    val selected =
      api.copy(
        auth = api.auth!!.copy(selection = GeneratedEnvironment(client = GeneratedSecuritySelection(write))),
      )
    assertEquals(write, selected.clientSecurity(service, operation, context)!!.requirement)
  }

  @Test
  fun `missing conjunct and colliding transports cannot become partial credentials`() {
    val key =
      GeneratedSecurityScheme(
        "key",
        "apiKey",
        headers =
          listOf(
            GeneratedParameter("Authorization", GeneratedParameter.Location.HEADER, GeneratedTypeRef.scalar("string")),
          ),
      )
    val api = api(listOf(requirement("token", "key")), listOf(bearer.copy(bindings = provider), key))
    val missing = assertThrows(GenerationException::class.java) { api.clientSecurity(service, operation, context) }
    assertTrue(missing.message!!.contains("Missing client provider for 'key'"))
    val collision =
      api.copy(
        auth =
          api.auth!!.copy(
            securitySchemes = listOf(bearer.copy(bindings = provider), key.copy(bindings = provider)),
          ),
      )
    val conflict =
      assertThrows(GenerationException::class.java) { collision.clientSecurity(service, operation, context) }
    assertTrue(conflict.message!!.contains("conflict at header 'authorization'"))
  }

  @Test
  fun `one complete alternative may be selected when another has no provider`() {
    val api =
      api(
        listOf(requirement("token"), requirement("second")),
        listOf(bearer.copy(bindings = provider), bearer.copy(name = "second")),
      )
    assertEquals(requirement("token"), api.clientSecurity(service, operation, context)!!.requirement)
  }

  @Test
  fun `client endpoint overrides leave server validation and wire discovery unchanged`() {
    val scheme =
      bearer.copy(
        type = "openIdConnect",
        openIdConnectUrl = "https://issuer.example/discovery",
        bindings =
          GeneratedEnvironment(
            server = GeneratedSecurityBinding(provider = "trusted-issuer"),
            profiles =
              mapOf(
                "internal" to
                  GeneratedEnvironment.Scope(
                    client =
                      GeneratedSecurityBinding(
                        provider = "service",
                        flow = GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS,
                        discoveryUrl = "https://identity.internal/discovery",
                        audience = "api",
                      ),
                  ),
              ),
          ),
      )
    assertEquals(
      "https://identity.internal/discovery",
      scheme.resolveSecurityBinding(GenerationContext(GenerationMode.Client, "internal"))!!.discoveryUrl,
    )
    assertEquals("trusted-issuer", scheme.resolveSecurityBinding(GenerationContext(GenerationMode.Server))!!.provider)
    assertEquals("https://issuer.example/discovery", scheme.openIdConnectUrl)
  }

  @Test
  fun `public projection cannot make an internal-only alternative usable`() {
    val internal =
      bearer.copy(
        name = "internal",
        bindings =
          GeneratedEnvironment(
            all = GeneratedSecurityBinding(provider = "identity"),
            profiles =
              mapOf(
                "internal" to
                  GeneratedEnvironment.Scope(
                    client = GeneratedSecurityBinding(flow = GeneratedSecurityBinding.Flow.EXTERNAL),
                  ),
              ),
          ),
      )
    val original =
      api(listOf(requirement("token"), requirement("internal")), listOf(bearer.copy(bindings = provider), internal))
    val external = GenerationContext(GenerationMode.Client, "external")
    val projected = original.projectEnvironment(external)
    assertEquals(
      requirement("token"),
      projected.clientSecurity(projected.services.single(), operation, external)!!.requirement,
    )
    assertEquals(
      internal.bindings,
      projected.auth!!
        .securitySchemes
        .single { it.name == "internal" }
        .bindings,
    )
    assertEquals(original.auth!!.requirements, projected.auth.requirements)
  }

  private fun requirement(vararg schemes: String) = GeneratedSecurityRequirement(schemes.toList())

  private fun api(
    requirements: List<GeneratedSecurityRequirement>,
    schemes: List<GeneratedSecurityScheme>,
  ) = GeneratedApi(
    name = "Security",
    source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://security"),
    services = listOf(service),
    auth = GeneratedAuth(requirements = requirements, securitySchemes = schemes),
  )
}
