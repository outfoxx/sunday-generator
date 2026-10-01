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

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.test.quarkus.profiled.ProfiledAPI
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors

@QuarkusTest
@QuarkusTestResource(ProfiledOidcUpstream::class)
class ProfiledClientSecurityTest {
  @Inject
  @RestClient
  lateinit var client: ProfiledAPI

  @Test
  fun `public operations stay public and scope-specific clients cache concurrent acquisition`() {
    assertEquals("public", client.publicOperation())
    assertEquals(0, count("items:read"))
    Executors.newFixedThreadPool(8).use { executor ->
      val requests = (1..16).map { executor.submit<String> { client.read() } }
      requests.forEach { assertEquals("Bearer items:read-1", it.get()) }
    }
    assertEquals(1, count("items:read"))
    assertEquals("Bearer items:write-1", client.write())
    assertEquals("Bearer items:read-1", client.read())
    assertEquals(1, count("items:write"))
    assertEquals("public", client.publicOperation())
  }

  @Test
  fun `native renewal uses rotated refresh tokens`() {
    assertEquals("Bearer items:renew-1", client.renew())
    assertEquals("Bearer items:renew-2", client.renew())
    assertEquals("Bearer items:renew-3", client.renew())
    assertEquals(3, count("items:renew"))
  }

  @Test
  fun `authentication recovery coalesces rejected native leases across concurrent invocations`() {
    Executors.newFixedThreadPool(8).use { executor ->
      val requests = (1..16).map { executor.submit<String> { client.recover() } }
      requests.forEach { assertEquals("Bearer items:recover-2", it.get()) }
    }
    assertEquals(2, count("items:recover"))
  }

  @Test
  fun `an unconfigured retry policy permits exactly one safe authentication recovery`() {
    assertEquals("Bearer items:default-2", client.defaultRecover())
    assertEquals(2, count("items:default"))
    assertEquals(2, requestCount("default"))
  }

  @Test
  fun `native retry cannot multiply authentication recovery or replay forbidden and unsafe requests`() {
    val rejected = assertThrows(WebApplicationException::class.java) { client.reject() }
    assertEquals(401, rejected.response.status)
    assertEquals(2, requestCount("always"))
    assertEquals(2, count("items:reject"))
    val forbidden = assertThrows(WebApplicationException::class.java) { client.forbidden() }
    assertEquals(403, forbidden.response.status)
    assertEquals(1, requestCount("forbidden"))
    assertEquals(1, count("items:forbid"))
    assertThrows(WebApplicationException::class.java) { client.unsafe() }
    assertEquals(1, requestCount("unsafe"))
    assertEquals(1, count("items:unsafe"))
    assertThrows(WebApplicationException::class.java) { client.unchallenged() }
    assertEquals(1, requestCount("unchallenged"))
    assertEquals(1, count("items:unchallenged"))
  }

  private fun count(scope: String): Int = number("count/$scope")

  private fun requestCount(path: String): Int = number("requests/$path")

  private fun number(path: String): Int {
    val url = ConfigProvider.getConfig().getValue("quarkus.rest-client.profiled-client.url", String::class.java)
    return HttpClient.newHttpClient().use {
      it
        .send(
          HttpRequest.newBuilder(URI.create("$url/$path")).build(),
          HttpResponse.BodyHandlers.ofString(),
        ).body()
        .toInt()
    }
  }
}
