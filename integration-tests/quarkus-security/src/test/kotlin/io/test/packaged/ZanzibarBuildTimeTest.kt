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

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
@QuarkusTestResource(MockOidcResource::class)
@TestProfile(ZanzibarBuildTimeTest.Profile::class)
class ZanzibarBuildTimeTest {
  @TestHTTPResource lateinit var baseUri: URI

  class Profile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
      mapOf("quarkus.zanzibar.filter.deny-unannotated-resource-methods" to "true")
  }

  @Test
  fun `augmentation policy denies unannotated routes preserves ignored routes and cannot change at runtime`() {
    val client = HttpClient.newHttpClient()

    fun status(path: String) =
      client
        .send(
          HttpRequest.newBuilder(baseUri.resolve(path)).GET().build(),
          HttpResponse.BodyHandlers.discarding(),
        ).statusCode()
    expectThat(status("/native/unannotated")).isEqualTo(403)
    expectThat(status("/native/public")).isEqualTo(200)
    val key = "quarkus.zanzibar.filter.deny-unannotated-resource-methods"
    val previous = System.setProperty(key, "false")
    try {
      expectThat(status("/native/unannotated")).isEqualTo(403)
      expectThat(status("/native/public")).isEqualTo(200)
    } finally {
      if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
    }
  }
}
