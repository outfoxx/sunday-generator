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

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.jwt.build.Jwt
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID

@QuarkusTest
class NativeSecurityTest {
  @TestHTTPResource lateinit var baseUri: URI

  @Inject lateinit var relationships: TestRelationships

  @Inject lateinit var bindings: NativeSecurityBindings

  @Inject lateinit var delegate: Documents
  private val client = HttpClient.newHttpClient()

  @Test
  fun `authentication and permissions precede Zanzibar and preserve the native JWT identity`() {
    val beforeFga = relationships.checks.size
    val beforeDelegate = delegate.calls.get()
    assertEquals(401, request("allowed", "read").statusCode())
    assertEquals(401, request("allowed", "read", "invalid").statusCode())
    assertEquals(403, request("allowed", "read", token("alice", "")).statusCode())
    assertEquals(beforeFga, relationships.checks.size)
    assertEquals(beforeDelegate, delegate.calls.get())

    val requestId = UUID.randomUUID().toString()
    val response = request("allowed", "read", token("alice", "read"), requestId)
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("allowed:subject-alice:jwt", response.body())
    val relationship = relationships.checks.last()
    assertEquals("alice", relationship.userId())
    assertEquals("user", relationship.userType())
    assertEquals("document", relationship.objectType())
    assertEquals("allowed", relationship.objectId())
    assertEquals("viewer", relationship.relation())
    assertEquals(1, bindings.authentications.getValue("$requestId:jwt").get())
    assertEquals(1, bindings.permissionReads.getValue(requestId).get())
    assertEquals(1, bindings.authorizations.getValue(requestId).get())
    assertTrue(!bindings.authenticatedChecks.containsKey(requestId))

    val calls = delegate.calls.get()
    assertEquals(403, request("denied", "read", token("alice", "read")).statusCode())
    assertEquals(calls, delegate.calls.get())
    assertEquals(403, request("allowed", "write", token("alice", "read")).statusCode())
    assertEquals(200, request("allowed", "write", token("alice", "write")).statusCode())
  }

  @Test
  fun `composite authentication selects the configured subject and isolates concurrent requests`() {
    val requests =
      (1..8).map { index ->
        val id = "parallel-${UUID.randomUUID()}"
        val user = "user-$index"
        Triple(
          id,
          user,
          client.sendAsync(
            builder("allowed", "combined", token(user, "read"), id)
              .header("X-Tenant-Key", "tenant-secret")
              .build(),
            HttpResponse.BodyHandlers.ofString(),
          ),
        )
      }
    requests.forEach { (id, user, future) ->
      val response = future.join()
      assertEquals(200, response.statusCode(), response.body())
      assertEquals("allowed:subject-$user:jwt", response.body())
      assertEquals(1, bindings.authentications.getValue("$id:jwt").get())
      assertEquals(1, bindings.authentications.getValue("$id:tenantKey").get())
      assertEquals(1, bindings.permissionReads.getValue(id).get())
      assertEquals(1, bindings.authorizations.getValue(id).get())
      assertTrue(!bindings.authenticatedChecks.containsKey(id))
      assertTrue(relationships.checks.any { it.userId() == user })
    }
    val before = relationships.checks.size
    assertEquals(401, request("allowed", "combined", token("alice", "read")).statusCode())
    assertEquals(before, relationships.checks.size)
  }

  @ParameterizedTest
  @ValueSource(strings = ["provider-failure", "provider-throw"])
  fun `unused provider failures cannot override the first authorized alternative`(failure: String) {
    val id = UUID.randomUUID().toString()
    val beforeFga = relationships.checks.size
    val response =
      client.send(
        builder("allowed", "alternative", token("alice", "read"), id).header("X-Tenant-Key", failure).build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, response.statusCode(), response.body())
    assertEquals("allowed:subject-alice:jwt", response.body())
    assertEquals(beforeFga + 1, relationships.checks.size)
    assertEquals(1, bindings.authentications.getValue("$id:jwt").get())
    assertEquals(1, bindings.authentications.getValue("$id:tenantKey").get())
    assertEquals(1, bindings.permissionReads.getValue(id).get())
    assertEquals(1, bindings.authorizations.getValue(id).get())
    assertTrue(!bindings.authenticatedChecks.containsKey(id))

    val reversedId = UUID.randomUUID().toString()
    val reversed =
      client.send(
        builder("allowed", "key-first", failure, reversedId).header("X-Tenant-Key", "tenant-secret").build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, reversed.statusCode(), reversed.body())
    assertEquals("tenant-service", reversed.body())
    assertEquals(1, bindings.authentications.getValue("$reversedId:jwt").get())
    assertEquals(1, bindings.authentications.getValue("$reversedId:tenantKey").get())
    assertTrue(!bindings.permissionReads.containsKey(reversedId))
    assertEquals(1, bindings.authorizations.getValue(reversedId).get())
  }

