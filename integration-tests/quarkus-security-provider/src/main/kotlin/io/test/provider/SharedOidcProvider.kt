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
import io.quarkus.oidc.AccessTokenCredential
import io.quarkus.security.identity.request.TokenAuthenticationRequest
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils
import io.smallrye.mutiny.Uni
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.Config
import org.eclipse.microprofile.jwt.JsonWebToken

/** Shared integration library with no dependency on generated service packages. */
@Singleton
class SharedOidcProvider(
  private val config: Config,
) : ServerSecurityProvider {
  override val name = "shared"

  override fun binding(): ServerSecurityProvider.Binding {
    require(
      config.getOptionalValue("quarkus.oidc.token.issuer", String::class.java).isPresent,
    ) { "Missing trusted issuer" }
    require(config.getOptionalValue("quarkus.oidc.token.audience", String::class.java).isPresent) {
      "Missing trusted audience"
    }
    return ServerSecurityProvider.Binding(
      ServerSecurityProvider.Authenticator { request, _, credential ->
        if (credential == null) {
          Uni.createFrom().nullItem()
        } else {
          request.identityProviderManager.authenticate(
            HttpSecurityUtils.setRoutingContextAttribute(
              TokenAuthenticationRequest(AccessTokenCredential(credential)),
              request.context,
            ),
          )
        }
      },
      permissions = { identity ->
        (identity.principal as JsonWebToken)
          .getClaim<String>(
            "scope",
          ).orEmpty()
          .split(' ')
          .filter { it.isNotBlank() }
          .toSet()
      },
    )
  }
}
