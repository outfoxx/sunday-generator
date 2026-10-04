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

import io.outfoxx.sunday.validation.jakarta.ClientModelValidation
import io.outfoxx.sunday.validation.jakarta.EntitySchema
import io.outfoxx.sunday.validation.jakarta.ServerModelValidation
import io.test.jaxrs.payloads.API
import io.test.jaxrs.payloads.APIResource
import io.test.jaxrs.payloads.CodesValidation
import io.test.jaxrs.payloads.OpenState
import io.test.jaxrs.payloads.State
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.server.validation.ValidationFeature
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class NativePayloadTest : JerseyTest() {
  private val calls = AtomicInteger()

  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(
      APIResource::class.java,
      JacksonFeature::class.java,
      ValidationFeature::class.java,
      ServerModelValidation::class.java,
    ).property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(
              APIResource(
                object : API {
                  override fun defaultParameters(
                    pathValue: String,
                    queryValue: Int,
                    cookieValue: String,
                    headerValue: String,
                  ): Response {
                    calls.incrementAndGet()
                    return Response.ok(listOf(pathValue, queryValue.toString(), cookieValue, headerValue)).build()
                  }

                  override fun parameters(
                    pathState: State,
                    queryStates: List<State>?,
                    openState: OpenState?,
                    cookieState: State?,
                    headerState: State?,
                  ): Response {
                    calls.incrementAndGet()
                    return Response.ok(listOf(pathState)).build()
                  }

                  override fun codes(body: List<String>): Response {
                    calls.incrementAndGet()
                    return Response.ok(if (body == listOf("ZZ", "ZZ")) emptyList<String>() else body).build()
                  }

                  override fun states(body: List<State>): Response {
                    calls.incrementAndGet()
                    return Response.ok(listOf(State.Unknown("future"))).build()
                  }
                },
              ),
            ).to(APIResource::class.java)
          }
        },
      )

  override fun configureClient(config: ClientConfig) {
    config.register(JacksonFeature::class.java).register(ClientModelValidation::class.java)
  }

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `plain JAX-RS applies defaults before invoking the delegate`() {
    target("native/defaults/explicit").request().get().use {
      assertEquals(200, it.status)
      assertEquals("[\"explicit\",\"5\",\"cookie\",\"header\"]", it.readEntity(String::class.java))
    }
    assertEquals(1, calls.get())
  }

  @Test
  fun `plain JAX-RS parameters validate after conversion before delegates`() {
    target("native/parameters/future").request().get().use { assertEquals(400, it.status) }
    target("native/parameters/ready")
      .queryParam("queryStates", "ready", "future")
      .request()
      .get()
      .use { assertEquals(400, it.status) }
    target("native/parameters/ready")
      .request()
      .header("headerState", "future")
      .get()
      .use { assertEquals(400, it.status) }
    target("native/parameters/ready")
      .request()
      .cookie("cookieState", "future")
      .get()
      .use { assertEquals(400, it.status) }
    assertEquals(0, calls.get())
    target("native/parameters/ready")
      .queryParam("openState", "future")
      .request()
      .get()
      .use { assertEquals(200, it.status) }
    assertEquals(1, calls.get())
  }

  @Test
  fun `plain JAX-RS validates request collections and tolerant elements before delegates`() {
    for ((path, json) in listOf("codes" to "[]", "codes" to "[\"AB\",\"bad\"]", "states" to "[\"future\"]")) {
      target("native/$path").request().post(Entity.json(json)).use { assertEquals(400, it.status, json) }
      assertEquals(0, calls.get())
    }
    target("native/codes").request().post(Entity.json("[\"AB\",\"CD\"]")).use { assertEquals(200, it.status) }
    target("native/states").request().post(Entity.json("[\"ready\"]")).use {
      assertEquals(200, it.status)
      assertEquals("[\"future\"]", it.readEntity(String::class.java))
    }
    assertEquals(2, calls.get())
  }

  @Test
  fun `registered client codec adapter checks current root values before transmission`() {
    val values = mutableListOf("AB", "CD")

    fun send() =
      target("native/codes").request().post(
        Entity.entity(values, MediaType.APPLICATION_JSON_TYPE, arrayOf(EntitySchema(CodesValidation::class))),
      )
    send().use { assertEquals(200, it.status) }
    values[1] = "bad"
    val failure = assertThrows(RuntimeException::class.java) { send().close() }
    assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is ConstraintViolationException })
    assertEquals(1, calls.get())
  }

  @Test
  fun `invalid application response is a server failure`() {
    target("native/codes").request().post(Entity.json("[\"ZZ\",\"ZZ\"]")).use { assertEquals(500, it.status) }
    assertEquals(1, calls.get())
  }
}
