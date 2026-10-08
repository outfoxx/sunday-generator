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

package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode

/** Validates the native configuration surface without translating policy into a second configuration language. */
internal object KotlinQuarkusProperties {
  private val common =
    setOf(
      "auth-server-url",
      "discovery-enabled",
      "discovery-path",
      "token-path",
      "client-id",
      "connection-delay",
      "connection-timeout",
      "connection-retry-count",
      "max-pool-size",
      "use-blocking-dns-lookup",
      "follow-redirects",
      "credentials.secret",
      "credentials.client-secret.value",
      "credentials.client-secret.method",
      "credentials.client-secret.provider.name",
      "credentials.client-secret.provider.key",
      "credentials.client-secret.provider.keyring-name",
      "credentials.jwt.source",
      "credentials.jwt.secret",
      "credentials.jwt.key",
      "credentials.jwt.key-file",
      "credentials.jwt.key-store-file",
      "credentials.jwt.key-store-password",
      "credentials.jwt.key-password",
      "credentials.jwt.key-id",
      "credentials.jwt.audience",
      "credentials.jwt.issuer",
      "credentials.jwt.subject",
      "credentials.jwt.signature-algorithm",
      "credentials.jwt.lifespan",
      "credentials.jwt.token-path",
      "tls.tls-configuration-name",
      "tls.verification",
      "tls.trust-store-file",
      "tls.trust-store-password",
      "tls.key-store-file",
      "tls.key-store-password",
      "tls.key-store-key-password",
      "proxy.host",
      "proxy.port",
      "proxy.username",
      "proxy.password",
      "proxy.proxy-configuration-name",
    )
  private val client =
    setOf(
      "client-enabled",
      "grant.type",
      "grant.access-token-property",
      "grant.refresh-token-property",
      "grant.expires-in-property",
      "grant.refresh-expires-in-property",
      "scopes",
      "early-tokens-acquisition",
      "refresh-token-time-skew",
      "refresh-interval",
      "access-token-expires-in",
      "access-token-expiry-skew",
      "absolute-expires-in",
      "revoke-path",
    )
  private val server =
    setOf(
      "tenant-enabled",
      "application-type",
      "public-key",
      "jwks-path",
      "introspection-path",
      "authorization-path",
      "token.issuer",
      "token.audience",
      "token.principal-claim",
      "token.subject-required",
      "token.lifespan-grace",
      "authentication.pkce-required",
      "authentication.nonce-required",
      "authentication.verify-access-token",
      "authentication.redirect-path",
      "authentication.scopes",
      "authentication.user-info-required",
      "roles.source",
      "roles.role-claim-path",
      "token.refresh-expired",
      "token.refresh-token-time-skew",
      "logout.path",
    )

  fun suffix(
    key: String,
    value: String?,
    role: GenerationMode,
  ) {
    require(
      key in common ||
        key in (if (role == GenerationMode.Client) client else server) ||
        role == GenerationMode.Client &&
        (
          key.matches(Regex("grant-options\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_.-]+")) ||
            key.matches(Regex("headers\\.[A-Za-z0-9_-]+(?:[.][A-Za-z0-9_-]+)*"))
        ),
    ) {
      "Unsupported native Quarkus $role property '$key'"
    }
    if (value != null &&
      (
        key == "client-id" ||
          key.endsWith("secret") ||
          key.endsWith("password") ||
          key == "credentials.client-secret.value" ||
          key == "credentials.jwt.key" ||
          key == "credentials.jwt.subject" ||
          key.equals("headers.Authorization", ignoreCase = true) ||
          key.equals("headers.Proxy-Authorization", ignoreCase = true) ||
          key.endsWith(".subject_token") ||
          key.endsWith(".actor_token")
      )
    ) {
      require(
        value.startsWith("${'$'}{"),
      ) { "Native '$key' must reference runtime configuration with a property expression" }
    }
  }

  fun global(
    key: String,
    value: String,
    role: GenerationMode,
  ) {
    require(key != "config_ordinal" && key != "quarkus.config.locations") {
      "Configuration loading is application-owned: $key"
    }
    when {
      role == GenerationMode.Server && key.startsWith("quarkus.zanzibar.filter.") -> {
        require(
          key.substringAfter("quarkus.zanzibar.filter.") in
            setOf("enabled", "deny-unannotated-resource-methods", "timeout", "unauthenticated-user"),
        ) {
          "Unsupported Zanzibar property '$key'"
        }
      }
      role == GenerationMode.Server && key == "quarkus.http.auth.proactive" -> Unit
      role == GenerationMode.Client && key == "quarkus.rest-client-oidc-filter.refresh-on-unauthorized" -> Unit
      role == GenerationMode.Client && key.startsWith("quarkus.rest-client.") -> {
        require(
          key.substringAfterLast('.') in
            setOf("url", "uri", "connect-timeout", "read-timeout", "follow-redirects", "max-redirects"),
        ) {
          "Unsupported REST-client property '$key'"
        }
      }
      else ->
        error(
          "Unsupported native Quarkus $role property '$key'; put OIDC properties on the selected security binding",
        )
    }
    require(value.isNotBlank()) { "Native property '$key' cannot be blank" }
  }
}
