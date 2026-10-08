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

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.jwt.build.Jwt
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.startsWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

@QuarkusTest
@QuarkusTestResource(MockOidcResource::class)
class PackagedSecurityTest {
  @TestHTTPResource lateinit var baseUri: URI

  @Inject lateinit var relationships: TestRelationships

  @Inject lateinit var protectedCalls: ProtectedCalls
  private val client = HttpClient.newHttpClient()

  @Test
  fun `library properties load at default ordinal and retain application environment and system overrides`() {
    expectThat(request("/native/unannotated").statusCode()).isEqualTo(200)
    val config = ConfigProvider.getConfig().unwrap(io.smallrye.config.SmallRyeConfig::class.java)
    val prefix = "quarkus.rest-client.\"io.test.packaged.client.API\"."
    expectThat(config.getValue(prefix + "connect-timeout", String::class.java)).isEqualTo("2000")
    expectThat(config.getConfigValue(prefix + "connect-timeout").configSourceOrdinal).isEqualTo(250)
    expectThat(config.getValue(prefix + "read-timeout", String::class.java)).isEqualTo("3000")
    expectThat(config.getConfigValue(prefix + "read-timeout").configSourceOrdinal).isEqualTo(300)
    expectThat(config.getValue(prefix + "max-redirects", String::class.java)).isEqualTo("4")
    expectThat(config.getConfigValue(prefix + "max-redirects").configSourceOrdinal).isEqualTo(400)
    expectThat(
      config.getConfigValue(prefix + "url").configSourceOrdinal,
    ).isEqualTo(100)
    expectThat(config.getValue("quarkus.oidc.token.issuer", String::class.java)).isEqualTo("https://issuer.test")
  }

  @Test
  fun `dependency jars discover shared providers and native OIDC rejects untrusted tokens`() {
    val badKey =
      KeyPairGenerator
        .getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
        .private
    val nativeOnly =
      ConfigProvider
        .getConfig()
        .getOptionalValue(
          "fixture.native-only",
          Boolean::class.java,
        ).orElse(false)
    (if (nativeOnly) listOf("native") else listOf("first", "second", "native")).forEach { service ->
      expectThat(request("/$service/public").statusCode()).isEqualTo(200)
      expectThat(request("/$service/public", "invalid").statusCode()).isEqualTo(if (nativeOnly) 401 else 200)
      expectThat(request("/$service/protected").statusCode()).isEqualTo(401)
      expectThat(request("/$service/protected", "invalid").statusCode()).isEqualTo(401)
      expectThat(request("/$service/protected", token(key = badKey)).statusCode()).isEqualTo(401)
      expectThat(request("/$service/protected", token(issuer = "https://wrong.test")).statusCode()).isEqualTo(401)
      expectThat(request("/$service/protected", token(audience = "wrong")).statusCode()).isEqualTo(401)
      expectThat(
        request("/$service/protected", token(expires = Instant.now().epochSecond - 120)).statusCode(),
      ).isEqualTo(401)
      expectThat(request("/$service/protected", token(scopes = "")).statusCode()).isEqualTo(403)
      val response = request("/$service/protected", token())
      expectThat(response.statusCode()).isEqualTo(200)
      expectThat(response.body()).isEqualTo("alice")
    }
  }

  @Test
  fun `native client acquires caches and propagates only on selected methods`() {
    expectThat(request("/client/public").body()).isEqualTo("anonymous")
    val service = request("/client/service")
    expectThat(service.statusCode()).isEqualTo(200)
    expectThat(service.body()).startsWith("Bearer service-read-")
    expectThat(request("/client/service").body()).isEqualTo(service.body())
    val write = request("/client/write")
    expectThat(write.statusCode()).isEqualTo(200)
    expectThat(write.body()).startsWith("Bearer service-write-")
    expectThat(request("/client/service").body()).isEqualTo(service.body())
    val incoming = token()
    expectThat(request("/client/user", incoming).body()).isEqualTo("Bearer $incoming")
    expectThat(request("/client/exchange", incoming).body()).startsWith("Bearer exchange-")
    expectThat(request("/client/user").statusCode()).isEqualTo(401)
    expectThat(request("/client/user-without-login").statusCode()).isEqualTo(401)
    expectThat(request("/client/public", incoming).body()).isEqualTo("anonymous")
  }

