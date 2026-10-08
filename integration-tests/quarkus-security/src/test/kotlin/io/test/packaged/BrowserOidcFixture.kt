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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.smallrye.jwt.build.Jwt
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Mock authorization server validates PKCE and emits signed tokens for the browser integration test. */
class BrowserOidcFixture(
  private val server: HttpServer,
) {
  private data class Login(
    val challenge: String,
    val nonce: String,
  )

  private val codes = ConcurrentHashMap<String, Login>()
  private val refreshTokens = ConcurrentHashMap<String, Login>()
  private val logins = AtomicInteger()
  private val refreshes = AtomicInteger()
  private val key: RSAPrivateCrtKey =
    run {
      val pem =
        javaClass
          .getResource("/private-test-key.pem")!!
          .readText()
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "")
          .replace(Regex("\\s"), "")
      KeyFactory
        .getInstance(
          "RSA",
        ).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPrivateCrtKey
    }
  private val address get() = "http://127.0.0.1:${server.address.port}/browser"

  fun install(): Map<String, String> {
    server.createContext("/browser/.well-known/openid-configuration") { exchange ->
      exchange.json(
        """{"issuer":"https://issuer.test","authorization_endpoint":"$address/authorize","token_endpoint":"$address/token","jwks_uri":"$address/jwks","response_types_supported":["code"],"subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"]}""",
      )
    }
    server.createContext("/browser/jwks") { exchange ->
      fun unsigned(value: java.math.BigInteger): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
          value
            .toByteArray()
            .dropWhile {
              it ==
                0.toByte()
            }.toByteArray(),
        )
      exchange.json(
        """{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","n":"${unsigned(
          key.modulus,
        )}","e":"${unsigned(key.publicExponent)}"}]}""",
      )
    }
    server.createContext("/browser/authorize") { exchange ->
      val query = parameters(exchange.requestURI.rawQuery)
      require(
        query["code_challenge_method"] == "S256" &&
          !query["code_challenge"].isNullOrBlank() &&
          !query["nonce"].isNullOrBlank(),
      )
      val code = UUID.randomUUID().toString()
      codes[code] = Login(query.getValue("code_challenge"), query.getValue("nonce"))
      logins.incrementAndGet()
      val target =
        query.getValue("redirect_uri") + "?code=$code&state=" + URLEncoder.encode(query.getValue("state"), UTF_8)
      exchange.responseHeaders.add("Location", target)
      exchange.sendResponseHeaders(302, -1)
      exchange.close()
    }
    server.createContext("/browser/token") { exchange ->
      val parameters = parameters(exchange.requestBody.bufferedReader().readText())
      val login =
        if (parameters["grant_type"] == "refresh_token") {
          refreshes.incrementAndGet()
          refreshTokens.getValue(parameters.getValue("refresh_token"))
        } else {
          val login = codes.remove(parameters.getValue("code"))!!
          val challenge =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
              MessageDigest.getInstance("SHA-256").digest(parameters.getValue("code_verifier").toByteArray(UTF_8)),
            )
          require(challenge == login.challenge)
          login
        }
      val expiry = Instant.now().epochSecond + 4
      val access =
        Jwt
          .issuer(
            "https://issuer.test",
          ).subject("alice")
          .audience("browser-app")
          .claim("scope", "read")
          .expiresAt(expiry)
          .sign(key)
      val id =
        Jwt
          .issuer(
            "https://issuer.test",
          ).subject("alice")
          .audience("browser-app")
          .claim("nonce", login.nonce)
          .expiresAt(expiry)
          .sign(key)
      val refresh = UUID.randomUUID().toString()
      refreshTokens[refresh] = login
      exchange.json(
        """{"access_token":"$access","id_token":"$id","refresh_token":"$refresh","expires_in":4,"token_type":"Bearer"}""",
      )
    }
    server.createContext("/browser/stats") { exchange -> exchange.json("${logins.get()}:${refreshes.get()}") }
    return mapOf(
      "quarkus.oidc.browser.client-id" to "browser-app",
      "quarkus.oidc.browser.credentials.secret" to "browser-test-secret",
    )
  }

  private fun parameters(value: String): Map<String, String> =
    value.split('&').associate {
      val parts = it.split('=', limit = 2)
      URLDecoder.decode(parts[0], UTF_8) to URLDecoder.decode(parts.getOrElse(1) { "" }, UTF_8)
    }

  private fun HttpExchange.json(value: String) {
    val bytes = value.toByteArray(UTF_8)
    responseHeaders.add("Content-Type", "application/json")
    sendResponseHeaders(200, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
  }
}
