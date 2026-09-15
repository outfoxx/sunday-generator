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

import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.TokenAuthenticationRequest
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.security.spi.runtime.AuthorizationSuccessEvent
import io.quarkus.smallrye.jwt.runtime.auth.JsonWebTokenCredential
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils
import io.smallrye.mutiny.Uni
import io.test.quarkus.zanzibar.OpenAPISecurity
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.jwt.JsonWebToken
import java.security.Principal
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Native provider bindings with per-request instrumentation, used only by the integration fixture. */
@Singleton
class NativeSecurityBindings {
  val authentications = ConcurrentHashMap<String, AtomicInteger>()
  val permissionReads = ConcurrentHashMap<String, AtomicInteger>()
  val authorizations = ConcurrentHashMap<String, AtomicInteger>()
  val authenticatedChecks = ConcurrentHashMap<String, AtomicInteger>()

  /** Counts native authorization gates without adding instrumentation to generated policies. */
  fun authorized(
    @Observes event: AuthorizationSuccessEvent,
  ) {
    val context = event.eventProperties[RoutingContext::class.java.name] as? RoutingContext ?: return
    val id = context.request().getHeader("X-Request-ID") ?: return
    authorizations.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
    if (event.eventProperties[AuthorizationSuccessEvent.AUTHORIZATION_CONTEXT] ==
      "io.quarkus.security.runtime.interceptor.check.AuthenticatedCheck"
    ) {
      authenticatedChecks.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
    }
  }

  /** Uses Quarkus's real JWT provider; the API-key fixture represents a different authenticated party. */
  @Produces
  @Singleton
  fun security(): OpenAPISecurity =
    OpenAPISecurity(
      mapOf(
        "jwt" to
          OpenAPISecurity.SchemeBinding(
            OpenAPISecurity.Authenticator { request, _, credential ->
              val id = request.context.request().getHeader("X-Request-ID") ?: "default"
              authentications.computeIfAbsent("$id:jwt") { AtomicInteger() }.incrementAndGet()
              request.identityProviderManager
                .authenticate(
                  HttpSecurityUtils.setRoutingContextAttribute(
                    TokenAuthenticationRequest(JsonWebTokenCredential(credential)),
                    request.context,
                  ),
                ).map { identity ->
                  QuarkusSecurityIdentity
                    .builder(
                      identity,
                    ).addAttribute("binding", "jwt")
                    .addAttribute("request-id", id)
                    .build()
                }
            },
            permissions = { identity ->
              val id = identity.getAttribute<String>("request-id")
              permissionReads.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
              (identity.principal as JsonWebToken)
                .getClaim<String>(
                  "scope",
                ).orEmpty()
                .split(' ')
                .filter { it.isNotBlank() }
                .toSet()
            },
          ),
        "tenantKey" to
          OpenAPISecurity.SchemeBinding(
            OpenAPISecurity.Authenticator { request, _, credential ->
              val id = request.context.request().getHeader("X-Request-ID") ?: "default"
              authentications.computeIfAbsent("$id:tenantKey") { AtomicInteger() }.incrementAndGet()
              Uni
                .createFrom()
                .item<SecurityIdentity?>(
                  if (credential == "tenant-secret") {
                    QuarkusSecurityIdentity
                      .builder()
                      .setPrincipal(
                        Principal { "tenant-service" },
                      ).addAttribute("binding", "tenant")
                      .build()
                  } else {
                    null
                  },
                ).onItem()
                .delayIt()
                .by(Duration.ofMillis(5))
            },
          ),
      ),
      subjectSchemes = mapOf(setOf("jwt", "tenantKey") to "jwt"),
    )
}
