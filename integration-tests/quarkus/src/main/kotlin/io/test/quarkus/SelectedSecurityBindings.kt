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
import io.test.quarkus.selected.OpenAPISecurity
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.security.Principal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Accepts opaque credentials to distinguish selected bindings from Quarkus's other installed mechanisms. */
@Singleton
class SelectedSecurityBindings {
  val validations = ConcurrentHashMap<String, AtomicInteger>()

  /** Provides independently counted authenticators for two generated, annotation-selected strategies. */
  @Produces
  @Singleton
  fun security(): OpenAPISecurity =
    OpenAPISecurity(
      mapOf("bearerAuth" to "selected-token", "apiKey" to "selected-key").mapValues { (name, expected) ->
        OpenAPISecurity.SchemeBinding(
          OpenAPISecurity.Authenticator { request, _, credential ->
            val id = request.context.request().getHeader("X-Request-ID")
            validations.computeIfAbsent("$id:$name") { AtomicInteger() }.incrementAndGet()
            Uni.createFrom().item(
              if (credential == expected) {
                QuarkusSecurityIdentity.builder().setPrincipal(Principal { name }).build()
              } else {
                null
              },
            )
          },
        )
      },
    )
}
