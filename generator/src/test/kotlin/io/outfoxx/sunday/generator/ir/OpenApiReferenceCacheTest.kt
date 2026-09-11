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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText

class OpenApiReferenceCacheTest {
  @Test
  fun `every observed redirect alias works in a fresh offline session`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/a", "", 302, mapOf("Location" to "/b"))
      server.respond("/b", "", 307, mapOf("Location" to "/c"))
      server.respond("/c", "type: string", headers = mapOf("ETag" to "\"v1\""))
      val options = OpenApiReferenceOptions(directory)
      val online = OpenApiDocumentLoader.create(options)
      val effective = server.baseUri.resolve("c")
      assertEquals(effective, online.load(server.baseUri.resolve("a")).uri)
      assertEquals(effective, online.load(effective).uri)
      for (alias in listOf("c", "b", "a")) {
        val offline = OpenApiDocumentLoader.create(options.copy(offline = true)).load(server.baseUri.resolve(alias))
        assertEquals(effective, offline.uri)
        assertEquals("type: string", String(offline.bytes))
      }
      assertEquals(listOf("/a", "/b", "/c"), server.requests.toList())
      server.handlers["/c"] = { exchange ->
        assertEquals("\"v1\"", exchange.requestHeaders.getFirst("If-None-Match"))
        exchange.sendResponseHeaders(304, -1)
      }
      assertEquals("type: string", String(OpenApiDocumentLoader.create(options).load(effective).bytes))
      assertEquals(4, server.requests.size)
      server.respond("/c", "type: integer", headers = mapOf("ETag" to "\"v2\""))
      OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("a"))
      for (alias in listOf("b", "c", "a")) {
        val offline = OpenApiDocumentLoader.create(options.copy(offline = true)).load(server.baseUri.resolve(alias))
        assertEquals("type: integer", String(offline.bytes))
      }
      for ((body, status) in listOf("broken: [" to 200, "unavailable" to 503)) {
        server.respond("/c", body, status)
        assertThrows(Exception::class.java) { OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("a")) }
        for (alias in listOf("a", "b", "c")) {
          val offline = OpenApiDocumentLoader.create(options.copy(offline = true)).load(server.baseUri.resolve(alias))
          assertEquals("type: integer", String(offline.bytes))
        }
      }
    }
  }

  @Test
  fun `existing version one entries can populate effective URL aliases after revalidation`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val requested = server.baseUri.resolve("old")
      val effective = server.baseUri.resolve("schema")
      val body = "type: string".toByteArray()
      val digest = body.sha256()
      val entry = Files.createDirectories(directory.resolve("v1").resolve(requested.toString().toByteArray().sha256()))
      Files.write(entry.resolve("$digest.body"), body)
      entry.resolve("metadata.yaml").writeText("uri: '$effective'\nsha256: '$digest'\netag: '\"legacy\"'\n")
      val options = OpenApiReferenceOptions(directory)
      assertEquals(effective, OpenApiDocumentLoader.create(options.copy(offline = true)).load(requested).uri)
      server.respond("/old", "", 302, mapOf("Location" to "/schema"))
      server.handlers["/schema"] = { exchange ->
        assertEquals("\"legacy\"", exchange.requestHeaders.getFirst("If-None-Match"))
        exchange.sendResponseHeaders(304, -1)
      }
      OpenApiDocumentLoader.create(options).load(requested)
      assertEquals(
        "type: string",
        String(OpenApiDocumentLoader.create(options.copy(offline = true)).load(effective).bytes),
      )
      assertEquals(listOf("/old", "/schema"), server.requests.toList())
    }
  }

  @Test
  fun `concurrent crossed redirects release requested locks before persisting aliases`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val started = CountDownLatch(2)
      for ((path, destination) in listOf("/a" to "/b", "/b" to "/a")) {
        val requests = AtomicInteger()
        server.handlers[path] = { exchange ->
          if (requests.incrementAndGet() == 1) {
            started.countDown()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            exchange.responseHeaders.add("Location", destination)
            exchange.sendResponseHeaders(302, -1)
          } else {
            val body = "type: string".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
          }
        }
      }
      val options = OpenApiReferenceOptions(directory)
      val executor = Executors.newFixedThreadPool(2)
      try {
        val loads =
          listOf("a", "b").map { path ->
            CompletableFuture.supplyAsync(
              { OpenApiDocumentLoader.create(options).load(server.baseUri.resolve(path)) },
              executor,
            )
          }
        loads.forEach { assertEquals("type: string", String(it.get(10, TimeUnit.SECONDS).bytes)) }
      } finally {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
      }
      for (path in listOf("a", "b")) {
        assertEquals(
          "type: string",
          String(OpenApiDocumentLoader.create(options.copy(offline = true)).load(server.baseUri.resolve(path)).bytes),
        )
      }
    }
  }

  @Test
  fun `empty path segments survive redirects resource rebasing snapshots and offline caching`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/start", "", 302, mapOf("Location" to "/schemas//node%20file.yaml?old=1"))
      server.respond(
        "/schemas//node file.yaml",
        """
        ${'$'}id: '?mode=%2F'
        ${'$'}anchor: node
        type: object
        properties:
          self: {${'$'}ref: '?mode=%2F#node'}
          detail: {${'$'}ref: './detail%20file.yaml?kind=%2F#detail'}
        """.trimIndent(),
      )
      server.respond(
        "/schemas//detail file.yaml",
        "${'$'}anchor: detail\ntype: object\nproperties: {value: {type: string}}",
      )
      server.respond("/schemas/detail file.yaml", "type: integer")
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Empty segments, version: 1.0.0}
        paths: {}
        components:
          schemas:
            Node: {${'$'}ref: '${server.baseUri}start#node'}
            Single: {${'$'}ref: '${server.baseUri}schemas/detail%20file.yaml?kind=%2F'}
        """.trimIndent(),
      )
      val options = OpenApiReferenceOptions(directory.resolve("cache"))
      val loader = OpenApiDocumentLoader.create(options)
      val resolution = OpenApiReferenceResolver(loader).resolve(source.toUri())
      val result = OpenApiToGeneratedApi().convert(source.toUri(), loader)
      val node = result.models.single { it.name == "Node" }
      assertEquals(GeneratedTypeRef.named("Node"), node.properties.single { it.name == "self" }.type)
      assertEquals(
        GeneratedTypeRef.scalar("integer"),
        result.models
          .single { it.name == "Single" }
          .aliases
          .single(),
      )
      val snapshot = directory.resolve("snapshot")
      OpenApiDocumentSnapshot.write(snapshot, directory, resolution.documents)
      assertEquals(
        result,
        OpenApiToGeneratedApi().convert(source.toUri(), OpenApiDocumentSnapshot.loader(snapshot, directory)),
      )
      assertEquals(
        result,
        OpenApiToGeneratedApi().convert(source.toUri(), OpenApiDocumentLoader.create(options.copy(offline = true))),
      )
      assertEquals(
        setOf(
          "/start",
          "/schemas//node%20file.yaml?old=1",
          "/schemas//detail%20file.yaml?kind=%2F",
          "/schemas/detail%20file.yaml?kind=%2F",
        ),
        server.requests.toSet(),
      )
      assertEquals(4, server.requests.size)
      assertThrows(IOException::class.java) {
        OpenApiDocumentLoader
          .create(
            options.copy(offline = true),
          ).load(URI("${server.baseUri}schemas/node%20file.yaml?old=1"))
      }
    }
  }
}
