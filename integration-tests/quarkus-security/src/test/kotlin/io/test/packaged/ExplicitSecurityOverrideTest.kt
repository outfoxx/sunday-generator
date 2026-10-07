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

import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.smallrye.mutiny.Uni
import io.test.packaged.first.OpenAPISecurity
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Principal

@QuarkusTest
@TestProfile(ExplicitSecurityOverrideTest.Profile::class)
class ExplicitSecurityOverrideTest {
  @TestHTTPResource lateinit var baseUri: URI

  class Profile : QuarkusTestProfile {
    override fun getEnabledAlternatives(): Set<Class<*>> = setOf(Override::class.java)
  }

  /** Explicit application customization replaces the generated default producer. */
  @Alternative
  @Singleton
  class Override {
    @Produces
    @Singleton
    fun security(): OpenAPISecurity =
      OpenAPISecurity(
        mapOf(
          "shared" to
            OpenAPISecurity.SchemeBinding(
              OpenAPISecurity.Authenticator { _, _, credential ->
                Uni.createFrom().item(
                  if (credential ==
                    "custom"
                  ) {
                    QuarkusSecurityIdentity.builder().setPrincipal(Principal { "custom" }).build()
                  } else {
                    null
                  },
                )
              },
              permissions = { setOf("read") },
            ),
        ),
      )
  }

  @Test
  fun `application producer overrides generated default without ambiguity`() {
    val response =
      HttpClient.newHttpClient().send(
        HttpRequest
          .newBuilder(
            baseUri.resolve("/first/protected"),
          ).header("Authorization", "Bearer custom")
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    expectThat(response.statusCode()).isEqualTo(200)
    expectThat(response.body()).isEqualTo("custom")
  }
}
