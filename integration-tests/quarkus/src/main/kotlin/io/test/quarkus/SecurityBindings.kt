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

import io.test.quarkus.secure.OpenAPISecurity
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.security.Principal

/** Fixed credential validators used only by the runtime integration fixture. */
@Singleton
class SecurityBindings {
  /** Supplies all scheme validators through CDI. */
  @Produces
  @Singleton
  fun endpointSecurity(): OpenAPISecurity =
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
}