  @Test
  fun `native renewal happens before expiry and after rejection without replay`() {
    val before = request("/client/service").body()
    Thread.sleep(3500)
    val calls =
      (1..8)
        .map {
          client.sendAsync(
            HttpRequest.newBuilder(baseUri.resolve("/client/service")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
          )
        }.map { it.join().body() }
    expectThat(calls.distinct().size).isEqualTo(1)
    expectThat(calls.first()).isNotEqualTo(before)
    val upstream = URI(ConfigProvider.getConfig().getValue("fixture.idp.url", String::class.java))

    fun rejections(): Int =
      client
        .send(
          HttpRequest.newBuilder(upstream.resolve("/stats")).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
        ).body()
        .toInt()
    val rejected = rejections()
    expectThat(request("/client/reject-get").statusCode()).isEqualTo(401)
    expectThat(rejections()).isEqualTo(rejected + 1)
    val rejectedToken =
      client
        .send(
          HttpRequest.newBuilder(upstream.resolve("/last-rejected")).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
        ).body()
    val renewed = request("/client/reject-get")
    expectThat(renewed.statusCode()).isEqualTo(200)
    expectThat(renewed.body()).isNotEqualTo(rejectedToken)
    expectThat(rejections()).isEqualTo(rejected + 2)
    expectThat(request("/client/reject-post").statusCode()).isEqualTo(401)
    expectThat(rejections()).isEqualTo(rejected + 3)
  }

  @Test
  fun `native browser login verifies PKCE nonce and refreshes before downstream propagation`() {
    org.junit.jupiter.api.Assumptions
      .assumeFalse(System.getProperty("fixture.native-only") == "true")
    val browser =
      HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .cookieHandler(java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
        .build()

    fun visit(): HttpResponse<String> =
      browser.send(
        HttpRequest.newBuilder(baseUri.resolve("/web/protected")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    val first = visit()
    expectThat(first.statusCode()).isEqualTo(200)
    expectThat(first.body()).startsWith("Bearer ey")
    Thread.sleep(5500)
    val refreshed = visit()
    expectThat(refreshed.statusCode()).isEqualTo(200)
    expectThat(refreshed.body()).startsWith("Bearer ey")
    expectThat(refreshed.body()).isNotEqualTo(first.body())
    val upstream = URI(ConfigProvider.getConfig().getValue("fixture.idp.url", String::class.java))
    val statistics =
      client
        .send(
          HttpRequest.newBuilder(upstream.resolve("/browser/stats")).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
        ).body()
    expectThat(statistics).isEqualTo("1:1")
  }

  @Test
  fun `new native and shared bindings enforce authentication before Zanzibar and delegation`() {
    val nativeOnly = System.getProperty("fixture.native-only") == "true"
    (if (nativeOnly) listOf("native") else listOf("first", "native", "composite")).forEach { service ->
      val path = "/$service/documents/allowed"
      val beforeFga = relationships.checks.size
      val beforeCalls = protectedCalls.calls.size
      expectThat(request(path).statusCode()).isEqualTo(401)
      expectThat(request(path, "invalid", "service-secret").statusCode()).isEqualTo(401)
      expectThat(request(path, token(scopes = ""), "service-secret").statusCode()).isEqualTo(403)
      expectThat(relationships.checks.size).isEqualTo(beforeFga)
      expectThat(protectedCalls.calls.size).isEqualTo(beforeCalls)
      val response = request(path, token(), "service-secret")
      expectThat(response.statusCode()).isEqualTo(200)
      expectThat(response.body()).isEqualTo("alice")
      val relationship = relationships.checks.last()
      expectThat(relationship.userId()).isEqualTo("alice")
      expectThat(relationship.userType()).isEqualTo("user")
      expectThat(relationship.objectId()).isEqualTo("allowed")
      expectThat(relationship.objectType()).isEqualTo("document")
      expectThat(relationship.relation()).isEqualTo("viewer")
      expectThat(protectedCalls.calls.last()).isEqualTo(Triple(service, "allowed", "alice"))
      val delegated = protectedCalls.calls.size
      expectThat(request("/$service/documents/denied", token(), "service-secret").statusCode()).isEqualTo(403)
      expectThat(protectedCalls.calls.size).isEqualTo(delegated)
    }
  }

  @Test
  fun `shared composite bindings select subjects isolate concurrent identities and retain alternatives`() {
    org.junit.jupiter.api.Assumptions
      .assumeFalse(System.getProperty("fixture.native-only") == "true")
    expectThat(request("/composite/protected", token()).statusCode()).isEqualTo(401)
    expectThat(request("/composite/protected", null, "service-secret").statusCode()).isEqualTo(401)
    val beforeFga = relationships.checks.size
    val beforeCalls = protectedCalls.calls.size
    expectThat(request("/composite/documents/allowed", token(), "wrong-key").statusCode()).isEqualTo(401)
    expectThat(relationships.checks.size).isEqualTo(beforeFga)
    expectThat(protectedCalls.calls.size).isEqualTo(beforeCalls)
    expectThat(request("/composite/alternative", token()).body()).isEqualTo("alice")
    expectThat(request("/composite/alternative", null, "service-secret").body()).isEqualTo("service-key")
    expectThat(request("/composite/alternative").statusCode()).isEqualTo(401)
    expectThat(request("/composite/public").statusCode()).isEqualTo(200)
    val calls =
      (1..8).map { index ->
        val subject = "user-$index"
        subject to
          client.sendAsync(
            HttpRequest
              .newBuilder(baseUri.resolve("/composite/documents/$subject"))
              .header("Authorization", "Bearer " + token(subject = subject))
              .header("X-Service-Key", "service-secret")
              .GET()
              .build(),
            HttpResponse.BodyHandlers.ofString(),
          )
      }
    calls.forEach { (subject, future) ->
      val response = future.join()
      expectThat(response.statusCode()).isEqualTo(200)
      expectThat(response.body()).isEqualTo(subject)
      expectThat(relationships.checks.any { it.objectId() == subject && it.userId() == subject }).isEqualTo(true)
      expectThat(protectedCalls.calls.contains(Triple("composite", subject, subject))).isEqualTo(true)
    }
  }

  private fun request(
    path: String,
    token: String? = null,
    serviceKey: String? = null,
  ): HttpResponse<String> =
    client.send(
      HttpRequest
        .newBuilder(baseUri.resolve(path))
        .apply {
          if (token != null) header("Authorization", "Bearer $token")
          if (serviceKey != null) header("X-Service-Key", serviceKey)
        }.GET()
        .build(),
      HttpResponse.BodyHandlers.ofString(),
    )

  private fun token(
    subject: String = "alice",
    issuer: String = "https://issuer.test",
    audience: String = "integration",
    scopes: String = "read",
    expires: Long = Instant.now().epochSecond + 300,
    key: PrivateKey = fixtureKey(),
  ): String =
    Jwt
      .issuer(issuer)
      .subject(subject)
      .audience(audience)
      .claim("scope", scopes)
      .expiresAt(expires)
      .sign(key)

  private fun fixtureKey(): PrivateKey {
    // Public test fixture key, never used outside integration tests.
    val pem =
      javaClass
        .getResource("/private-test-key.pem")!!
        .readText()
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace(Regex("\\s"), "")
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
  }
}
