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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.OpenApiLoadedDocument
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class GeneratedEndpointPolicyTest {

  @Test
  fun `RAML and AsyncAPI permission requirements survive conversion`() {
    val raml = export("raml/ir/security-enforcement.raml")
    assertEquals(
      mapOf("oauth" to listOf("read")),
      policy(raml, "scoped")!!.requirements.single().permissions,
    )
    val asyncapi = export("asyncapi/ir/security-enforcement.yaml")
    assertEquals(
      mapOf("eventToken" to listOf("read")),
      policy(asyncapi, "streamEvents")!!.requirements.single().permissions,
    )
    assertEquals(
      "https://issuer.example/token",
      policy(
        asyncapi,
        "streamEvents",
      )!!.schemes.getValue("eventToken").oauthFlows.getValue("clientCredentials").tokenUrl,
    )
  }

  @Test
  fun `preserves named permissions and discovery metadata through composition and IR round trips`() {
    val api = export("openapi/ir/security-enforcement.yaml", "asyncapi/ir/security-enforcement.yaml")
    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    assertEquals(api, decoded)
    val combined = policy(decoded, "combined")!!
    assertEquals(listOf("oauth", "headerKey"), combined.requirements.single().schemes)
    assertEquals(mapOf("oauth" to listOf("read")), combined.requirements.single().permissions)
    val flow =
      combined.schemes
        .getValue("oauth")
        .oauthFlows
        .getValue("clientCredentials")
    assertEquals("https://issuer.example/token", flow.tokenUrl)
    assertEquals("https://issuer.example/refresh", flow.refreshUrl)
    assertEquals(mapOf("read" to "Read resources", "admin" to "Administer resources"), flow.scopes)
    assertEquals(
      "https://issuer.example/.well-known/openid-configuration",
      policy(decoded, "oidc")!!.schemes.getValue("oidc").openIdConnectUrl,
    )
    assertEquals(2, policy(decoded, "alternative")!!.requirements.size)
    assertEquals(listOf("admin"), policy(decoded, "role")!!.requirements.single().permissions["headerKey"])
    assertEquals(listOf("bearerAuth"), policy(decoded, "inherited")!!.requirements.single().schemes)
    assertTrue(policy(decoded, "anonymous")!!.requirements.isEmpty())
    assertTrue(policy(decoded, "optional")!!.requirements.isEmpty())
    val service = decoded.services.first()
    assertNull(
      decoded
        .copy(
          auth = null,
        ).endpointSecurityPolicy(service.copy(auth = null), service.operations.first().copy(auth = null)),
    )
  }

  @Test
  fun `undefined and unsupported schemes cannot produce an enforceable endpoint`() {
    val api = export("openapi/ir/security-enforcement.yaml")
    val service = api.services.first()
    val operation = service.operations.first()
    assertThrows(GenerationException::class.java) {
      api.endpointSecurityPolicy(
        service,
        operation.copy(auth = GeneratedAuth(requirements = listOf(GeneratedSecurityRequirement(listOf("missing"))))),
      )
    }
    val auth = api.effectiveAuth(service, operation)!!
    assertThrows(GenerationException::class.java) {
      api.endpointSecurityPolicy(
        service,
        operation.copy(auth = auth.copy(securitySchemes = auth.securitySchemes.map { it.copy(type = "unknown") })),
      )
    }
    assertThrows(GenerationException::class.java) {
      listOf(
        policy(api, "inherited")!!,
        policy(api, "inherited")!!.copy(
          schemes = mapOf("bearerAuth" to auth.securitySchemes.single().copy(scheme = "basic")),
        ),
      ).endpointSecuritySchemes()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["null", "{}", "[null]", "[bearerAuth]", "[{bearerAuth: read}]", "[{bearerAuth: [1]}]"])
  fun `malformed root and operation security cannot become public`(
    value: String,
    @TempDir directory: Path,
  ) {
    val source = fixture()
    listOf(
      source.replace("security:\n  - bearerAuth: []", "security: $value"),
      source.replace("security: [{basicAuth: []}]", "security: $value"),
    ).forEach { document ->
      val path = directory.resolve("security.yaml")
      Files.writeString(path, document)
      assertThrows(GenerationException::class.java) { exporter().export(path.toUri()) }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["in: invalid", "name: ''"])
  fun `API key parameter metadata must be explicit`(
    invalid: String,
    @TempDir directory: Path,
  ) {
    val path = directory.resolve("security.yaml")
    val document = fixture().replace(if (invalid.startsWith("in:")) "in: header" else "name: X-API-Key", invalid)
    Files.writeString(path, document)
    assertThrows(GenerationException::class.java) { exporter().export(path.toUri()) }
  }

  @Test
  fun `security references retain the binding name and resolve external JSON pointers`(
    @TempDir directory: Path,
  ) {
    Files.writeString(
      directory.resolve("schemes.yaml"),
      """
      definitions:
        token/name:
          type: http
          scheme: bearer
          bearerFormat: JWT
      """.trimIndent(),
    )
    val path = directory.resolve("api.yaml")
    Files.writeString(
      path,
      fixture().replace(
        "type: http\n      scheme: bearer\n      bearerFormat: JWT",
        "\$ref: 'schemes.yaml#/definitions/token~1name'",
      ),
    )
    val api = exporter().export(path.toUri())
    val scheme = policy(api, "inherited")!!.schemes.getValue("bearerAuth")
    assertEquals("bearerAuth", scheme.name)
    assertEquals("http", scheme.type)
    assertEquals("bearer", scheme.scheme)
    assertEquals("JWT", scheme.bearerFormat)

    val documents =
      listOf(path, directory.resolve("schemes.yaml")).associate { source ->
        source.toUri() to OpenApiLoadedDocument(source.toUri(), Files.readAllBytes(source))
      }
    Files.delete(directory.resolve("schemes.yaml"))
    val capturedApi = OpenApiToGeneratedApi().convert(path.toUri()) { uri -> documents.getValue(uri) }
    assertEquals(scheme, policy(capturedApi, "inherited")!!.schemes.getValue("bearerAuth"))

    Files.writeString(
      directory.resolve("schemes.yaml"),
      "definitions:\n  token/name:\n    \$ref: '#/definitions/token~1name'",
    )
    assertThrows(GenerationException::class.java) { exporter().export(path.toUri()) }
  }

  private fun exporter() = GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))

  private fun export(vararg paths: String): GeneratedApi =
    exporter().export(paths.map { javaClass.getResource("/$it")!!.toURI() })

  private fun fixture(): String = javaClass.getResource("/openapi/ir/security-enforcement.yaml")!!.readText()

  private fun policy(
    api: GeneratedApi,
    operationId: String,
  ): GeneratedEndpointPolicy? {
    val service = api.services.single { service -> service.operations.any { it.id == operationId } }
    return api.endpointSecurityPolicy(service, service.operations.single { it.id == operationId })
  }
}
