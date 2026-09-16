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

import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.smallrye.mutiny.Uni
import io.test.quarkus.secure.OpenAPISecurity
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.security.Principal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Fixed credential validators used only by the runtime integration fixture. */
@Singleton
class SecurityBindings {
  /** Counts validator and permission-reader invocations independently. */
  val authentications = ConcurrentHashMap<String, AtomicInteger>()
  val permissionReads = ConcurrentHashMap<String, AtomicInteger>()

  /** Supplies all scheme validators through CDI. */
  @Produces
  @Singleton
  fun endpointSecurity(): OpenAPISecurity =
    OpenAPISecurity(
      OpenAPISecurity.schemes.mapValues { (_, definition) ->
        OpenAPISecurity.SchemeBinding(
          OpenAPISecurity.Authenticator { _, scheme, credential ->
            authentications.computeIfAbsent(scheme.name) { AtomicInteger() }.incrementAndGet()
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
            Uni.createFrom().item(
              permissions?.let {
                QuarkusSecurityIdentity
                  .builder()
                  .setPrincipal(
                    Principal { "alice" },
                  ).addAttribute("permissions", it)
                  .build()
              },
            )
          },
          permissions = { identity ->
            permissionReads.computeIfAbsent(definition.name) { AtomicInteger() }.incrementAndGet()
            identity.getAttribute<Set<String>>("permissions")
          },
        )
      },
      subjectSchemes = OpenAPISecurity.subjectRequirements.associateWith { "oauth" },
    )
}
