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

import io.test.jaxrs.uploads.APIResource
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Application
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class ContentTypeTest : JerseyTest() {
  override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

  override fun configure(): Application =
    ResourceConfig(APIResource::class.java)
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(
        object : AbstractBinder() {
          override fun configure() {
            bind(APIResource(Uploads())).to(APIResource::class.java)
          }
        },
      )

  @BeforeEach
  fun start() = setUp()

  @AfterEach
  fun stop() = tearDown()

  @Test
  fun `generated Jersey endpoints bind headers without widening body media constraints`() {
    val body = byteArrayOf(0, -1, 10, 13, 123, 125)
    val mediaTypes =
      listOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "text/plain",
        "application/octet-stream",
        "IMAGE/PNG",
        "image/png;profile=test",
      )
    for (path in listOf("raw", "image", "fixed", "enum", "constant", "multiple", "tolerant")) {
      for (mediaType in mediaTypes) {
        val baseType = mediaType.substringBefore(';').lowercase()
        val compatible =
          when (path) {
            "raw" -> true
            "fixed" -> baseType == "application/octet-stream"
            "multiple" -> baseType in listOf("image/png", "image/jpeg")
            else -> baseType.startsWith("image/")
          }
        val valid =
          when (path) {
            "enum" -> mediaType in listOf("image/png", "image/jpeg")
            "constant" -> mediaType == "image/png"
            else -> true
          }
        val expected =
          if (!compatible) {
            415
          } else if (!valid) {
            400
          } else {
            204
          }
        target("/uploads/$path").request().put(Entity.entity(body, mediaType)).use { response ->
          assertEquals(expected, response.status, "$path $mediaType")
          if (expected == 204) {
            assertEquals(mediaType, response.getHeaderString("X-Observed-Type"), path)
            assertEquals(Base64.getEncoder().encodeToString(body), response.getHeaderString("X-Observed-Body"), path)
          } else {
            assertNull(response.getHeaderString("X-Observed-Type"), path)
          }
        }
      }
    }
  }

  @Test
  fun `optional headers bind on endpoints without bodies`() {
    target("/uploads/header").request().get().use {
      assertEquals(204, it.status)
      assertEquals("missing", it.getHeaderString("X-Observed-Type"))
    }
    target("/uploads/header").request().header("Content-Type", "application/json").get().use {
      assertEquals(204, it.status)
      assertEquals("application/json", it.getHeaderString("X-Observed-Type"))
    }
    target("/uploads/header").request().header("Content-Type", "image/png").get().use {
      assertEquals(415, it.status)
    }
  }
}