  @ParameterizedTest
  @ValueSource(strings = ["provider-failure", "provider-throw"])
  fun `needed provider failures still fail before Zanzibar and delegation`(failure: String) {
    val beforeFga = relationships.checks.size
    val beforeDelegate = delegate.calls.get()
    listOf(
      "alternative" to token("alice", ""),
      "combined" to token("alice", "read"),
      "key-first" to token("alice", "read"),
      "alternative" to null,
    ).forEach { (operation, jwt) ->
      val response =
        client.send(
          builder("allowed", operation, jwt, UUID.randomUUID().toString()).header("X-Tenant-Key", failure).build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      assertEquals(500, response.statusCode(), response.body())
    }
    assertEquals(500, request("allowed", "simple", failure).statusCode())
    assertEquals(beforeFga, relationships.checks.size)
    assertEquals(beforeDelegate, delegate.calls.get())
  }

  @Test
  fun `OR failure handling preserves credential rejection and permission denials`() {
    val beforeFga = relationships.checks.size
    val beforeDelegate = delegate.calls.get()
    assertEquals(401, request("allowed", "alternative").statusCode())
    assertEquals(401, request("allowed", "alternative", "invalid").statusCode())
    assertEquals(403, request("allowed", "alternative", token("alice", "")).statusCode())
    val ambiguous =
      client.send(
        builder("allowed", "combined", token("alice", "read"), UUID.randomUUID().toString())
          .header("X-Tenant-Key", "tenant-secret")
          .header("X-Tenant-Key", "another-secret")
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(401, ambiguous.statusCode())
    assertEquals(beforeFga, relationships.checks.size)
    assertEquals(beforeDelegate, delegate.calls.get())

    val fallback =
      client.send(
        builder("allowed", "key-first", token("alice", "read"), UUID.randomUUID().toString())
          .header("X-Tenant-Key", "invalid")
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, fallback.statusCode(), fallback.body())
  }

  @Test
  fun `simple authentication skips permission evaluation and public security does not bypass FGA`() {
    val id = UUID.randomUUID().toString()
    assertEquals(200, request("allowed", "simple", token("alice", ""), id).statusCode())
    assertEquals(1, bindings.authentications.getValue("$id:jwt").get())
    assertTrue(!bindings.permissionReads.containsKey(id))
    // Quarkus also emits a success event for its empty HTTP-policy phase before this built-in gate.
    assertEquals(1, bindings.authenticatedChecks.getValue(id).get())
    assertEquals(403, request("allowed", "public").statusCode())
    assertEquals(200, request("allowed", "public", token("alice", "")).statusCode())
    val before = relationships.checks.size
    assertEquals(200, request("allowed", "ignored").statusCode())
    assertEquals(before, relationships.checks.size)
  }

  @Test
  fun `conflicting early HTTP authentication fails before Zanzibar and delegation`() {
    val beforeFga = relationships.checks.size
    val beforeDelegate = delegate.calls.get()
    val credentials = Base64.getEncoder().encodeToString("alice:test-password".toByteArray())
    val request =
      HttpRequest
        .newBuilder(baseUri.resolve("/documents/early/simple"))
        .header("Authorization", "Basic $credentials")
        .GET()
        .build()
    assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
    assertEquals(beforeFga, relationships.checks.size)
    assertEquals(beforeDelegate, delegate.calls.get())
  }

  private fun request(
    document: String,
    operation: String,
    token: String? = null,
    id: String = UUID.randomUUID().toString(),
  ) = client.send(builder(document, operation, token, id).build(), HttpResponse.BodyHandlers.ofString())

  private fun builder(
    document: String,
    operation: String,
    token: String?,
    id: String,
  ): HttpRequest.Builder =
    HttpRequest.newBuilder(baseUri.resolve("/documents/$document/$operation")).GET().header("X-Request-ID", id).apply {
      token?.let { header("Authorization", "Bearer $it") }
    }

  private fun token(
    user: String,
    scopes: String,
  ): String {
    // This private key is a public test fixture and is never used outside these integration tests.
    val pem =
      javaClass
        .getResource("/private-test-key.pem")!!
        .readText()
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace(Regex("\\s"), "")
    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
    return Jwt
      .issuer("https://issuer.test")
      .subject("subject-$user")
      .claim("preferred_username", user)
      .claim("scope", scopes)
      .groups(setOf("user"))
      .sign(key)
  }
}
