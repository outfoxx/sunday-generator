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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.emit.clientSecurity
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.ir.emit.endpointSecurityPolicy
import io.outfoxx.sunday.generator.ir.emit.projectEnvironment
import io.outfoxx.sunday.generator.ir.emit.resolveSecurityBinding
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class ScopedSecurityIrTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `preserves role and profile bindings through source conversion IR1 and public projection`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    assertEquals("1", api.irVersion)
    if (frontend == "asyncapi3") {
      assertEquals(
        "https://events.example",
        api.protocol!!
          .servers
          .single()
          .url,
      )
    }
    api.services.forEach { service ->
      service.operations.forEach { operation ->
        val internal = api.clientSecurity(service, operation, GenerationContext(GenerationMode.Client, "internal"))!!
        val external = api.clientSecurity(service, operation, GenerationContext(GenerationMode.Client, "external"))!!
        assertEquals(listOf("token"), internal.requirement.schemes)
        assertEquals(listOf("items:read"), internal.requirement.permissions["token"])
        assertEquals("https://identity.internal/token", internal.bindings.getValue("token").tokenUrl)
        assertEquals("application", external.bindings.getValue("token").provider)
        assertEquals("https://identity.example/token", external.bindings.getValue("token").tokenUrl)
        assertEquals(GeneratedSecurityBinding.Flow.AUTHORIZATION_CODE, external.bindings.getValue("token").flow)
        assertEquals(
          "verifier",
          internal.schemes
            .getValue("token")
            .resolveSecurityBinding(GenerationContext(GenerationMode.Server))!!
            .provider,
        )
        assertThrows(GenerationException::class.java) {
          api.clientSecurity(service, operation, GenerationContext(GenerationMode.Client))
        }
        assertThrows(GenerationException::class.java) {
          api.clientSecurity(service, operation, GenerationContext(GenerationMode.Client, "typo"))
        }
      }
    }
    val publicApi = api.projectEnvironment(GenerationContext(GenerationMode.Client, "external"))
    val sourceUris = Files.list(directory).use { files -> files.sorted().map { it.toUri() }.toList() }
    val exported =
      GeneratedApiIrExporter(GeneratedApiIrOptions(projection = GenerationContext(GenerationMode.Client, "external")))
        .export(sourceUris)
    assertEquals(publicApi, exported)
    val serialized = GeneratedApiYaml.writeString(publicApi)
    assertFalse(serialized.contains("identity.internal"))
    assertFalse(serialized.contains("verifier"))
    assertFalse(serialized.contains("internal:"))
    assertEquals(publicApi, GeneratedApiYaml.readString(serialized))
    api.services.zip(publicApi.services).forEach { (original, projected) ->
      original.operations.zip(projected.operations).forEach { (before, after) ->
        assertEquals(
          api.effectiveAuth(original, before)!!.requirements,
          publicApi.effectiveAuth(projected, after)!!.requirements,
        )
        assertEquals(
          "application",
          publicApi
            .clientSecurity(
              projected,
              after,
              GenerationContext(GenerationMode.Client, "external"),
            )!!
            .bindings
            .getValue("token")
            .provider,
        )
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `server profiles select validators without rewriting wire requirements`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, profiledServer = true)
    api.services.forEach { service ->
      service.operations.forEach { operation ->
        for (profile in listOf("internal", "external")) {
          val policy =
            api.endpointSecurityPolicy(
              service,
              operation,
              GenerationContext(GenerationMode.Server, profile),
            )!!
          assertEquals(mapOf("token" to "${profile}Verifier"), policy.providers)
          assertEquals(listOf("token"), policy.requirements.single().schemes)
          assertEquals(listOf("items:read"), policy.requirements.single().permissions["token"])
        }
        assertThrows(GenerationException::class.java) { api.endpointSecurityPolicy(service, operation) }
        assertThrows(GenerationException::class.java) {
          api.endpointSecurityPolicy(service, operation, GenerationContext(GenerationMode.Server, "missing"))
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `endpoint bindings preserve independently configured providers through composition and projection`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, endpointBindings = true)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    api.services.forEach { service ->
      service.operations.forEach { operation ->
        val selected = api.clientSecurity(service, operation, GenerationContext(GenerationMode.Client, "internal"))!!
        val provider = if (operation.id == "events") "eventsProvider" else "apiProvider"
        assertEquals(provider, selected.bindings.getValue("token").provider)
        assertEquals("https://identity.internal/token", selected.bindings.getValue("token").tokenUrl)
        assertEquals("verifier", api.endpointSecurityPolicy(service, operation)!!.providers.getValue("token"))
      }
    }
    val projected = api.projectEnvironment(GenerationContext(GenerationMode.Client, "external"))
    val serialized = GeneratedApiYaml.writeString(projected)
    assertFalse(serialized.contains("apiProvider"))
    assertFalse(serialized.contains("eventsProvider"))
    assertFalse(serialized.contains("identity.internal"))
    projected.services.forEach { service ->
      service.operations.forEach { operation ->
        assertEquals(
          "application",
          projected
            .clientSecurity(
              service,
              operation,
              GenerationContext(GenerationMode.Client, "external"),
            )!!
            .bindings
            .getValue("token")
            .provider,
        )
      }
    }
  }

  @Test
  fun `selection preserves complete alternatives and explicit public overrides`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("security.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Selection, version: 1.0.0}
      security: [{token: [read], key: []}, {secondary: []}]
      x-sunday-security:
        profiles:
          external: {client: {alternative: {token: [read], key: []}}}
      components:
        securitySchemes:
          token:
            type: http
            scheme: bearer
            x-sunday-security: {client: {provider: token}}
          key:
            type: apiKey
            in: header
            name: X-API-Key
            x-sunday-security: {client: {provider: key, flow: static}}
          secondary:
            type: http
            scheme: bearer
            x-sunday-security: {client: {provider: secondary}}
      paths:
        /items:
          get:
            operationId: fetch
            responses: {'204': {description: OK}}
          post:
            operationId: other
            x-sunday-security: {all: {alternative: {secondary: []}}}
            responses: {'204': {description: OK}}
        /public:
          get:
            operationId: public
            security: []
            responses: {'204': {description: OK}}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val context = GenerationContext(GenerationMode.Client, "external")
    api.services.forEach { service ->
      service.operations.forEach { operation ->
        val selection = api.clientSecurity(service, operation, context)
        when (operation.id) {
          "fetch" -> {
            assertEquals(listOf("token", "key"), selection!!.requirement.schemes)
            assertEquals(listOf("read"), selection.requirement.permissions["token"])
            assertEquals(setOf("token", "key"), selection.bindings.keys)
          }
          "other" -> assertEquals(listOf("secondary"), selection!!.requirement.schemes)
          "public" -> assertNull(selection)
          else -> fail<Unit>("Unexpected operation ${operation.id}")
        }
      }
    }
  }

  @Test
  fun `strictly parses bindings and preserves explicit anonymous selection`() {
    for (invalid in listOf(
      mapOf("client" to mapOf("provider" to "")),
      mapOf("client" to mapOf("clientSecret" to "never-allowed")),
      mapOf("profiles" to mapOf("internal" to mapOf("client" to mapOf("flow" to "password")))),
      mapOf("client" to mapOf("tokenUrl" to "https://user:secret@example/token")),
      mapOf("client" to mapOf("tokenUrl" to "relative/token")),
      mapOf("client" to mapOf("tokenUrl" to null)),
    )) {
      assertThrows(GenerationException::class.java) { GeneratedSecurityReader.binding(invalid, "security") }
    }
    val selection =
      GeneratedSecurityReader.selection(
        mapOf("client" to mapOf("alternative" to emptyMap<String, Any>())),
        "security",
      )
    val api =
      GeneratedApi(
        "1",
        "Security",
        GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://test"),
        auth = GeneratedAuth(selection = selection),
      )
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
  }

  @Test
  fun `referenced scheme bindings retain the logical requirement name`(
    @TempDir directory: Path,
  ) {
    directory.resolve("security.yaml").writeText(
      """
      token:
        type: http
        scheme: bearer
        x-sunday-security:
          client: {provider: credentials}
      """.trimIndent(),
    )
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Security, version: 1.0.0}
      security: [{alias: []}]
      components:
        securitySchemes:
          alias: {${'$'}ref: './security.yaml#/token'}
      paths:
        /items:
          get:
            responses: {'204': {description: OK}}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val service = api.services.single()
    val selected = api.clientSecurity(service, service.operations.single(), GenerationContext(GenerationMode.Client))!!
    assertEquals(listOf("alias"), selected.requirement.schemes)
    assertEquals("credentials", selected.bindings.getValue("alias").provider)
  }

  @Test
  fun `composition isolates defaults and rejects conflicting provider definitions`() {
    val scheme =
      GeneratedSecurityScheme(
        "token",
        "http",
        scheme = "bearer",
        bindings = GeneratedEnvironment(client = GeneratedSecurityBinding(provider = "credentials")),
      )
    val protected =
      GeneratedAuth(
        requirements = listOf(GeneratedSecurityRequirement(listOf("token"))),
        securitySchemes = listOf(scheme),
      )

    fun fragment(
      name: String,
      auth: GeneratedAuth?,
    ) = GeneratedApiFragment(
      apiId = GeneratedIdentity.explicit("security"),
      api =
        GeneratedApi(
          name = "Security",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://$name"),
          auth = auth,
          services = listOf(GeneratedService("Items", operations = listOf(GeneratedOperation(name, "GET", "/$name")))),
        ),
    )
    val first = fragment("protected", protected)
    val second = fragment("public", null)
    for (fragments in listOf(listOf(first, second), listOf(second, first))) {
      val api = GeneratedApiComposer().compose(fragments)
      val service = api.services.single()
      assertEquals(protected, api.effectiveAuth(service, service.operations.single { it.id == "protected" }))
      assertEquals(
        GeneratedAuth(securityOverride = true),
        api.effectiveAuth(
          service,
          service.operations.single {
            it.id ==
              "public"
          },
        ),
      )
    }
    val conflict =
      protected.copy(
        securitySchemes =
          listOf(
            scheme.copy(bindings = GeneratedEnvironment(client = GeneratedSecurityBinding(provider = "other"))),
          ),
      )
    assertThrows(GeneratedApiCompositionException::class.java) {
      GeneratedApiComposer().compose(listOf(first, fragment("conflict", conflict)))
    }
  }
}
