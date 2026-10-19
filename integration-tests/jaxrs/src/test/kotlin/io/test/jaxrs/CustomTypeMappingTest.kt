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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.test.jaxrs.custom.API
import io.test.jaxrs.custom.APIResource
import io.test.jaxrs.custom.Identifier
import io.test.jaxrs.custom.Record
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ContextResolver
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Exercises application-owned scalars through generated JAX-RS resource adapters. */
class CustomTypeMappingTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(APIResource::class.java, JacksonFeature::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : ContextResolver<ObjectMapper> {
          override fun getContext(type: Class<*>): ObjectMapper = jacksonObjectMapper()
        },
      ).register(
        object : AbstractBinder() {
          override fun configure() {
            bind(APIResource(Identifiers)).to(APIResource::class.java)
          }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `path query and JSON use the application scalar`() {
    val mapper = jacksonObjectMapper()
    target("/identifiers/id:abc").queryParam("other", Identifier("id:def")).request().get().use {
      assertEquals(200, it.status)
      val record = mapper.readValue(it.readEntity(String::class.java), Record::class.java)
      assertEquals(Identifier("id:abc"), record.id)
      assertEquals(Identifier("id:def"), record.optional)
    }
    val wire = """{"id":"id:abc","optional":"id:def","plain":"plain"}"""
    target("/identifiers").request().post(Entity.json(wire)).use {
      assertEquals(200, it.status)
      assertEquals(mapper.readTree(wire), mapper.readTree(it.readEntity(String::class.java)))
    }
    target("/identifiers/invalid").queryParam("other", "id:def").request().get().use {
      assertEquals(404, it.status)
    }
    target("/identifiers/id:abc").queryParam("other", "invalid").request().get().use {
      assertEquals(404, it.status)
    }
    // The domain converter accepts this value; source-schema length checks still reject it.
    target("/identifiers/id:abc").queryParam("other", "id:abcdefghijklmnopqrstuvwxyz").request().get().use {
      assertEquals(400, it.status)
    }
    target("/identifiers").request().post(Entity.json("""{"id":"invalid"}""")).use {
      assertEquals(400, it.status)
    }
  }

  private object Identifiers : API {
    override fun identify(
      id: Identifier,
      other: Identifier,
    ): Response = Response.ok(Record(id, other, "plain")).build()

    override fun echo(body: Record): Response = Response.ok(body).build()
  }
}
