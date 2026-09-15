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
import io.test.quarkus.asyncapi.OpenAPISecurity
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.security.Principal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Application-owned providers for the AsyncAPI integration contracts. */
@Singleton
class AsyncSecurityBindings {
  val validations = ConcurrentHashMap<String, AtomicInteger>()

  /** Uses generated scheme names, including stable inline names, for provider registration. */
  @Produces
  @Singleton
  fun security(): OpenAPISecurity =
    OpenAPISecurity(
      OpenAPISecurity.schemes.mapValues { (name, _) ->
        OpenAPISecurity.SchemeBinding(
          OpenAPISecurity.Authenticator { _, _, credential ->
            validations.computeIfAbsent(name) { AtomicInteger() }.incrementAndGet()
            val permissions =
              when {
                name.endsWith("Key") && credential == "valid-key" -> emptySet<String>()
                name.startsWith("inline_") && credential == "valid-token" -> emptySet()
                name == "eventToken" && credential == "reader" -> setOf("read")
                name == "eventToken" && credential == "unscoped" -> emptySet()
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
          permissions =
            if (name ==
              "eventToken"
            ) {
              { identity -> identity.getAttribute<Set<String>>("permissions") }
            } else {
              null
            },
        )
      },
      subjectSchemes =
        OpenAPISecurity.subjectRequirements.associateWith {
          if ("eventToken" in
            it
          ) {
            "eventToken"
          } else {
            "headerKey"
          }
        },
    )
}
