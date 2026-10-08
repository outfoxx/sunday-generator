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

package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiFragment
import io.outfoxx.sunday.generator.ir.GeneratedEnvironment
import io.outfoxx.sunday.generator.ir.GeneratedIdentity
import io.outfoxx.sunday.generator.ir.GeneratedQuarkusConfig
import io.outfoxx.sunday.generator.ir.GeneratedQuarkusSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.utils.GeneratedProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

@KotlinTest
class GeneratedPropertiesTest {
  @Test
  fun `properties preserve expressions unicode and list escapes`(
    @TempDir root: Path,
  ) {
    val values =
      mapOf(
        "quoted.\"client\".url" to "${'$'}{endpoint:https://host/path?a=b}",
        "scopes" to "one\\,two,three",
        "unicode" to "héllo\nworld",
      )
    GeneratedProperties.write(root, "config/client.properties", values)
    val file = root.resolve("config/client.properties")
    val parsed = Properties().apply { Files.newBufferedReader(file).use { load(it) } }
    expectThat(parsed.entries.associate { it.key.toString() to it.value.toString() }).isEqualTo(
      values + ("config_ordinal" to "100"),
    )
    expectThat(
      GeneratedProperties.render(values),
    ).isEqualTo(
      GeneratedProperties.render(
        values.entries.reversed().associate {
          it.toPair()
        },
      ),
    )
    expectCatching { GeneratedProperties.write(root, "config/client.properties", values) }.isFailure()
  }

  @Test
  fun `merge accepts compatible defaults and rejects conflicts`() {
    val values = mutableMapOf("one" to "1")
    GeneratedProperties.merge(values, mapOf("one" to "1", "two" to "2"), "first and second")
    expectThat(values.toMap()).isEqualTo(mapOf("one" to "1", "two" to "2"))
    expectCatching { GeneratedProperties.merge(values, mapOf("one" to "2"), "first and second") }.isFailure()
  }

  @Test
  fun `properties cannot escape output through symlinks`(
    @TempDir root: Path,
  ) {
    val outside = Files.createDirectory(root.resolve("outside"))
    val output = Files.createDirectory(root.resolve("output"))
    Files.createSymbolicLink(output.resolve("config"), outside)
    expectCatching { GeneratedProperties.write(output, "config/client.properties", emptyMap()) }.isFailure()
    val linkedRoot = Files.createSymbolicLink(root.resolve("linked-root"), outside)
    expectCatching { GeneratedProperties.write(linkedRoot, "client.properties", emptyMap()) }.isFailure()
  }

  @Test
  fun `native metadata resolves inheritance and peer conflicts with provenance`() {
    fun scope(value: String) =
      io.outfoxx.sunday.generator.ir
        .GeneratedQuarkusConfig(mapOf("key" to value))
    val parent =
      GeneratedEnvironment(
        all = scope("shared"),
        client = scope("client"),
        profiles =
          mapOf(
            "internal" to
              GeneratedEnvironment
                .Scope(client = scope("profile")),
          ),
      )
    val local =
      parent.inherit(
        io.outfoxx.sunday.generator.ir
          .GeneratedEnvironment(all = scope("local")),
      )
    val context =
      GenerationContext(
        GenerationMode.Client,
        "internal",
      )
    expectThat(local.resolve(context) { a, b -> a.merge(b) }?.properties).isEqualTo(mapOf("key" to "local"))
    expectThat(parent.resolve(context) { a, b -> a.merge(b) }?.properties).isEqualTo(mapOf("key" to "profile"))
    expectThat(parent.resolve(context.copy(profile = "external")) { a, b -> a.merge(b) }?.properties).isEqualTo(
      mapOf(
        "key" to "client",
      ),
    )

    fun fragment(
      location: String,
      config: GeneratedQuarkusConfig,
    ) = GeneratedApiFragment(
      GeneratedApi(
        name = "contract",
        source =
          GeneratedSourceSpec(
            GeneratedSourceSpec.Kind.OPENAPI,
            location,
          ),
        quarkusConfig =
          io.outfoxx.sunday.generator.ir
            .GeneratedEnvironment(client = config),
      ),
      GeneratedIdentity
        .explicit("contract"),
    )
    val composer =
      io.outfoxx.sunday.generator.ir
        .GeneratedApiComposer()
    val first = fragment("first.yaml", scope("one"))
    val compatible =
      fragment(
        "second.yaml",
        GeneratedQuarkusConfig(
          mapOf(
            "extra" to "two",
          ),
        ),
      )
    expectThat(
      composer
        .compose(listOf(first, compatible))
        .quarkusConfig
        ?.client
        ?.properties,
    ).isEqualTo(mapOf("key" to "one", "extra" to "two"))
    val error =
      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
        composer.compose(listOf(first, fragment("second.yaml", scope("two"))))
      }
    org.junit.jupiter.api.Assertions.assertTrue(
      error.message!!.contains("first.yaml") && error.message!!.contains("second.yaml"),
    )
  }

  @Test
  fun `native metadata rejects unknown fields invalid values and cross resource conflicts`() {
    val invalid =
      listOf(
        mapOf("unknown" to emptyMap<String, String>()),
        mapOf("client" to mapOf("server" to true)),
        mapOf("client" to mapOf("properties" to mapOf("key" to listOf("nested")))),
        mapOf("profiles" to mapOf("bad" to mapOf("required" to true))),
      )
    invalid.forEach { value ->
      expectCatching {
        GeneratedQuarkusConfig
          .read(value, "contract.yaml")
      }.isFailure()
    }
    for (mode in listOf(
      GeneratedQuarkusSecurityBinding.Mode.PROVIDER,
      GeneratedQuarkusSecurityBinding.Mode.PROPAGATE,
    )) {
      expectCatching {
        GeneratedQuarkusSecurityBinding(mode, properties = mapOf("refresh-token-time-skew" to "5S"))
      }.isFailure()
      expectCatching {
        GeneratedQuarkusSecurityBinding(
          mode,
          providerProperties = listOf("tls.verification"),
        )
      }.isFailure()
    }
    val server = GenerationMode.Server
    val client = GenerationMode.Client
    for ((key, role) in listOf(
      "quarkus.zanzibar.filter.enabled" to client,
      "quarkus.rest-client.read-timeout" to server,
      "quarkus.config.locations" to client,
      "quarkus.zanzibar.filter.unknown" to server,
    )) {
      expectCatching { KotlinQuarkusProperties.global(key, "true", role) }.isFailure()
    }
    for (key in listOf(
      "client-id",
      "credentials.secret",
      "credentials.jwt.key",
      "proxy.password",
      "headers.Authorization",
      "grant-options.exchange.subject_token",
    )) {
      expectCatching { KotlinQuarkusProperties.suffix(key, "embedded", client) }.isFailure()
      KotlinQuarkusProperties.suffix(key, "${'$'}{runtime.value}", client)
    }
    val registry = KotlinTypeRegistry("test", null, server, emptySet())
    registry.addProperties("first.properties", mapOf("quarkus.zanzibar.filter.enabled" to "true"), "first.yaml")
    val error =
      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
        registry.addProperties("second.properties", mapOf("quarkus.zanzibar.filter.enabled" to "false"), "second.yaml")
      }
    org.junit.jupiter.api.Assertions.assertTrue(
      error.message!!.contains("first.yaml") && error.message!!.contains("second.yaml"),
    )
  }
}
