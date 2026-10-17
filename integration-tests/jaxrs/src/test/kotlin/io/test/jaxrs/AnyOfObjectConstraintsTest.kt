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
import io.test.jaxrs.checkout.API
import io.test.jaxrs.checkout.APIResource
import io.test.jaxrs.checkout.BranchCheckoutRequest
import io.test.jaxrs.checkout.InlineCheckoutRequest
import io.test.jaxrs.checkout.InlineCheckoutRequestCase1
import io.test.jaxrs.checkout.NamedCheckoutRequest
import io.test.jaxrs.checkout.SharedCheckoutRequest
import io.test.jaxrs.checkout.SharedCheckoutRequestCase1
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
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
import java.util.concurrent.atomic.AtomicInteger

/** Exercises inclusive object unions through generated resource adapters and JSON binding. */
class AnyOfObjectConstraintsTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(APIResource::class.java, JacksonFeature::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(APIResource(Checkout)).to(APIResource::class.java)
          }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `all source forms bind objects and reject invalid bodies before delegation`() {
    val mapper = ObjectMapper()
    for (shape in listOf("shared", "inline", "named")) {
      for (wire in listOf(
        """{"branchId":"branch-1"}""",
        """{"targetCommitId":"commit-1"}""",
        """{"branchId":"branch-1","targetCommitId":"commit-1"}""",
      )) {
        val before = Checkout.calls.get()
        target("/checkout/$shape").request().put(Entity.json(wire)).use {
          assertEquals(200, it.status, shape)
          assertEquals(mapper.readTree(wire), mapper.readTree(it.readEntity(String::class.java)), shape)
        }
        assertEquals(before + 1, Checkout.calls.get())
      }
      for (wire in listOf("{}", """{"future":"not-a-target"}""", """{"branchId":null}""", "\"not-an-object\"")) {
        val before = Checkout.calls.get()
        target("/checkout/$shape").request().put(Entity.json(wire)).use {
          assertEquals(400, it.status, "$shape: $wire")
        }
        assertEquals(before, Checkout.calls.get())
      }
    }
  }

  @Test
  fun `generated alternatives support direct construction and JSON round trips`() {
    val mapper = ObjectMapper()
    val expected = mapper.readTree("""{"branchId":"branch-1","targetCommitId":"commit-1"}""")
    for (value in listOf(
      SharedCheckoutRequestCase1("branch-1", "commit-1"),
      InlineCheckoutRequestCase1("branch-1", "commit-1"),
      BranchCheckoutRequest("branch-1", "commit-1"),
    )) {
      assertEquals(expected, mapper.readTree(mapper.writeValueAsString(value)))
    }
  }

  private object Checkout : API {
    val calls = AtomicInteger()

    override fun sharedCheckout(body: SharedCheckoutRequest): Response = accept(body)

    override fun inlineCheckout(body: InlineCheckoutRequest): Response = accept(body)

    override fun namedCheckout(body: NamedCheckoutRequest): Response = accept(body)

    private fun accept(body: Any): Response {
      calls.incrementAndGet()
      return Response.ok(body).build()
    }
  }
}
