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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.emit.GeneratedClientSecurity
import io.outfoxx.sunday.generator.ir.emit.clientConfigurations
import io.outfoxx.sunday.generator.ir.emit.clientFactoryAlternatives
import io.outfoxx.sunday.generator.ir.emit.clientFactoryProfiles
import io.outfoxx.sunday.generator.ir.emit.projectEnvironment
import io.outfoxx.sunday.generator.ir.emit.requireCompatibleAggregate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClientConfigurationPlanTest {
  @TempDir
  lateinit var directory: Path

  private fun api(
    servers: String,
    operations: String = "",
  ): GeneratedApi {
    val path = directory.resolve("api.yaml")
    Files.writeString(
      path,
      """
      openapi: 3.2.0
      info: {title: Example API, version: '1'}
      servers:
      $servers
      paths:
        /items:
          get:
            operationId: listItems
            responses:
              '200': {description: Success}
      $operations
      """.trimIndent(),
    )
    return OpenApiToGeneratedApi().convert(path.toUri())
  }

  @Test
  fun `named servers preserve variables and security profiles`() {
    val api =
      api(
        """  - name: production
          url: https://{tenant}.example/v1
          x-sunday-security-profile: external
          variables:
            tenant: {default: primary, enum: [primary, secondary]}
        - name: development
          url: https://dev.example/v1""",
      )
    val plans = api.clientConfigurations(api.services)
    assertEquals(listOf("ExampleAPIProductionConfig", "ExampleAPIDevelopmentConfig"), plans.map { it.name })
    assertEquals("external", plans.first().server.securityProfile)
    assertEquals(
      listOf("primary", "secondary"),
      plans
        .first()
        .variables
        .single()
        .allowedValues,
    )
    assertEquals(
      "primary",
      plans
        .first()
        .variables
        .single()
        .defaultValue,
    )
  }

  @Test
  fun `relative local servers require an explicit document base URL`() {
    val api = api("  - url: /api")
    assertTrue(api.clientConfigurations(api.services).single().requiresDocumentBaseUri)
  }

  @Test
  fun `invalid enum defaults fail conversion`() {
    assertThrows(IllegalArgumentException::class.java) {
      api(
        """  - url: https://{tenant}.example
          variables:
            tenant: {default: invalid, enum: [primary]}""",
      )
    }
  }

  @Test
  fun `undefined template variables fail planning`() {
    val api = api("  - url: https://{tenant}.example")
    assertThrows(IllegalArgumentException::class.java) { api.clientConfigurations(api.services) }
  }

  @Test
  fun `one server declaration is shared by services`() {
    val api = api("  - url: https://example.com")
    val services = listOf(api.services.single().copy(name = "First"), api.services.single().copy(name = "Second"))
    val plans = api.clientConfigurations(services)
    assertEquals(1, plans.size)
    assertEquals(listOf("First", "Second"), plans.single().services)
  }

  @Test
  fun `normalized server names cannot collide`() {
    val api =
      api(
        "  - name: one-two\n          url: https://one.example\n        - name: one_two\n          url: https://two.example",
      )
    assertThrows(IllegalArgumentException::class.java) { api.clientConfigurations(api.services) }
  }

  @Test
  fun `aggregate endpoints must agree`() {
    val api = api("  - url: https://one.example")
    val first = api.services.single().copy(name = "First")
    val second = first.copy(name = "Second", servers = listOf(GeneratedServer(url = "https://two.example")))
    val services = listOf(first, second)
    assertThrows(IllegalArgumentException::class.java) {
      api.clientConfigurations(services).requireCompatibleAggregate(services)
    }
  }

  @Test
  fun `path and operation servers override API servers before grouping`() {
    val source = directory.resolve("inherited.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.2.0
      info: {title: Inherited, version: '1'}
      servers: [{url: 'https://root.example'}]
      paths:
        /items:
          servers: [{url: 'https://path.example'}]
          get:
            operationId: read
            x-sunday-service: Read
            responses: {'204': {description: Success}}
          post:
            operationId: write
            x-sunday-service: Write
            servers: [{url: 'https://operation.example'}]
            responses: {'204': {description: Success}}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(
      setOf("https://path.example", "https://operation.example"),
      api.services
        .map {
          it.servers.single().url
        }.toSet(),
    )
    Files.writeString(source, Files.readString(source).replace("x-sunday-service: Write", "x-sunday-service: Read"))
    assertThrows(IllegalArgumentException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
  }

  @Test
  fun `referenced relative servers retain the declaring document location`() {
    val external = directory.resolve("paths.yaml")
    Files.writeString(
      external,
      """
      items:
        servers: [{url: ./v1}]
        get:
          operationId: read
          responses: {'204': {description: Success}}
      """.trimIndent(),
    )
    val source = directory.resolve("root.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.2.0
      info: {title: Referenced, version: '1'}
      paths:
        /items: {${'$'}ref: './paths.yaml#/items'}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(
      external.toUri(),
      java.net.URI(
        api.services
          .single()
          .servers
          .single()
          .sourceUri!!,
      ),
    )
  }

  @Test
  fun `unknown server security profiles fail factory planning`() {
    val api = api("  - url: https://example.com\n          x-sunday-security-profile: missing")
    assertThrows(IllegalArgumentException::class.java) { api.clientFactoryProfiles(api.services.single(), null) }
  }

  @Test
  fun `client projection preserves security profiles without projecting policies to server defaults`() {
    val api = api("  - url: https://example.com")
    val profiles =
      mapOf(
        "production" to GeneratedEnvironment.Scope(client = GeneratedSecurityBinding(provider = "prod")),
        "development" to GeneratedEnvironment.Scope(client = GeneratedSecurityBinding(provider = "dev")),
      )
    val auth =
      GeneratedAuth(
        securitySchemes =
          listOf(
            GeneratedSecurityScheme(
              name = "identity",
              type = "http",
              scheme = "bearer",
              bindings = GeneratedEnvironment(profiles = profiles),
            ),
          ),
      )
    val projected = api.copy(auth = auth).projectEnvironment(GenerationContext(GenerationMode.Client, "production"))
    assertEquals(
      profiles.keys,
      projected.auth!!
        .securitySchemes
        .single()
        .bindings!!
        .profiles.keys,
    )
  }

  @Test
  fun `policy-only generation profiles retain applicable unprofiled security`() {
    val api = api("  - url: https://example.com")
    assertEquals(setOf(null, "policy-only"), api.clientFactoryProfiles(api.services.single(), "policy-only"))
  }

  @Test
  fun `typed alternatives retain scopes and complete conjunctive requirements`() {
    val requirements =
      listOf(
        GeneratedSecurityRequirement(listOf("identity", "key"), mapOf("identity" to listOf("read"))),
        GeneratedSecurityRequirement(listOf("identity", "key"), mapOf("identity" to listOf("write"))),
        GeneratedSecurityRequirement(emptyList()),
      )
    val security = requirements.map { GeneratedClientSecurity(it, emptyMap(), emptyMap()) }
    val choices = listOf(mapOf("operation" to security)).clientFactoryAlternatives()
    assertEquals(3, choices.size)
    assertEquals(listOf("identity", "key"), choices.first().requirement.schemes)
    assertEquals(listOf("read"), choices.first().requirement.permissions["identity"])
    assertEquals(listOf("write"), choices[1].requirement.permissions["identity"])
    assertTrue(
      choices
        .last()
        .requirement.schemes
        .isEmpty(),
    )
    assertEquals(3, choices.map { it.name }.toSet().size)
  }
}
