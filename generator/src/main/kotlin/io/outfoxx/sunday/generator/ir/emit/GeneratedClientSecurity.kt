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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedEnvironment
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedQuarkusSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedService

/** One chosen client alternative, retaining every conjunct and each scheme's declared scopes. */
data class GeneratedClientSecurity(
  val requirement: GeneratedSecurityRequirement,
  val bindings: Map<String, GeneratedSecurityBinding>,
  val schemes: Map<String, GeneratedSecurityScheme>,
) {
  /** Wire placement shared by client emitters after alternative resolution. */
  data class CredentialTransport(
    val location: String,
    val name: String,
    val prefix: String? = null,
  )
}

/** Resolves scoped acquisition without changing which alternatives the server accepts. */
fun GeneratedApi.clientSecurity(
  service: GeneratedService,
  operation: GeneratedOperation,
  context: GenerationContext,
): GeneratedClientSecurity? {
  require(context.role == GenerationMode.Client) { "Client security requires client generation context" }
  val auth = effectiveAuth(service, operation) ?: return null
  val requirements =
    auth.requirements.ifEmpty {
      if (auth.schemes.isEmpty()) emptyList() else listOf(GeneratedSecurityRequirement(auth.schemes))
    }
  if (requirements.isEmpty()) return null
  val definitions =
    auth.securitySchemes.groupBy { it.name }.mapValues { (name, schemes) ->
      schemes.distinct().singleOrNull() ?: genError("Conflicting security scheme definitions for '$name'")
    }
  val configured = definitions.values.any { it.bindings != null } || auth.selection != null
  if (!configured) return null
  val selection =
    auth.selection
      ?.mapNotNull { selection -> selection.takeIf { it.alternative != null }?.copy(bindings = emptyMap()) }
      ?.resolveSecurityEnvironment(context, "security selection on '${operation.id}'") { first, second ->
        first.merge(second)
      }?.alternative
  val overrides =
    auth.selection
      ?.resolve(context) { first, second -> first.merge(second) }
      ?.bindings
      .orEmpty()
  val undefinedBindings = overrides.keys - definitions.keys
  if (undefinedBindings.isNotEmpty()) {
    genError("Security bindings on '${operation.id}' reference undefined schemes: ${undefinedBindings.sorted()}")
  }
  val alternatives =
    requirements.distinct().filter { requirement ->
      selection == null ||
        selection.schemes.toSet() == requirement.schemes.toSet() &&
        selection.permissions.mapValues { it.value.toSet() } ==
        requirement.permissions.filterValues { it.isNotEmpty() }.mapValues { it.value.toSet() }
    }
  if (alternatives.isEmpty()) genError("Selected security alternative is not declared on '${operation.id}'")
  val failures = mutableListOf<String>()
  val candidates =
    alternatives.mapNotNull { requirement ->
      if (!requirement.schemes.containsAll(requirement.permissions.keys)) {
        genError("Security permissions must refer to schemes in the same requirement: ${operation.id}")
      }
      val schemes =
        requirement.schemes.associateWith { name ->
          (
            definitions[name] ?: genError(
              "Undefined security scheme '$name' on '${operation.id}'",
            )
          ).normalizedSecurityScheme()
        }
      try {
        val bindings =
          schemes.mapValues { (_, scheme) ->
            val local = auth.selection?.mapNotNull { it.bindings[scheme.name] }
            scheme.resolveSecurityBinding(context, local) ?: genError("Missing client provider for '${scheme.name}'")
          }
        checkCredentialTransports(schemes.values, operation.id)
        GeneratedClientSecurity(requirement, bindings, schemes)
      } catch (failure: GenerationException) {
        failures += failure.message.orEmpty()
        null
      }
    }
  if (candidates.isEmpty()) {
    genError(
      "No complete client security alternative is usable for '${operation.id}' in profile '${context.profile}': " +
        failures.joinToString("; "),
    )
  }
  if (candidates.size >
    1
  ) {
    genError(
      "Multiple client security alternatives are usable on '${operation.id}'; select x-sunday-security.client.alternative explicitly",
    )
  }
  return candidates.single()
}

