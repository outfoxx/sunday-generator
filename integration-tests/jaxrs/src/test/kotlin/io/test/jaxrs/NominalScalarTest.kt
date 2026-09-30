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

package io.test.jaxrs

import io.test.jaxrs.nominal.AmbiguousSid
import io.test.jaxrs.nominal.AnySid
import io.test.jaxrs.nominal.BaseFactSid
import io.test.jaxrs.nominal.NominalAPI
import io.test.jaxrs.nominal.NominalAPIResource
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Exercises actual JAX-RS conversion of generated nominal parameters. */
class NominalScalarTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(NominalAPIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(NominalAPIResource(Identifiers)).to(NominalAPIResource::class.java)
          }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `path and query bind wrappers without JVM name mangling`() {
    for ((id, branch) in listOf("sid:f:abc" to "BaseFactSid", "sid:l:abc" to "BaseLossSid")) {
      target("/ids/$id").queryParam("fact", "sid:f:query").request().get().use {
        assertEquals(200, it.status)
        assertEquals("$branch:$id:sid:f:query", it.readEntity(String::class.java))
      }
    }
    target("/ids/invalid")
      .queryParam("fact", "sid:f:query")
      .request()
      .get()
      .use { assertEquals(404, it.status) }
    target("/ids/sid:f:abc")
      .queryParam("fact", "invalid")
      .request()
      .get()
      .use { assertEquals(404, it.status) }
    target("/ambiguous/sid:f:abc").request().get().use { assertEquals(404, it.status) }
  }

  private object Identifiers : NominalAPI {
    override fun identify(
      id: AnySid,
      fact: BaseFactSid,
    ): Response = Response.ok("${id.javaClass.simpleName}:$id:$fact").build()

    override fun ambiguous(id: AmbiguousSid): Response = Response.noContent().build()
  }
}
