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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.test.inherited.Metadata
import io.test.inherited.QueuedProblem
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
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

/** Exercises Jersey's actual entity writer with a generated inherited problem payload. */
class InheritedProblemTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(Endpoint::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(JacksonFeature::class.java)

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `Jersey serializes inherited problem extensions`() {
    target("inherited-problems").request().get().use { response ->
      assertEquals(409, response.status)
      assertEquals("application/problem+json", response.mediaType.toString())
      val mapper = jacksonObjectMapper()
      assertEquals(
        mapper.readTree(
          """
          {"type":"https://example.com/problems/rebuilding","title":"Resource Rebuilding","status":409,
          "detail":"Resource A is rebuilding","traceId":"trace-A","nullableParent":null,
          "resourceKey":"A","retryAfterSeconds":1,"nullableChild":null,"metadata":{"region":"west"},
          "queuePosition":2,"queue-name":"rebuilds"}
          """.trimIndent(),
        ),
        mapper.readTree(response.readEntity(String::class.java)),
      )
    }
  }

  /** Supplies a problem entity to the JAX-RS response pipeline. */
  @Path("/inherited-problems")
  class Endpoint {
    /** Returns a concrete problem with fields declared across three inheritance levels. */
    @GET
    fun read(): Response =
      Response
        .status(409)
        .type("application/problem+json")
        .entity(
          QueuedProblem(
            detail = "Resource A is rebuilding",
            traceId = "trace-A",
            resourceKey = "A",
            nullableChild = null,
            metadata = Metadata(region = "west"),
            queuePosition = 2,
          ),
        ).build()
  }
}
