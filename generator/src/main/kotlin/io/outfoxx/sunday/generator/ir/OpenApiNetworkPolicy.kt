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

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** DNS preflight and proxy checks; the JDK client still resolves independently when connecting. */
internal class OpenApiNetworkPolicy(
  private val options: OpenApiReferenceOptions,
  private val resolveAddresses: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
  private val proxySelector: ProxySelector? = ProxySelector.getDefault(),
) : ProxySelector() {

  fun validate(uri: URI) {
    if (uri.scheme?.lowercase() !in setOf("http", "https") ||
      uri.host.isNullOrBlank() ||
      uri.userInfo != null ||
      uri.port !in -1..65535
    ) {
      throw IOException("Unsupported OpenAPI HTTP destination '$uri'")
    }
    select(uri)
    val addresses = resolve(uri)
    if (addresses.isEmpty()) throw IOException("OpenAPI DNS resolution returned no addresses for '$uri'")
    if (!options.allowPrivateNetwork) {
      addresses.firstOrNull { !isPublicAddress(it.address) }?.let { address ->
        throw IOException(
          "OpenAPI destination '$uri' resolves to prohibited address '${address.hostAddress}'; " +
            "enable allowPrivateNetwork (--openapi-allow-private-network) only for trusted specifications",
        )
      }
    }
  }

  override fun select(uri: URI): List<Proxy> {
    val proxies = proxySelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
    if (!options.allowPrivateNetwork && proxies.any { it.type() != Proxy.Type.DIRECT }) {
      throw IOException(
        "Configured proxy for OpenAPI destination '$uri' requires allowPrivateNetwork " +
          "(--openapi-allow-private-network); enable it only for trusted specifications",
      )
    }
    return proxies
  }

  override fun connectFailed(
    uri: URI,
    address: SocketAddress,
    failure: IOException,
  ) {
    proxySelector?.connectFailed(uri, address, failure)
  }

  private fun resolve(uri: URI): List<InetAddress> {
    val task = FutureTask { resolveAddresses(uri.host) }
    // Native DNS may ignore interruption. A daemon virtual thread lets timeout return without waiting for it.
    Thread.ofVirtual().name("sunday-openapi-dns").start(task)
    try {
      return task.get(options.requestTimeout.toNanos(), TimeUnit.NANOSECONDS)
    } catch (exception: TimeoutException) {
      throw IOException("OpenAPI DNS resolution timed out for '$uri'", exception)
    } catch (exception: ExecutionException) {
      throw IOException("Cannot resolve OpenAPI destination '$uri': ${exception.cause?.message}", exception.cause)
    } catch (exception: InterruptedException) {
      Thread.currentThread().interrupt()
      throw IOException("Interrupted resolving OpenAPI destination '$uri'", exception)
    } finally {
      task.cancel(true)
    }
  }

  private fun isPublicAddress(address: ByteArray): Boolean =
    when (address.size) {
      4 -> publicIpv4Exceptions.any { it.contains(address) } || prohibitedIpv4.none { it.contains(address) }
      16 ->
        when {
          mappedIpv4.contains(
            address,
          ) ||
            translatedIpv4.contains(address) -> isPublicAddress(address.copyOfRange(12, 16))
          publicIpv6Exceptions.any { it.contains(address) } -> true
          else -> globalIpv6.contains(address) && prohibitedIpv6.none { it.contains(address) }
        }
      else -> false
    }

  private class Network(
    cidr: String,
  ) {
    private val bytes =
      InetAddress.getByName(cidr.substringBefore('/')).address.let { address ->
        // InetAddress collapses mapped IPv6 literals to four bytes; retain their prefix for IPv6 DNS answers.
        if (':' in cidr && address.size == 4) ByteArray(10) + byteArrayOf(-1, -1) + address else address
      }
    private val bits = cidr.substringAfter('/').toInt()

    fun contains(address: ByteArray): Boolean {
      if (address.size != bytes.size) return false
      val whole = bits / 8
      if ((0 until whole).any { address[it] != bytes[it] }) return false
      val remaining = bits % 8
      if (remaining == 0) return true
      val mask = (0xff shl (8 - remaining)) and 0xff
      return (address[whole].toInt() and mask) == (bytes[whole].toInt() and mask)
    }
  }

  private companion object {
    // IANA special-purpose registries, checked 2026-09-12. More-specific globally reachable entries override
    // their enclosing reservations. Multicast and IPv6 outside global unicast are also excluded.
    // https://www.iana.org/assignments/iana-ipv4-special-registry
    // https://www.iana.org/assignments/iana-ipv6-special-registry
    val prohibitedIpv4 =
      listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.0.0/24",
        "192.0.2.0/24",
        "192.88.99.0/24",
        "192.168.0.0/16",
        "198.18.0.0/15",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "224.0.0.0/3",
      ).map(::Network)
    val publicIpv4Exceptions = listOf("192.0.0.9/32", "192.0.0.10/32").map(::Network)
    val globalIpv6 = Network("2000::/3")
    val prohibitedIpv6 = listOf("2001::/23", "2001:db8::/32", "2002::/16", "3fff::/20").map(::Network)
    val publicIpv6Exceptions =
      listOf(
        "2001:1::1/128",
        "2001:1::2/128",
        "2001:1::3/128",
        "2001:3::/32",
        "2001:4:112::/48",
        "2001:20::/28",
        "2001:30::/28",
      ).map(::Network)
    val mappedIpv4 = Network("::ffff:0:0/96")
    val translatedIpv4 = Network("64:ff9b::/96")
  }
}
