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

package io.test.provider

import io.outfoxx.sunday.jaxrs.quarkus.ServerSecurityProvider
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.smallrye.mutiny.Uni
import jakarta.inject.Singleton
import java.security.Principal

/** Supplies independent API-key evidence for composite and alternative requirements. */
@Singleton
class SharedKeyProvider : ServerSecurityProvider {
  override val name = "key"

  override fun binding() =
    ServerSecurityProvider.Binding(
      ServerSecurityProvider.Authenticator { _, _, credential ->
        Uni.createFrom().item(
          if (credential == "service-secret") {
            QuarkusSecurityIdentity.builder().setPrincipal(Principal { "service-key" }).build()
          } else {
            null
          },
        )
      },
    )
}