/** Resolves a binding with explicit profile selection and no implicit bearer token acquisition. */
fun GeneratedSecurityScheme.resolveSecurityBinding(
  context: GenerationContext,
  overrides: GeneratedEnvironment<GeneratedSecurityBinding>? = null,
): GeneratedSecurityBinding? {
  val environment = if (overrides == null) bindings else bindings?.inherit(overrides) ?: overrides
  val binding =
    environment?.resolveSecurityEnvironment(context, "security scheme '$name'") { first, second -> first.merge(second) }
      ?: return null
  if (binding.provider.isNullOrBlank()) {
    genError(
      "Security scheme '$name' requires an application provider for ${context.role.name.lowercase()}",
    )
  }
  if (context.role == GenerationMode.Server) {
    if (binding.quarkus != null &&
      binding.quarkus.mode !in
      setOf(
        GeneratedQuarkusSecurityBinding.Mode.PROVIDER,
        GeneratedQuarkusSecurityBinding.Mode.OIDC,
        GeneratedQuarkusSecurityBinding.Mode.WEB_APP,
      )
    ) {
      genError("Server Quarkus binding '$name' requires provider, oidc, or webApp mode")
    }
    if (binding.quarkus?.mode == GeneratedQuarkusSecurityBinding.Mode.PROVIDER && binding.quarkus.tenant != null) {
      genError("Shared providers own tenant selection; tenant is only valid for native OIDC bindings")
    }
    if (binding.copy(provider = null, quarkus = null) != GeneratedSecurityBinding()) {
      genError(
        "Server security binding '$name' accepts a validation provider only; acquisition settings belong in client scope",
      )
    }
    return binding
  }
  val flow = binding.flow ?: GeneratedSecurityBinding.Flow.EXTERNAL
  if (flow == GeneratedSecurityBinding.Flow.EXTERNAL || flow == GeneratedSecurityBinding.Flow.STATIC) {
    if (listOf(
        binding.discoveryUrl,
        binding.authorizationUrl,
        binding.tokenUrl,
        binding.refreshUrl,
      ).any { it != null }
    ) {
      genError("Security scheme '$name' needs an explicit OAuth flow to configure acquisition endpoints")
    }
    return binding.copy(flow = flow)
  }
  val wire =
    oauthFlows[
      if (flow ==
        GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS
      ) {
        "clientCredentials"
      } else {
        "authorizationCode"
      },
    ]
  val resolved =
    binding.copy(
      flow = flow,
      discoveryUrl = binding.discoveryUrl ?: openIdConnectUrl,
      authorizationUrl = binding.authorizationUrl ?: wire?.authorizationUrl,
      tokenUrl = binding.tokenUrl ?: wire?.tokenUrl,
      refreshUrl = binding.refreshUrl ?: wire?.refreshUrl,
    )
  if (normalizedSecurityScheme().let {
      it.type !in setOf("oauth2", "openIdConnect") &&
        !(it.type == "http" && it.scheme.equals("bearer", true))
    }
  ) {
    genError("OAuth acquisition for '$name' requires a bearer credential transport")
  }
  return resolved
}

/** Rejects missing or mistyped security profiles before applying normal environment precedence. */
internal fun <T> GeneratedEnvironment<T>.resolveSecurityEnvironment(
  context: GenerationContext,
  location: String,
  merge: (T, T) -> T,
): T? {
  val profiles = securityProfileNames(context)
  if (profiles.isNotEmpty() && context.profile !in profiles) {
    genError(
      "$location requires an explicit ${context.role.name.lowercase()} profile from ${profiles.sorted().joinToString()}; got '${context.profile}'",
    )
  }
  return resolve(context, merge)
}

/** Profiles with values applicable to this role, including inherited declarations. */
internal fun <T> GeneratedEnvironment<T>.securityProfileNames(context: GenerationContext): Set<String> =
  inherited.flatMap { it.securityProfileNames(context) }.toSet() +
    profiles
      .filterValues {
        it.all != null || (if (context.role == GenerationMode.Client) it.client else it.server) != null
      }.keys

/** Resolves normalized wire placement without inferring an acquisition flow. */
fun GeneratedSecurityScheme.credentialTransport(): GeneratedClientSecurity.CredentialTransport =
  when (type) {
    "http", "oauth2", "openIdConnect" ->
      GeneratedClientSecurity.CredentialTransport("header", "Authorization", if (type == "http") scheme else "Bearer")
    "apiKey", "passThrough" -> {
      val parameter =
        (headers + queryParameters + cookieParameters).singleOrNull()
          ?: genError("Client credential transport '$name' requires exactly one header, query, or cookie parameter")
      GeneratedClientSecurity.CredentialTransport(
        parameter.location.name.lowercase(),
        parameter.serializationName ?: parameter.name,
      )
    }
    else -> genError("Unsupported client credential transport '$type' for '$name'")
  }

internal fun checkCredentialTransports(
  schemes: Collection<GeneratedSecurityScheme>,
  operation: String,
) {
  val transports = mutableSetOf<Pair<String, String>>()
  schemes.forEach { scheme ->
    val transport = scheme.credentialTransport()
    val key =
      transport.location to
        (if (transport.location == "header") transport.name.lowercase() else transport.name)
    if (!transports.add(key)) {
      genError("Conjunctive security schemes on '$operation' conflict at ${key.first} '${key.second}'")
    }
  }
}
