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

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.writeText

class OpenApiDocumentLoaderTest {
  @Test
  fun `query redirects preserve paths and query ids resolve without another fetch`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.handlers["/schemas/user file.yaml"] = { exchange ->
        if (exchange.requestURI.rawQuery == "v=1") {
          exchange.responseHeaders.add("Location", "?v=2#user")
          exchange.sendResponseHeaders(302, -1)
        } else {
          val body =
            """
            ${'$'}id: '?v=3'
            ${'$'}anchor: user
            type: object
            properties:
              self: {${'$'}ref: '?v=3#user'}
              detail: {${'$'}ref: 'detail%20file.yaml?mode=full#detail'}
            """.trimIndent().toByteArray()
          exchange.sendResponseHeaders(200, body.size.toLong())
          exchange.responseBody.write(body)
        }
      }
      server.respond(
        "/schemas/detail file.yaml",
        "${'$'}anchor: detail\ntype: object\nproperties: {id: {type: string}}",
      )
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Queries, version: 1.0.0}
        paths: {}
        components:
          schemas:
            User: {${'$'}ref: '${server.baseUri}schemas/user%20file.yaml?v=1#user'}
        """.trimIndent(),
      )
      val options =
        GeneratedApiIrOptions(
          openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
        )
      val result = OpenApiToGeneratedApi(options).convert(source.toUri())
      assertEquals(
        GeneratedTypeRef.named("User"),
        result.models
          .single { it.name == "User" }
          .properties
          .single {
            it.name ==
              "self"
          }.type,
      )
      assertEquals(
        listOf(
          "/schemas/user%20file.yaml?v=1",
          "/schemas/user%20file.yaml?v=2",
          "/schemas/detail%20file.yaml?mode=full",
        ),
        server.requests.toList(),
      )
      val offline =
        OpenApiToGeneratedApi(
          options.copy(openApiReferences = options.openApiReferences.copy(offline = true)),
        ).convert(source.toUri())
      assertEquals(result, offline)
      assertEquals(3, server.requests.size)
    }
  }

  @Test
  fun `loads HTTPS documents and rejects transport downgrades`(
    @TempDir directory: Path,
  ) {
    val store = directory.resolve("server.p12")
    val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
    val process =
      ProcessBuilder(
        keytool,
        "-genkeypair",
        "-keystore",
        store.toString(),
        "-storepass",
        "test-password",
        "-alias",
        "localhost",
        "-keyalg",
        "RSA",
        "-dname",
        "CN=localhost",
        "-validity",
        "1",
        "-ext",
        "SAN=ip:127.0.0.1",
        "-storetype",
        "PKCS12",
      ).redirectErrorStream(true).start()
    val output = String(process.inputStream.readAllBytes())
    assertEquals(0, process.waitFor(), output)
    val keyStore = KeyStore.getInstance("PKCS12")
    Files.newInputStream(store).use { keyStore.load(it, "test-password".toCharArray()) }
    val keys =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore, "test-password".toCharArray())
      }
    val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
    val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trust.trustManagers, null) }
    val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.httpsConfigurator = HttpsConfigurator(context)
    server.createContext("/schema") { exchange ->
      exchange.use {
        val body = "type: string".toByteArray()
        it.sendResponseHeaders(200, body.size.toLong())
        it.responseBody.write(body)
      }
    }
    server.createContext("/redirect") { exchange ->
      exchange.use {
        it.responseHeaders.add("Location", "http://127.0.0.1:1/schema")
        it.sendResponseHeaders(302, -1)
      }
    }
    server.start()
    try {
      val loader =
        DefaultOpenApiDocumentLoader(OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true)) {
          HttpClient
            .newBuilder()
            .sslContext(context)
            .proxy(it)
            .build()
        }
      val base = URI("HTTPS://127.0.0.1:${server.address.port}/")
      assertEquals("type: string", String(loader.load(base.resolve("schema")).bytes))
      val failure = assertThrows(IOException::class.java) { loader.load(base.resolve("redirect")) }
      assertTrue(failure.message.orEmpty().contains("HTTPS-to-HTTP"), failure.message)
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `names remote schemas whose URLs have no filename`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/", "type: object\nproperties:\n  id: {type: string}")
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Empty filename, version: 1.0.0}
        paths:
          /users:
            get:
              operationId: getUser
              responses:
                '200':
                  description: User
                  content:
                    application/json:
                      schema: {${'$'}ref: '${server.baseUri}'}
        """.trimIndent(),
      )
      val api =
        OpenApiToGeneratedApi(
          GeneratedApiIrOptions(
            openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
          ),
        ).convert(source.toUri())
      assertEquals("Schema", api.models.single().name)
    }
  }

  @Test
  fun `revalidates with validators once per session and reuses bodies offline`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val count = AtomicInteger()
      server.handlers["/schema"] = { exchange ->
        if (count.getAndIncrement() == 0) {
          exchange.responseHeaders.add("ETag", "\"v1\"")
          exchange.responseHeaders.add("Last-Modified", "Wed, 09 Sep 2026 00:00:00 GMT")
          val bytes = "type: string".toByteArray()
          exchange.sendResponseHeaders(200, bytes.size.toLong())
          exchange.responseBody.write(bytes)
        } else {
          assertEquals("\"v1\"", exchange.requestHeaders.getFirst("If-None-Match"))
          assertEquals("Wed, 09 Sep 2026 00:00:00 GMT", exchange.requestHeaders.getFirst("If-Modified-Since"))
          exchange.sendResponseHeaders(304, -1)
        }
      }
      val options = OpenApiReferenceOptions(directory, allowPrivateNetwork = true)
      val uri = server.baseUri.resolve("schema")
      val first = OpenApiDocumentLoader.create(options)
      val bytes = first.load(uri).bytes
      assertArrayEquals(bytes, first.load(uri).bytes)
      assertEquals(1, count.get())
      assertArrayEquals(bytes, OpenApiDocumentLoader.create(options).load(uri).bytes)
      assertEquals(2, count.get())
      assertArrayEquals(bytes, OpenApiDocumentLoader.create(options.copy(offline = true)).load(uri).bytes)
      assertEquals(2, count.get())
      assertThrows(IOException::class.java) {
        OpenApiDocumentLoader.create(options.copy(offline = true)).load(server.baseUri.resolve("missing"))
      }
    }
  }

  @Test
  fun `handles unchanged and changed 200 responses without validators`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val options = OpenApiReferenceOptions(directory, allowPrivateNetwork = true)
      val uri = server.baseUri.resolve("schema?version=1")
      server.respond("/schema", "type: string")
      val first = OpenApiDocumentLoader.create(options).load(uri)
      val second = OpenApiDocumentLoader.create(options).load(uri)
      assertArrayEquals(first.bytes, second.bytes)
      server.respond("/schema", "type: integer")
      assertEquals("type: integer", String(OpenApiDocumentLoader.create(options).load(uri).bytes))
      assertEquals(3, server.requests.size)
    }
  }

  @Test
  fun `retains redirect bases and resolves remote ids and anchors`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val api = server.export(directory)
      assertEquals(
        setOf(
          "User",
          "Address",
          "Node",
          "UserExtendedAddress",
          "ScalarRestrictionsBase",
          "ScalarRestrictions",
          "ScalarWireBase",
          "ScalarWireChild",
          "ScalarWireState",
          "AddFactOp",
          "BadRequestProblem",
          "BaseNarrativeChangeEvent",
          "CharacterChangeEvent",
          "CurrentAsset",
          "CurrentAssetState",
          "DeleteFactOp",
          "EntityDetails",
          "FactEditOp",
          "HttpProblem",
          "NarrativeChangeEventType",
          "RefusedAsset",
          "RenderedAsset",
          "SdkMappedPet",
          "SdkMappedCat",
          "SdkWrappedCat",
          "SdkAliasBase",
          "SdkAliasChain",
          "SdkAliasChild",
          "SdkAliasWrapper",
          "SdkByteBase",
          "SdkByteRestrictions",
          "SdkBytes",
          "SdkConflictingChild",
          "SdkDefaultChild",
          "SdkDefaultParent",
          "SdkEnvelope",
          "SdkFirstParent",
          "SdkFirstStatus",
          "SdkInlineAliasChild",
          "SdkInlineBase",
          "SdkInlineBaseDetail",
          "SdkInlineBaseSelection",
          "SdkInlineChild",
          "SdkInlineMiddle",
          "SdkInlineNode",
          "SdkIntegerBase",
          "SdkIntegerChild",
          "SdkMultiChild",
          "SdkMultiFirst",
          "SdkMultiReversed",
          "SdkMultiSecond",
          "SdkSecondParent",
          "SdkSecondStatus",
          "SdkTemporalBase",
          "SdkTemporalChild",
          "SdkTimestamp",
          "UserProfile",
          "UserProfile2",
          "MaybeAddress",
          "AnnotatedNode",
          "Measurement",
          "Limit",
          "NullableText",
          "NullableValues",
          "Nullability",
          "cat",
          "BooleanValues",
          "BaseRecord",
          "DocumentedRecord",
          "RecordNode",
          "Anything",
          "Empty",
          "Unbounded",
          "Cat2",
          "Dog",
          "Pet",
          "Pets",
          "MappedPet",
          "MappedPets",
          "MappedCat",
          "MappedDog",
          "Restrictions",
          "State",
          "Text",
        ),
        api.models.map { it.name }.toSet(),
      )
      val properties =
        api.models
          .single { it.name == "User" }
          .properties
          .associateBy { it.name }
      assertEquals(properties.getValue("address").type, properties.getValue("repeated").type)
      assertEquals(GeneratedTypeRef.named("UserProfile2"), properties.getValue("profile").type)
      assertEquals(GeneratedTypeRef.named("UserProfile"), properties.getValue("externalProfile").type)
      assertEquals(GeneratedTypeRef.scalar("any"), properties.getValue("arbitrary").type)
      assertEquals(GeneratedTypeRef.scalar("any", nullable = true), properties.getValue("nullableArbitrary").type)
      assertEquals(
        listOf(
          "/redirect",
          "/schemas/user.yaml",
          "/schemas/mapped-cat?schema=cat",
          "/schemas/mapped-dog",
          "/schemas/address.yaml",
          "/schemas/user-profile.yaml",
        ),
        server.requests.toList(),
      )
    }
  }

  @Test
  fun `fetches intermediate redirect aliases only once per session`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/a", "", 302, mapOf("Location" to "/b"))
      server.respond("/b", "", 302, mapOf("Location" to "/c"))
      server.respond("/c", "type: string", headers = mapOf("ETag" to "\"v1\""))
      val loader = OpenApiDocumentLoader.create(OpenApiReferenceOptions(directory, allowPrivateNetwork = true))
      for (alias in listOf("a", "b", "c", "a")) {
        val document = loader.load(server.baseUri.resolve(alias))
        assertEquals(server.baseUri.resolve("c"), document.uri)
        assertEquals("type: string", String(document.bytes))
      }
      assertEquals(listOf("/a", "/b", "/c"), server.requests.toList())
      server.handlers["/c"] = { exchange ->
        assertEquals("\"v1\"", exchange.requestHeaders.getFirst("If-None-Match"))
        exchange.sendResponseHeaders(304, -1)
      }
      assertEquals(
        "type: string",
        String(
          OpenApiDocumentLoader
            .create(
              OpenApiReferenceOptions(directory, allowPrivateNetwork = true),
            ).load(server.baseUri.resolve("b"))
            .bytes,
        ),
      )
    }
  }

  @Test
  fun `online failures never fall back to a cached document`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val options = OpenApiReferenceOptions(directory, allowPrivateNetwork = true)
      val uri = server.baseUri.resolve("schema")
      server.respond("/schema", "type: string")
      OpenApiDocumentLoader.create(options).load(uri)
      server.respond("/schema", "unavailable", 503)
      assertTrue(
        assertThrows(IOException::class.java) {
          OpenApiDocumentLoader.create(options).load(uri)
        }.message.orEmpty().contains("503"),
      )
      server.respond("/schema", "broken: [")
      assertThrows(Exception::class.java) { OpenApiDocumentLoader.create(options).load(uri) }
      assertEquals("type: string", String(OpenApiDocumentLoader.create(options.copy(offline = true)).load(uri).bytes))
    }
  }

  @Test
  fun `bounds redirect chains and document bodies`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/a", "", 302, mapOf("Location" to "/b"))
      server.respond("/b", "", 302, mapOf("Location" to "/c"))
      server.respond("/c", "type: string")
      val options =
        OpenApiReferenceOptions(directory, allowPrivateNetwork = true, maximumRedirects = 1, maximumDocumentBytes = 32)
      assertTrue(
        assertThrows(IOException::class.java) {
          OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("a"))
        }.message.orEmpty().contains("redirects"),
      )
      server.respond("/large", "x".repeat(33))
      assertTrue(
        assertThrows(IOException::class.java) {
          OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("large"))
        }.message.orEmpty().contains("exceeds 32"),
      )
      server.respond("/cycle", "", 302, mapOf("Location" to "/cycle"))
      assertThrows(
        IOException::class.java,
      ) { OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("cycle")) }
      server.respond("/file", "", 302, mapOf("Location" to directory.resolve("secret.yaml").toUri().toString()))
      assertThrows(
        IOException::class.java,
      ) { OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("file")) }
    }
  }

  @Test
  fun `request timeout also bounds a stalled response body`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.handlers["/slow"] = { exchange ->
        exchange.sendResponseHeaders(200, 100)
        exchange.responseBody.write('x'.code)
        exchange.responseBody.flush()
        Thread.sleep(500)
      }
      val options =
        OpenApiReferenceOptions(directory, allowPrivateNetwork = true, requestTimeout = Duration.ofMillis(100))
      assertTrue(
        assertThrows(IOException::class.java) {
          OpenApiDocumentLoader.create(options).load(server.baseUri.resolve("slow"))
        }.message.orEmpty().contains("timed out"),
      )
    }
  }

  @Test
  fun `parallel loaders coordinate persistent cache writes`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/schema", "type: string")
      val options = OpenApiReferenceOptions(directory, allowPrivateNetwork = true)
      val uri = server.baseUri.resolve("schema")
      Executors.newFixedThreadPool(4).use { executor ->
        val results =
          (1..8).map {
            CompletableFuture.supplyAsync({ OpenApiDocumentLoader.create(options).load(uri) }, executor)
          }
        results.forEach { assertEquals("type: string", String(it.get().bytes)) }
      }
      assertEquals("type: string", String(OpenApiDocumentLoader.create(options.copy(offline = true)).load(uri).bytes))
    }
  }

  @Test
  fun `snapshot captures immutable content and relocates local files`(
    @TempDir directory: Path,
  ) {
    val source =
      directory.resolve("api.yaml").also {
        it.writeText("openapi: 3.1.0\ninfo: {title: Snapshot, version: 1}\npaths: {}")
      }
    val resolution = OpenApiReferenceResolver().resolve(source.toUri())
    val snapshot = directory.resolve("snapshot")
    OpenApiDocumentSnapshot.write(snapshot, directory, resolution.documents)
    val before = Files.getLastModifiedTime(snapshot.resolve("manifest.yaml"))
    OpenApiDocumentSnapshot.write(snapshot, directory, resolution.documents)
    assertEquals(before, Files.getLastModifiedTime(snapshot.resolve("manifest.yaml")))
    source.writeText("invalid: [")
    val movedBase = directory.resolve("relocated")
    val loader = OpenApiDocumentSnapshot.loader(snapshot, movedBase)
    val api = GeneratedApiIrExporter(openApiDocumentLoader = loader).export(movedBase.resolve("api.yaml").toUri())
    assertEquals("Snapshot", api.name)
    assertThrows(IOException::class.java) { loader.load(URI("https://example.test/missing")) }
  }

  @Test
  fun `remote errors identify the referring source`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val root = directory.resolve("api.yaml")
      root.writeText(
        "openapi: 3.1.0\ninfo: {title: Error, version: 1}\npaths: {}\ncomponents:\n  schemas:\n    Missing: {\$ref: '${server.baseUri}missing'}",
      )
      val failure =
        assertThrows(GenerationException::class.java) {
          OpenApiToGeneratedApi(
            GeneratedApiIrOptions(
              openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
            ),
          ).convert(root.toUri())
        }
      assertEquals(root.toUri().toString(), failure.file)
      assertEquals(6, failure.line)
      assertTrue(failure.message.orEmpty().contains("404"), failure.message)
    }
  }
}
