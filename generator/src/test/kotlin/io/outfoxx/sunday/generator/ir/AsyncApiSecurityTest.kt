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

import com.fasterxml.jackson.databind.ObjectMapper
import io.outfoxx.sunday.generator.GenerationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class AsyncApiSecurityTest {

  @TempDir
  lateinit var directory: Path

  @Test
  fun `3x inline and referenced schemes preserve names scopes and alternative order`() {
    val oauth =
      mapOf(
        "type" to "oauth2",
        "scopes" to listOf("read"),
        "flows" to
          mapOf(
            "clientCredentials" to
              mapOf(
                "tokenUrl" to "https://issuer/token",
                "availableScopes" to mapOf("write" to "Available only"),
              ),
          ),
      )
    val api = convert(document(listOf(ref("#/components/securitySchemes/oauth"), bearer), mapOf("oauth" to oauth)))
    val auth = operationAuth(api)
    assertEquals(
      "oauth",
      auth.requirements
        .first()
        .schemes
        .single(),
    )
    assertEquals(mapOf("oauth" to listOf("read")), auth.requirements.first().permissions)
    assertTrue(
      auth.requirements
        .last()
        .schemes
        .single()
        .startsWith("inline_"),
    )
    assertEquals(
      mapOf("write" to "Available only"),
      auth.securitySchemes
        .first()
        .oauthFlows
        .getValue("clientCredentials")
        .scopes,
    )
    assertNull(api.auth)
  }

  @ParameterizedTest
  @ValueSource(strings = ["2.6.0", "3.0.0", "3.1.0"])
  fun `OAuth flow metadata uses the versioned field independently of required permissions`(version: String) {
    val version3 = version.startsWith("3")
    val field = if (version3) "availableScopes" else "scopes"
    val wrongField = if (version3) "scopes" else "availableScopes"
    val advertised = mapOf("read" to "Read events", "write" to "Write events")
    val flow =
      mapOf(
        "tokenUrl" to "https://issuer/token",
        field to advertised,
        wrongField to mapOf("ignored" to "Wrong version"),
      )
    val definition = mapOf("type" to "oauth2", "flows" to mapOf("clientCredentials" to flow))
    val references = if (version3) listOf(false, true) else listOf(true)
    references.forEach { referenced ->
      listOf(emptyList(), listOf("read")).forEach { required ->
        val scheme = if (version3 && required.isNotEmpty()) definition + ("scopes" to required) else definition
        val entry =
          when {
            !version3 -> mapOf("oauth" to required)
            referenced -> ref("#/components/securitySchemes/oauth")
            else -> scheme
          }
        val api = convert(document(listOf(entry), mapOf("oauth" to scheme), version))
        val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
        assertEquals(api, decoded)
        val auth = if (version3) operationAuth(decoded) else decoded.auth!!
        val name = auth.schemes.single()
        assertEquals(
          required,
          auth.requirements
            .single()
            .permissions[name]
            .orEmpty(),
        )
        assertEquals(
          advertised,
          auth.securitySchemes
            .single()
            .oauthFlows
            .getValue("clientCredentials")
            .scopes,
        )
      }
    }
    val wrongDefinition = definition + ("flows" to mapOf("clientCredentials" to (flow - field)))
    val entry = if (version3) ref("#/components/securitySchemes/oauth") else mapOf("oauth" to emptyList<String>())
    val api = convert(document(listOf(entry), mapOf("oauth" to wrongDefinition), version))
    val auth = if (version3) operationAuth(api) else api.auth!!
    assertTrue(
      auth.securitySchemes
        .single()
        .oauthFlows
        .getValue("clientCredentials")
        .scopes
        .isEmpty(),
    )
  }

  @Test
  fun `advertised scopes survive composition without adding permissions`() {
    val api =
      GeneratedApiIrExporter().export(
        listOf("openapi/ir/security-enforcement.yaml", "asyncapi/ir/security-enforcement-3.yaml")
          .map { javaClass.getResource("/$it")!!.toURI() },
      )
    val auth =
      api.services
        .flatMap { it.operations }
        .single { it.id == "receiveScoped" }
        .auth!!
    assertEquals(mapOf("eventToken" to listOf("read")), auth.requirements.single().permissions)
    assertEquals(
      mapOf("read" to "Read events", "write" to "Write events"),
      auth.securitySchemes
        .single()
        .oauthFlows
        .getValue("clientCredentials")
        .scopes,
    )
  }

  @Test
  fun `local reference chains preserve the requested component name and decode pointer escapes`() {
    val api =
      convert(
        document(
          listOf(ref("#/components/securitySchemes/a~1b~0c")),
          mapOf("a/b~c" to ref("#/components/securitySchemes/target"), "target" to bearer),
        ),
      )
    assertEquals(listOf("a/b~c"), operationAuth(api).schemes)
    assertEquals("bearer", operationAuth(api).securitySchemes.single().scheme)
  }

  @Test
  fun `equivalent inline authentication shares identity independently of required scopes and field ordering`() {
    val flows =
      mapOf(
        "clientCredentials" to
          mapOf(
            "tokenUrl" to "https://issuer/token",
            "availableScopes" to linkedMapOf("read" to "Read events", "write" to "Write events"),
          ),
      )
    val definition = oauth + ("flows" to flows)
    val first = operationAuth(convert(document(listOf(definition + ("scopes" to listOf("read"))))))
    val second =
      operationAuth(
        convert(
          document(
            listOf(
              linkedMapOf(
                "scopes" to listOf("write"),
                "flows" to
                  mapOf(
                    "clientCredentials" to
                      linkedMapOf(
                        "availableScopes" to linkedMapOf("write" to "Write events", "read" to "Read events"),
                        "tokenUrl" to "https://issuer/token",
                      ),
                  ),
                "type" to "oauth2",
              ),
            ),
          ),
        ),
      )
    assertEquals(first.schemes, second.schemes)
    assertEquals(first.securitySchemes, second.securitySchemes)
    assertEquals(
      listOf("write"),
      second.requirements
        .single()
        .permissions
        .getValue(second.schemes.single()),
    )
    val named =
      operationAuth(
        convert(
          document(
            listOf(ref("#/components/securitySchemes/token")),
            mapOf(
              "token" to bearer,
            ),
          ),
        ),
      )
    assertEquals(listOf("token"), named.schemes)
  }

  @Test
  fun `inline scheme names cannot collide with named bindings`() {
    val name = operationAuth(convert(document(listOf(bearer)))).schemes.single()
    failure(document(listOf(bearer), mapOf(name to bearer)), "name collision")
  }

  @Test
  fun `server and operation alternatives form ordered conjunctions with merged permissions`() {
    val schemes = mapOf("serverA" to bearer, "serverB" to bearer, "operationA" to bearer, "operationB" to bearer)
    val doc =
      document(
        listOf(ref("#/components/securitySchemes/operationA"), ref("#/components/securitySchemes/operationB")),
        schemes,
      ) +
        (
          "servers" to
            mapOf(
              "main" to
                mapOf(
                  "host" to "example.com",
                  "protocol" to "http",
                  "security" to
                    listOf(
                      ref("#/components/securitySchemes/serverA"),
                      ref("#/components/securitySchemes/serverB"),
                    ),
                ),
            )
        )
    assertEquals(
      listOf(
        listOf("serverA", "operationA"),
        listOf("serverA", "operationB"),
        listOf("serverB", "operationA"),
        listOf("serverB", "operationB"),
      ),
      operationAuth(convert(doc)).requirements.map { it.schemes },
    )
    val repeated =
      document(listOf(oauth + ("scopes" to listOf("write")))) +
        (
          "servers" to
            mapOf("main" to mapOf("protocol" to "http", "security" to listOf(oauth + ("scopes" to listOf("read")))))
        )
    val auth = operationAuth(convert(repeated))
    assertEquals(
      listOf("read", "write"),
      auth.requirements
        .single()
        .permissions
        .getValue(auth.schemes.single()),
    )
  }

  @Test
  fun `channel server selection excludes unrelated policies and public servers remain alternatives`() {
    val secured = mapOf("protocol" to "http", "security" to listOf(bearer))
    val public = mapOf("protocol" to "http")
    val doc = document(emptyList()) + ("servers" to mapOf("secured" to secured, "public" to public))
    val all = operationAuth(convert(doc))
    assertEquals(listOf(1, 0), all.requirements.map { it.schemes.size })
    val channels = mapOf("events" to (channel + ("servers" to listOf(ref("#/servers/public")))))
    val selected = convert(doc + ("channels" to channels))
    assertNull(
      selected.services
        .single()
        .operations
        .single()
        .auth,
    )
    assertNull(selected.auth)
  }

  @Test
  fun `empty and omitted channel server lists both inherit all server requirements`() {
    val secured = mapOf("protocol" to "http", "security" to listOf(ref("#/components/securitySchemes/token")))
    val doc = document(emptyList(), mapOf("token" to bearer)) + ("servers" to mapOf("main" to secured))
    val implicit = operationAuth(convert(doc))
    val explicit =
      operationAuth(
        convert(
          doc + ("channels" to mapOf("events" to (channel + ("servers" to emptyList<Any>())))),
        ),
      )
    assertEquals(listOf("token"), implicit.schemes)
    assertEquals(implicit, explicit)
  }

  @ParameterizedTest
  @ValueSource(strings = ["header", "query", "cookie"])
  fun `HTTP API keys retain exact wire names in 2x and 3x`(transport: String) {
    listOf("2.6.0", "3.0.0", "3.1.0").forEach { version ->
      val scheme = mapOf("type" to "httpApiKey", "name" to "X-API-Key", "in" to transport)
      val security =
        if (version.startsWith(
            "2",
          )
        ) {
          listOf(mapOf("key" to emptyList<String>()))
        } else {
          listOf(ref("#/components/securitySchemes/key"))
        }
      val api = convert(document(security, mapOf("key" to scheme), version))
      val definition =
        (
          if (version.startsWith(
              "2",
            )
          ) {
            api.protocol!!
              .servers
              .single()
              .auth!!
          } else {
            operationAuth(api)
          }
        ).securitySchemes.single()
      val parameter = (definition.headers + definition.queryParameters + definition.cookieParameters).single()
      assertEquals("X-API-Key", parameter.serializationName)
      assertEquals(transport.uppercase(), parameter.location.name)
      assertTrue(parameter.required)
    }
  }

  @Test
  fun `2x named requirements keep AND groups and permissions`() {
    val doc =
      document(
        listOf(mapOf("token" to listOf("read"), "other" to emptyList<String>())),
        mapOf(
          "token" to bearer,
          "other" to bearer,
        ),
        "2.6.0",
      )
    val auth =
      convert(doc)
        .protocol!!
        .servers
        .single()
        .auth!!
    assertEquals(listOf("token", "other"), auth.requirements.single().schemes)
    assertEquals(mapOf("token" to listOf("read")), auth.requirements.single().permissions)
  }

  @Test
  fun `invalid references report the source and security location`() {
    listOf(
      ref("other.yaml#/components/securitySchemes/token") to "External security references",
      ref("https://example.com/security.yaml#/token") to "External security references",
      ref("#/components/securitySchemes/missing") to "Missing security reference target",
      ref("#/info/title") to "Invalid security reference target",
      ref("#/components/securitySchemes/token") to "Cyclic security reference",
      ref("#/info") to "must declare its type",
      ref("#/components/securitySchemes/bad~2escape") to "Invalid JSON Pointer escape",
    ).forEach { (entry, expected) ->
      failure(document(listOf(entry), mapOf("token" to ref("#/components/securitySchemes/token"))), expected)
    }
  }

  @Test
  fun `invalid permission and API key metadata fail with useful diagnostics`() {
    failure(document(listOf(oauth + ("scopes" to "read"))), "permission list")
    failure(document(listOf(oauth + ("scopes" to null))), "permission list")
    failure(document(listOf(oauth + ("scopes" to listOf(42)))), "must be strings")
    failure(document(listOf(mapOf("token" to "read")), mapOf("token" to bearer), "2.6.0"), "permission list")
    failure(document(listOf(mapOf("type" to "httpApiKey", "in" to "header"))), "parameter name")
    failure(
      document(listOf(mapOf("type" to "httpApiKey", "name" to "key", "in" to "user"))),
      "header, query, or cookie",
    )
  }

  @Test
  fun `malformed security arrays cannot silently disable authentication`() {
    listOf(null, "token", emptyMap<String, Any>(), listOf(null), listOf("token")).forEach { invalid ->
      val doc = document(emptyList())
      val operations = doc.getValue("operations") as Map<*, *>
      val operation = operations["publish"] as Map<*, *>
      failure(doc + ("operations" to mapOf("publish" to (operation + ("security" to invalid)))), "Security")
      failure(doc + ("servers" to mapOf("main" to mapOf("protocol" to "http", "security" to invalid))), "Security")
    }
  }

  private fun failure(
    document: Map<String, Any?>,
    expected: String,
  ) {
    val error = assertThrows(GenerationException::class.java) { convert(document) }
    assertTrue(error.message.orEmpty().contains(expected), error.toString())
    assertTrue(error.message.orEmpty().contains("#/"), error.toString())
    assertTrue(error.file.endsWith("security.json"), error.toString())
  }

  private fun operationAuth(api: GeneratedApi): GeneratedAuth =
    api.services
      .single()
      .operations
      .single()
      .auth!!

  private fun convert(document: Map<String, Any?>): GeneratedApi {
    val file = directory.resolve("security.json")
    file.writeText(ObjectMapper().writeValueAsString(document))
    return AsyncApiToGeneratedApi().convertFragment(file.toUri()).api
  }

  private fun document(
    security: List<Map<String, Any?>>,
    schemes: Map<String, Any?> = emptyMap(),
    version: String = "3.0.0",
  ): Map<String, Any?> =
    mapOf(
      "asyncapi" to version,
      "info" to mapOf("title" to "Security", "version" to "1"),
      "components" to mapOf("securitySchemes" to schemes),
    ) +
      if (version.startsWith("2")) {
        mapOf(
          "servers" to
            mapOf("main" to mapOf("url" to "https://example.com", "protocol" to "http", "security" to security)),
        )
      } else {
        mapOf(
          "channels" to mapOf("events" to channel),
          "operations" to
            mapOf(
              "publish" to
                mapOf(
                  "action" to "send",
                  "channel" to ref("#/channels/events"),
                  "messages" to listOf(ref("#/channels/events/messages/event")),
                  "security" to security,
                ),
            ),
        )
      }

  private val oauth = mapOf("type" to "oauth2", "flows" to emptyMap<String, Any?>())

  private val bearer = mapOf("type" to "http", "scheme" to "bearer")
  private val channel =
    mapOf(
      "address" to "/events",
      "messages" to mapOf("event" to mapOf("payload" to mapOf("type" to "string"))),
    )

  private fun ref(value: String): Map<String, Any?> = mapOf("\$ref" to value)
}
