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
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class OpenApiNetworkPolicyTest {
  @ParameterizedTest
  @ValueSource(
    strings = [
      "0.0.0.0", "0.255.255.255", "10.0.0.1", "100.64.0.0", "100.127.255.255", "100.100.100.200",
      "127.0.0.1", "127.1", "2130706433", "169.254.169.254", "172.16.0.1", "172.31.255.255",
      "192.0.0.8", "192.0.0.170", "192.0.2.1", "192.88.99.2", "192.168.1.1", "198.18.0.1",
      "198.19.255.255", "198.51.100.1", "203.0.113.1", "224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255",
      "::", "::1", "::127.0.0.1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1",
      "64:ff9b::7f00:1", "64:ff9b:1::1", "100::1", "100:0:0:1::1", "2001::1", "2001:2::1",
      "2001:10::1", "2001:db8::1", "2002:7f00:1::", "3fff::1", "4000::1", "5f00::1",
      "fc00::1", "fd00::1", "fe80::1", "fec0::1", "ff02::1",
    ],
  )
  fun `rejects non public addresses in DNS answers and permits trusted networks`(address: String) {
    val uri = URI("https://schemas.example.test/model")
    val addresses = listOf(InetAddress.getByName(address))
    val options = OpenApiReferenceOptions()
    val failure =
      assertThrows(IOException::class.java) {
        OpenApiNetworkPolicy(options, { addresses }, null).validate(uri)
      }
    assertTrue(failure.message.orEmpty().contains("prohibited address"), failure.message)
    OpenApiNetworkPolicy(options.copy(allowPrivateNetwork = true), { addresses }, null).validate(uri)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "8.8.8.8", "100.63.255.255", "100.128.0.0", "172.15.255.255", "172.32.0.0", "192.0.0.9", "192.0.0.10",
      "192.31.196.1", "192.52.193.1", "198.17.255.255", "198.20.0.0", "223.255.255.255",
      "2606:4700:4700::1111", "64:ff9b::808:808", "2001:1::1", "2001:1::2", "2001:1::3",
      "2001:3::1", "2001:4:112::1", "2001:20::1", "2001:30::1", "2001:4860:4860::8888", "2620:4f:8000::1",
    ],
  )
  fun `permits public addresses and more specific global assignments`(address: String) {
    OpenApiNetworkPolicy(OpenApiReferenceOptions(), { listOf(InetAddress.getByName(address)) }, null)
      .validate(URI("https://schemas.example.test/model"))
  }

  @Test
  fun `checks mapped IPv6 answers without depending on InetAddress literal conversion`() {
    for (last in listOf(byteArrayOf(127, 0, 0, 1), byteArrayOf(8, 8, 8, 8))) {
      val mapped = Inet6Address.getByAddress(null, ByteArray(10) + byteArrayOf(-1, -1) + last, -1)
      val policy = OpenApiNetworkPolicy(OpenApiReferenceOptions(), { listOf(mapped) }, null)
      if (last[0] == 127.toByte()) {
        assertThrows(IOException::class.java) { policy.validate(URI("https://schemas.example.test/model")) }
      } else {
        policy.validate(URI("https://schemas.example.test/model"))
      }
    }
  }

  @Test
  fun `rejects mixed empty and failed DNS answers`() {
    val uri = URI("https://schemas.example.test/model")
    for (addresses in listOf(
      emptyList(),
      listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("::1")),
      listOf(InetAddress.getByName("10.0.0.1"), InetAddress.getByName("2606:4700:4700::1111")),
    )) {
      assertThrows(IOException::class.java) {
        OpenApiNetworkPolicy(OpenApiReferenceOptions(), { addresses }, null).validate(uri)
      }
    }
    val failure =
      assertThrows(IOException::class.java) {
        OpenApiNetworkPolicy(
          OpenApiReferenceOptions(),
          { throw UnknownHostException("unavailable") },
          null,
        ).validate(uri)
      }
    assertTrue(failure.message.orEmpty().contains(uri.toString()), failure.message)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "file:///tmp/schema", "https:/missing-host", "http://user:password@example.test/model",
      "http://example.test:65536/model",
    ],
  )
  fun `trusted mode retains URI validation before DNS`(value: String) {
    val policy =
      OpenApiNetworkPolicy(OpenApiReferenceOptions(allowPrivateNetwork = true), { error("DNS must not run") }, null)
    assertThrows(IOException::class.java) { policy.validate(URI(value)) }
  }

  @Test
  fun `DNS timeout does not wait for a resolver that ignores interruption`() {
    val release = CountDownLatch(1)
    val policy =
      OpenApiNetworkPolicy(OpenApiReferenceOptions(requestTimeout = Duration.ofMillis(50)), {
        while (release.count > 0) {
          try {
            release.await()
          } catch (_: InterruptedException) {
            // Simulate an uninterruptible native resolver without leaving work behind after the test.
          }
        }
        listOf(InetAddress.getByName("8.8.8.8"))
      }, null)
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(2)) {
        val failure =
          assertThrows(IOException::class.java) { policy.validate(URI("https://schemas.example.test/model")) }
        assertTrue(failure.message.orEmpty().contains("DNS resolution timed out"), failure.message)
      }
    } finally {
      release.countDown()
    }
  }

  @Test
  fun `loopback requests require opt in and cached documents need no network validation`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/schema", "type: string")
      val uri = server.baseUri.resolve("schema")
      val options = OpenApiReferenceOptions(directory)
      assertThrows(IOException::class.java) { OpenApiDocumentLoader.create(options).load(uri) }
      assertTrue(server.requests.isEmpty())
      val loaded = OpenApiDocumentLoader.create(options.copy(allowPrivateNetwork = true)).load(uri)
      assertEquals("type: string", String(loaded.bytes))
      server.requests.clear()
      val offline = options.copy(offline = true)
      val policy = OpenApiNetworkPolicy(offline, { error("Offline DNS") }, null)
      val cached = DefaultOpenApiDocumentLoader(offline, policy) { error("Offline HTTP client") }.load(uri)
      assertEquals(String(loaded.bytes), String(cached.bytes))
      assertTrue(server.requests.isEmpty())
    }
  }

  @Test
  fun `each redirect destination is checked before sending`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      server.respond("/entry", "", 302, mapOf("Location" to "/blocked"))
      server.respond("/blocked", "type: string")
      val options = OpenApiReferenceOptions(directory)
      val resolutions = AtomicInteger()
      val policy =
        OpenApiNetworkPolicy(options, {
          listOf(InetAddress.getByName(if (resolutions.getAndIncrement() == 0) "8.8.8.8" else "127.0.0.1"))
        }, null)
      val failure =
        assertThrows(IOException::class.java) {
          DefaultOpenApiDocumentLoader(options, policy).load(server.baseUri.resolve("entry"))
        }
      assertTrue(failure.message.orEmpty().contains("/blocked"), failure.message)
      assertEquals(listOf("/entry"), server.requests.toList())
      assertEquals(2, resolutions.get())
    }
  }

  @Test
  fun `configured proxies require opt in and are not changed globally`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { proxy ->
      proxy.respond("/schema", "type: string")
      val selector = ProxySelector.of(InetSocketAddress("127.0.0.1", proxy.baseUri.port))
      val original = ProxySelector.getDefault()
      val uri = URI("http://8.8.8.8/schema")
      val options = OpenApiReferenceOptions(directory)
      val failure =
        assertThrows(IOException::class.java) {
          DefaultOpenApiDocumentLoader(
            options,
            OpenApiNetworkPolicy(options, {
              error("DNS after blocked proxy")
            }, selector),
          ).load(uri)
        }
      assertTrue(failure.message.orEmpty().contains("Configured proxy"), failure.message)
      assertTrue(proxy.requests.isEmpty())
      val trusted = options.copy(allowPrivateNetwork = true)
      val loaded =
        DefaultOpenApiDocumentLoader(
          trusted,
          OpenApiNetworkPolicy(trusted, {
            listOf(InetAddress.getByName("8.8.8.8"))
          }, selector),
        ).load(uri)
      assertEquals("type: string", String(loaded.bytes))
      assertEquals(listOf(uri.toString()), proxy.requests.toList())
      assertEquals(original, ProxySelector.getDefault())
    }
  }

  @Test
  fun `proxy changes between preflight and connection remain blocked`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { proxy ->
      val selections = AtomicInteger()
      val selector =
        object : ProxySelector() {
          override fun select(uri: URI): List<Proxy> =
            if (selections.getAndIncrement() == 0) {
              listOf(Proxy.NO_PROXY)
            } else {
              listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.baseUri.port)))
            }

          override fun connectFailed(
            uri: URI,
            address: SocketAddress,
            failure: IOException,
          ) {}
        }
      val options = OpenApiReferenceOptions(directory)
      val policy = OpenApiNetworkPolicy(options, { listOf(InetAddress.getByName("8.8.8.8")) }, selector)
      val failure =
        assertThrows(IOException::class.java) {
          DefaultOpenApiDocumentLoader(options, policy).load(URI("http://8.8.8.8/schema"))
        }
      assertTrue(failure.message.orEmpty().contains("Configured proxy"), failure.message)
      assertTrue(proxy.requests.isEmpty())
      assertTrue(selections.get() >= 2)
    }
  }
}
