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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedQuarkusSecurityBinding
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy
import io.outfoxx.sunday.generator.ir.emit.credentialTransport

/** Native OIDC endpoint gates with explicit tenant evidence and deployment trust requirements. */
internal class KotlinQuarkusNativeSecurity(
  private val name: ClassName,
  policies: List<GeneratedEndpointPolicy>,
) {
  private val protected = policies.filter { it.requirements.isNotEmpty() }
  private val selections = protected.associateWith(::selection)
  private val bindings = selections.values.distinct()

  fun register(registry: KotlinTypeOutputRegistry) {
    bindings.groupBy { it.tenant }.forEach { (tenant, values) ->
      if (values.map { it.mode }.distinct().size !=
        1
      ) {
        genError("Conflicting native OIDC application types for tenant '$tenant'")
      }
    }
    val defaults =
      buildMap {
        bindings.forEach { binding ->
          val prefix = "quarkus.oidc." + (binding.tenant?.let { "$it." } ?: "")
          put(
            prefix + "application-type",
            if (binding.mode ==
              GeneratedQuarkusSecurityBinding.Mode.WEB_APP
            ) {
              "web-app"
            } else {
              "service"
            },
          )
          if (binding.mode == GeneratedQuarkusSecurityBinding.Mode.WEB_APP) {
            put(prefix + "authentication.pkce-required", "true")
            put(prefix + "authentication.nonce-required", "true")
            put(prefix + "authentication.verify-access-token", "true")
            put(prefix + "roles.source", "accesstoken")
          }
          if (binding.tenant != null) put("quarkus.http.auth.proactive", "false")
          require(binding.providerProperties.isEmpty()) { "Server bindings cannot alias client provider properties" }
          binding.properties.forEach { (key, value) ->
            KotlinQuarkusProperties.suffix(key, value, GenerationMode.Server)
            require(
              binding.mode == GeneratedQuarkusSecurityBinding.Mode.WEB_APP ||
                !key.startsWith("authentication.") &&
                !key.startsWith("logout.") &&
                !key.startsWith("token.refresh"),
            ) {
              "Native OIDC property '$key' requires webApp mode"
            }
            require(key != "application-type" || value == get(prefix + key)) { "Conflicting native application-type" }
            require(prefix + key !in this || get(prefix + key) == value) { "Conflicting native property '$prefix$key'" }
            put(prefix + key, value)
          }
        }
      }
    val metadata = registry.applicationMetadata
    if (metadata.enabled && metadata.serverConfiguration) {
      registry.addProperties(metadata.serverConfigurationFileName, defaults, name.canonicalName)
    }
  }

  private fun selection(policy: GeneratedEndpointPolicy): GeneratedQuarkusSecurityBinding {
    val requirement =
      policy.requirements.singleOrNull()
        ?: genError(
          "Native Quarkus OIDC requires one security alternative; use shared providers for composite policies",
        )
    val scheme =
      requirement.schemes.singleOrNull()
        ?: genError("Native Quarkus OIDC requires one scheme; use shared providers for AND requirements")
    val binding =
      policy.bindings[scheme]?.quarkus
        ?: genError("Every protected scheme in a native Quarkus package must declare its native binding")
    if (binding.mode !in
      setOf(GeneratedQuarkusSecurityBinding.Mode.OIDC, GeneratedQuarkusSecurityBinding.Mode.WEB_APP)
    ) {
      genError("Cannot mix native OIDC and generated provider bindings in one service package")
    }
    if (binding.tenant != null && !binding.tenant.matches(Regex("[A-Za-z0-9_-]+"))) {
      genError("Quarkus tenant must contain only letters, digits, underscore, or hyphen")
    }
    val transport = policy.schemes.getValue(scheme).credentialTransport()
    if (transport.location != "header" ||
      !transport.name.equals("Authorization", true) ||
      !transport.prefix.equals("Bearer", true)
    ) {
      genError("Native Quarkus OIDC requires a bearer wire scheme")
    }
    return binding
  }

  fun annotations(policy: GeneratedEndpointPolicy): List<AnnotationSpec> {
    if (policy.requirements.isEmpty()) {
      return listOf(
        AnnotationSpec.builder(ClassName("jakarta.annotation.security", "PermitAll")).build(),
      )
    }
    val binding = selections.getValue(policy)
    return buildList {
      binding.tenant?.let {
        add(AnnotationSpec.builder(ClassName("io.quarkus.oidc", "Tenant")).addMember("%S", it).build())
      }
      add(
        AnnotationSpec
          .builder(ClassName("io.quarkus.vertx.http.security", "AuthorizationPolicy"))
          .addMember("name = %S", policyName(policy).canonicalName)
          .build(),
      )
    }
  }

  private fun policyName(policy: GeneratedEndpointPolicy): ClassName {
    val binding = selections.getValue(policy)
    return name.peerClass(
      "OpenAPIOidcPolicy_" + (binding.tenant?.let { "Tenant_$it" } ?: "Default") + "_" +
        binding.mode.name + "_" + KotlinQuarkusSecurityPlan.policy(policy).id,
    )
  }

  fun generate(): Map<ClassName, TypeSpec.Builder> =
    protected.associate { endpoint ->
      val binding = selections.getValue(endpoint)
      val type = policyName(endpoint)
      val scopes =
        endpoint.requirements
          .single()
          .permissions.values
          .flatten()
          .distinct()
          .sorted()
      val policy = ClassName("io.quarkus.vertx.http.runtime.security", "HttpSecurityPolicy")
      val result = policy.nestedClass("CheckResult")
      val identity = ClassName("io.quarkus.security.identity", "SecurityIdentity")
      val uni = ClassName("io.smallrye.mutiny", "Uni")
      val config = ClassName("org.eclipse.microprofile.config", "Config")
      val prefix = "quarkus.oidc." + (binding.tenant?.let { "$it." } ?: "")
      val mode = if (binding.mode == GeneratedQuarkusSecurityBinding.Mode.WEB_APP) "web-app" else "service"
      type to
        TypeSpec
          .classBuilder(type)
          .addKdoc("Checks native OIDC identity provenance before delegation; Quarkus owns credential validation.\n")
          .addAnnotation(ClassName("jakarta.inject", "Singleton"))
          .addAnnotation(ClassName("io.quarkus.runtime", "Startup"))
          .addSuperinterface(policy)
          .primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addParameter("config", config)
              .addParameter(
                ParameterSpec
                  .builder("oidc", ClassName("io.quarkus.oidc", "TenantIdentityProvider"))
                  .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNUSED_PARAMETER").build())
                  .build(),
              ).build(),
          ).addInitializerBlock(
            CodeBlock
              .builder()
              .addStatement("val prefix = %S", prefix)
              .add(
                """
                fun setting(member: String): String? = config.getOptionalValue(prefix + member, String::class.java).orElse(null)
                require(!setting("auth-server-url").isNullOrBlank() || !setting("public-key").isNullOrBlank()) { "Missing OIDC auth-server-url or public-key: " + prefix }
                require(!setting("token.issuer").isNullOrBlank() && setting("token.issuer") != "any") { "Explicit trusted OIDC issuer required: " + prefix }
                require(!setting("token.audience").isNullOrBlank()) { "Explicit trusted OIDC audience required: " + prefix }
                require(setting("tenant-enabled") != "false") { "Required OIDC tenant is disabled: " + prefix }
                """.trimIndent().replace(' ', '·') + "\n",
              ).addStatement(
                "require((setting(%S) ?: %S) == %S) { %S + prefix }",
                "application-type",
                "service",
                mode,
                "Incorrect OIDC application-type: ",
              ).apply {
                if (binding.tenant !=
                  null
                ) {
                  addStatement(
                    "require(!config.getOptionalValue(%S, Boolean::class.java).orElse(true)) { %S }",
                    "quarkus.http.auth.proactive",
                    "Endpoint tenant selection requires quarkus.http.auth.proactive=false",
                  )
                }
                if (binding.mode == GeneratedQuarkusSecurityBinding.Mode.WEB_APP) {
                  addStatement(
                    "require(setting(%S) == %S && setting(%S) == %S) { %S }",
                    "authentication.pkce-required",
                    "true",
                    "authentication.nonce-required",
                    "true",
                    "Native web-app login requires PKCE and nonce",
                  )
                  addStatement(
                    "require(setting(%S) == %S && setting(%S) == %S) { %S }",
                    "authentication.verify-access-token",
                    "true",
                    "roles.source",
                    "accesstoken",
                    "Native web-app scope checks require verified access tokens",
                  )
                }
              }.build(),
          ).addFunction(
            FunSpec
              .builder("name")
              .addModifiers(KModifier.OVERRIDE)
              .returns(STRING)
              .addKdoc("Identifies the contract-selected policy.\n")
              .addStatement("return %S", type.canonicalName)
              .build(),
          ).addFunction(
            FunSpec
              .builder("checkPermission")
              .addModifiers(KModifier.OVERRIDE)
              .addKdoc("Rejects unrelated identities and identities from another configured tenant.\n")
              .addParameter("request", ClassName("io.vertx.ext.web", "RoutingContext"))
              .addParameter("identity", uni.parameterizedBy(identity))
              .addParameter("requestContext", policy.nestedClass("AuthorizationRequestContext"))
              .returns(uni.parameterizedBy(result))
              .addCode("return identity.flatMap { current ->\n")
              .addStatement(
                "  val trusted = !current.isAnonymous && current.getCredential(%T::class.java) != null && current.getAttribute<String>(%S) == %S"
                  .replace(
                    ' ',
                    '·',
                  ),
                ClassName("io.quarkus.oidc", "AccessTokenCredential"),
                "tenant-id",
                binding.tenant ?: "Default",
              ).addStatement("  if (!trusted) return@flatMap %T.createFrom().item(%T.DENY)", uni, result)
              .addStatement("  val scopes = listOf<String>(%L)", scopes.map { CodeBlock.of("%S", it) }.joinToCode(", "))
              .addStatement("  var allowed = %T.createFrom().item(true)", uni)
              .addCode("  for (scope in scopes) {\n")
              .addStatement(
                "    allowed = allowed.flatMap { granted -> if (granted) current.checkPermission(%T(scope)) else %T.createFrom().item(false) }"
                  .replace(
                    ' ',
                    '·',
                  ),
                ClassName("io.quarkus.security", "StringPermission"),
                uni,
              ).addCode("  }\n")
              .addStatement("  allowed.map { if (it) %T.PERMIT else %T.DENY }", result, result)
              .addCode("}\n")
              .build(),
          )
    }
}
