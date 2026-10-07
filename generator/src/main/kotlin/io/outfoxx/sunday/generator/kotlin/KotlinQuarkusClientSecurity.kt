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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedQuarkusSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSecurityBinding
import io.outfoxx.sunday.generator.ir.emit.GeneratedClientSecurity
import io.outfoxx.sunday.generator.ir.emit.credentialTransport
import java.net.URI
import java.security.MessageDigest

/** Projects a selected binding into native named OIDC clients and overridable deployment defaults. */
internal class KotlinQuarkusClientSecurity(
  private val packageName: String,
  private val profile: String?,
) {
  private val clients = linkedMapOf<String, String>()
  private val defaults = linkedMapOf<String, String>()
  private val nativeClients = linkedSetOf<String>()
  private val requiredExtensions = linkedSetOf<String>()

  fun annotation(security: GeneratedClientSecurity): AnnotationSpec? {
    if (security.bindings.isEmpty()) return null
    val name =
      security.requirement.schemes.singleOrNull()
        ?: genError(
          "Quarkus OIDC requires a single bearer scheme in the selected alternative; use a Sunday client for composite acquisition",
        )
    val binding = security.bindings.getValue(name)
    if (binding.provider?.matches(Regex("[A-Za-z0-9_.-]+")) != true) {
      genError(
        "Quarkus OIDC provider for '$name' must be a native client configuration identifier using letters, digits, dot, underscore, or hyphen",
      )
    }
    val transport = security.schemes.getValue(name).credentialTransport()
    if (transport.location != "header" ||
      !transport.name.equals("Authorization", true) ||
      !transport.prefix.equals("Bearer", true)
    ) {
      genError("Quarkus OIDC requires a bearer Authorization header for '$name'")
    }
    val native = binding.quarkus
    if (native != null) {
      when (native.mode) {
        GeneratedQuarkusSecurityBinding.Mode.ACQUIRE -> {
          requiredExtensions.add("io.quarkus.oidc.client.reactive.filter.OidcClientRequestReactiveFilter")
        }
        GeneratedQuarkusSecurityBinding.Mode.PROPAGATE, GeneratedQuarkusSecurityBinding.Mode.EXCHANGE -> {
          requiredExtensions.add("io.quarkus.oidc.token.propagation.reactive.AccessTokenRequestReactiveFilter")
        }
        else -> Unit
      }
    }
    if (native?.tenant != null) genError("Client bindings cannot select a server OIDC tenant")
    if (native != null &&
      native.mode !in
      setOf(
        GeneratedQuarkusSecurityBinding.Mode.ACQUIRE,
        GeneratedQuarkusSecurityBinding.Mode.PROPAGATE,
        GeneratedQuarkusSecurityBinding.Mode.EXCHANGE,
      )
    ) {
      genError("Quarkus client binding '$name' requires acquire, propagate, or exchange mode")
    }
    if (native?.mode in
      setOf(GeneratedQuarkusSecurityBinding.Mode.PROPAGATE, GeneratedQuarkusSecurityBinding.Mode.EXCHANGE)
    ) {
      if (binding.flow != GeneratedSecurityBinding.Flow.EXTERNAL) {
        genError("Quarkus token propagation/exchange requires external flow; host login is a separate server binding")
      }
      if (native?.mode == GeneratedQuarkusSecurityBinding.Mode.PROPAGATE) {
        if (binding.audience != null ||
          binding.resource != null
        ) {
          genError("Token propagation cannot change audience or resource; select exchange")
        }
        return AnnotationSpec.builder(ClassName("io.quarkus.oidc.token.propagation.common", "AccessToken")).build()
      }
    }
    val exchange = native?.mode == GeneratedQuarkusSecurityBinding.Mode.EXCHANGE
    if (!exchange && binding.flow != GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS) {
      genError(
        "Quarkus OIDC binding '$name' supports clientCredentials; use an explicit native propagation/exchange binding or a Sunday client",
      )
    }
    if (binding.refreshUrl != null && binding.refreshUrl != binding.tokenUrl) {
      genError(
        "Quarkus OIDC binding '$name' uses one native endpoint for acquisition and refresh; configure the same tokenUrl and refreshUrl",
      )
    }
    val scopes =
      security.requirement.permissions[name]
        .orEmpty()
        .distinct()
        .sorted()
    val identity = listOf(packageName, profile.orEmpty(), name, binding.toString()) + scopes
    val digest =
      MessageDigest
        .getInstance("SHA-256")
        .digest(
          identity.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8),
        ).take(12)
        .joinToString("") { "%02x".format(it) }
    val client = "sunday-$digest"
    clients[client] = binding.provider
    if (native != null) nativeClients.add(client)
    val prefix = "quarkus.oidc-client.\"$client\"."

    fun setting(
      key: String,
      value: String,
    ) {
      defaults[prefix + key] = value
    }
    val grant = if (exchange) "exchange" else "client"
    setting("grant.type", grant)
    // The native propagation filter reads this literal unquoted key instead of the OIDC config mapping.
    if (exchange) defaults["quarkus.oidc-client.$client.grant.type"] = grant
    setting("early-tokens-acquisition", "false")
    if (binding.discoveryUrl != null) {
      val discovery = URI(binding.discoveryUrl)
      if (discovery.rawQuery != null) genError("Quarkus OIDC discoveryUrl for '$name' cannot include a query")
      setting("auth-server-url", "${discovery.scheme}://${discovery.rawAuthority}")
      setting("discovery-path", discovery.rawPath)
      setting("discovery-enabled", "true")
    } else if (binding.tokenUrl != null) {
      setting("discovery-enabled", "false")
    }
    binding.tokenUrl?.let { setting("token-path", it) }
    binding.audience?.let { setting("grant-options.$grant.audience", it) }
    binding.resource?.let { setting("grant-options.$grant.resource", it) }
    if (scopes.isNotEmpty()) {
      setting(
        "scopes",
        scopes.joinToString(",") { it.replace("\\", "\\\\").replace(",", "\\,") },
      )
    }
    if (exchange) {
      return AnnotationSpec
        .builder(ClassName("io.quarkus.oidc.token.propagation.common", "AccessToken"))
        .addMember("exchangeTokenClient = %S", client)
        .build()
    }
    return AnnotationSpec
      .builder(
        if (native?.mode == GeneratedQuarkusSecurityBinding.Mode.ACQUIRE) {
          ClassName("io.quarkus.oidc.client.filter", "OidcClientFilter")
        } else {
          ClassName("io.outfoxx.sunday.client.quarkus", "ClientAuthentication")
        },
      ).addMember("%S", client)
      .build()
  }

  private fun registerRequirements(registry: KotlinTypeOutputRegistry) {
    val type =
      TypeSpec
        .classBuilder(ClassName(packageName, "OpenAPIClientRequirements"))
        .addKdoc("Fails startup when explicitly selected native extensions or provider configuration are missing.\n")
        .addAnnotation(ClassName("jakarta.inject", "Singleton"))
        .addAnnotation(ClassName("io.quarkus.runtime", "Startup"))
        .primaryConstructor(
          FunSpec
            .constructorBuilder()
            .addParameter("config", ClassName("org.eclipse.microprofile.config", "Config"))
            .build(),
        ).addInitializerBlock(
          CodeBlock
            .builder()
            .addStatement(
              "val required = listOf(%L)",
              requiredExtensions.map { CodeBlock.of("%S", it) }.joinToCode(", "),
            ).add(
              """
              for (extension in required) {
                require(runCatching { Class.forName(extension, false, javaClass.classLoader) }.isSuccess) {
                  "Missing required native Quarkus OIDC extension: " + extension
                }
              }
              """.trimIndent().replace(' ', '·') + "\n",
            ).addStatement(
              "val clients = listOf<String>(%L)",
              nativeClients.map { CodeBlock.of("%S", it) }.joinToCode(", "),
            ).add(
              """
              require(clients.isEmpty() || config.getOptionalValue("quarkus.oidc-client.enabled", Boolean::class.java).orElse(true)) {
                "Required native OIDC client feature is disabled"
              }
              for (client in clients) {
                fun setting(member: String): String? =
                  config.getOptionalValue("quarkus.oidc-client." + client + "." + member, String::class.java).orElse(null)
                    ?: config.getOptionalValue("quarkus.oidc-client.\"" + client + "\"." + member, String::class.java).orElse(null)
                require(!setting("client-id").isNullOrBlank()) { "Missing native OIDC client-id: " + client }
                require(!setting("auth-server-url").isNullOrBlank() || !setting("token-path").isNullOrBlank()) {
                  "Missing native OIDC client endpoint: " + client
                }
                require(setting("client-enabled") != "false") { "Required native OIDC client is disabled: " + client }
              }
              """.trimIndent().replace(' ', '·') + "\n",
            ).build(),
        )
    registry.addServiceType(ClassName(packageName, "OpenAPIClientRequirements"), type)
    registry.addBeanArchive()
  }

  fun register(registry: KotlinTypeOutputRegistry) {
    if (requiredExtensions.isNotEmpty()) registerRequirements(registry)
    if (clients.isEmpty()) return
    val factory = ClassName("io.smallrye.config", "ConfigSourceFactory")
    val context = ClassName("io.smallrye.config", "ConfigSourceContext")
    val source = ClassName("org.eclipse.microprofile.config.spi", "ConfigSource")
    val name = ClassName(packageName, "OpenAPIOidcConfiguration")
    val values = MAP.parameterizedBy(STRING, STRING)
    val runtimeSource = name.nestedClass("Source")
    val type =
      TypeSpec
        .classBuilder(name)
        .addKdoc(
          "Native OIDC defaults for the explicitly selected profile. " +
            "Application configuration overrides these defaults.\n" +
            "Supply credentials under the named provider's quarkus.oidc-client configuration. " +
            "Include generated META-INF/services resources.\n",
        ).addSuperinterface(factory)
        .addFunction(
          FunSpec
            .builder("getConfigSources")
            .addModifiers(KModifier.OVERRIDE)
            .addKdoc(
              "Copies application provider settings into isolated native clients " +
                "without changing wire security or server trust.\n",
            ).addParameter("context", context)
            .returns(ITERABLE.parameterizedBy(source))
            .addCode(
              "val clients = mapOf(%L)\n",
              clients
                .map { (key, value) ->
                  CodeBlock.of("%S to %S", key, value)
                }.joinToCode(",\n"),
            ).addCode(
              "val values = mutableMapOf(%L)\n",
              defaults
                .map { (key, value) ->
                  CodeBlock.of("%S to %S", key, value)
                }.joinToCode(",\n"),
            ).addCode(
              """
              val names = context.iterateNames().asSequence().toList()
              for ((client, provider) in clients) {
                val target = "quarkus.oidc-client.\"${'$'}client\"."
                val prefixes = listOf("quarkus.oidc-client.\"${'$'}provider\".", "quarkus.oidc-client.${'$'}provider.")
                for (key in names) {
                  val prefix = prefixes.firstOrNull { key.startsWith(it) } ?: continue
                  val member = key.removePrefix(prefix)
                  val value = context.getValue(key)?.value ?: continue
                  require(member != "grant.type" || value == values[target + "grant.type"]) {
                    "Selected OIDC provider has an incompatible grant: " + provider
                  }
                  if (member !in setOf("id", "scopes", "early-tokens-acquisition")) values[target + member] = value
                }
              }
              return listOf(Source(values))
              """.trimIndent(),
            ).build(),
        ).addType(
          TypeSpec
            .classBuilder(runtimeSource)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(source)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("values", values).build())
            .addProperty(
              PropertySpec.builder("values", values, KModifier.PRIVATE).initializer("values.toMap()").build(),
            ).addFunction(
              FunSpec
                .builder(
                  "getProperties",
                ).addModifiers(KModifier.OVERRIDE)
                .returns(values)
                .addStatement("return values")
                .build(),
            ).addFunction(
              FunSpec
                .builder(
                  "getPropertyNames",
                ).addModifiers(
                  KModifier.OVERRIDE,
                ).returns(SET.parameterizedBy(STRING))
                .addStatement("return values.keys")
                .build(),
            ).addFunction(
              FunSpec
                .builder(
                  "getValue",
                ).addModifiers(
                  KModifier.OVERRIDE,
                ).addParameter(
                  "name",
                  STRING,
                ).returns(STRING.copy(nullable = true))
                .addStatement("return values[name]")
                .build(),
            ).addFunction(
              FunSpec
                .builder(
                  "getName",
                ).addModifiers(KModifier.OVERRIDE)
                .returns(STRING)
                .addStatement("return %S", name.canonicalName)
                .build(),
            ).addFunction(
              FunSpec
                .builder("getOrdinal")
                .addModifiers(KModifier.OVERRIDE)
                .returns(INT)
                .addStatement("return 100")
                .build(),
            ).build(),
        )
    registry.addServiceType(name, type)
    registry.addServiceProvider(factory, name)
  }
}
