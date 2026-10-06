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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedEnvironment
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedSecurityBinding
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService

/** Complete operation alternatives available to a configuration factory for one security profile. */
fun GeneratedApi.clientFactorySecurity(
  service: GeneratedService,
  profile: String?,
  server: GeneratedServer? = null,
): Map<String, List<GeneratedClientSecurity>> {
  val context = GenerationContext(GenerationMode.Client, profile)
  return service.operations.associate { operation ->
    val auth = effectiveAuth(service, operation.copy(auth = operation.serverAuth[server?.name] ?: operation.auth))
    val definitions =
      auth?.securitySchemes.orEmpty().groupBy { it.name }.mapValues { (name, definitions) ->
        definitions.distinct().singleOrNull() ?: genError("Conflicting security scheme definitions for '$name'")
      }
    val requirements =
      auth?.requirements.orEmpty().ifEmpty {
        listOf(GeneratedSecurityRequirement(auth?.schemes.orEmpty()))
      }
    val selection = auth?.selection?.resolve(context) { first, second -> first.merge(second) }
    val alternative = selection?.alternative
    require(
      selection?.bindings.orEmpty().keys.all {
        it in definitions
      },
    ) { "Security selection references an undefined scheme" }
    val alternatives =
      requirements.distinct().filter {
        alternative == null ||
          it.schemes.toSet() == alternative.schemes.toSet() &&
          it.permissions.mapValues { it.value.toSet() } == alternative.permissions.mapValues { it.value.toSet() }
      }
    require(alternatives.isNotEmpty()) { "Selected security alternative is not declared on '${operation.id}'" }
    operation.id to
      alternatives.flatMap { requirement ->
        val schemes =
          requirement.schemes.associateWith { name ->
            definitions[name]?.let { definition ->
              if (definition.type?.lowercase()?.replace(" ", "") == "passthrough") {
                definition.copy(type = "passThrough")
              } else {
                definition.normalizedSecurityScheme()
              }
            }
              ?: genError("Undefined security scheme '$name' on '${operation.id}'")
          }
        checkCredentialTransports(schemes.values, operation.id)
        if (schemes.values.any { scheme ->
            scheme.bindings != null &&
              scheme.bindings.resolve(context) { first, second -> first.merge(second) } == null &&
              selection?.bindings?.get(scheme.name) == null
          }
        ) {
          return@flatMap emptyList()
        }
        var combinations = listOf(emptyMap<String, GeneratedSecurityBinding>())
        schemes.forEach { (name, scheme) ->
          scheme.credentialTransport()
          val configured = scheme.bindings?.resolve(context) { first, second -> first.merge(second) }
          val override = selection?.bindings?.get(name)
          val selected = configured?.let { override?.let(it::merge) ?: it } ?: override
          val flows =
            selected?.flow?.let(::listOf) ?: when (scheme.type) {
              "oauth2" ->
                scheme.oauthFlows.keys
                  .mapNotNull { flow ->
                    when (flow) {
                      "clientCredentials" -> GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS
                      "authorizationCode" -> GeneratedSecurityBinding.Flow.AUTHORIZATION_CODE
                      else -> null
                    }
                  }.ifEmpty { listOf(GeneratedSecurityBinding.Flow.EXTERNAL) }
              else -> listOf(GeneratedSecurityBinding.Flow.EXTERNAL)
            }
          val bindings =
            flows.map { flow ->
              val binding =
                (selected ?: GeneratedSecurityBinding()).copy(
                  provider = selected?.provider ?: name,
                  flow = flow,
                )
              scheme.copy(bindings = GeneratedEnvironment(client = binding)).resolveSecurityBinding(context)!!
            }
          combinations = combinations.flatMap { combination -> bindings.map { combination + (name to it) } }
        }
        combinations.map { GeneratedClientSecurity(requirement, it, schemes) }
      }
  }
}

/** Named client security profiles reachable by a service's factory, including server defaults. */
fun GeneratedApi.clientFactoryProfiles(
  service: GeneratedService,
  generationProfile: String?,
): Set<String?> {
  val context = GenerationContext(GenerationMode.Client)
  val declared =
    buildSet {
      service.operations.forEach { operation ->
        val auth = effectiveAuth(service, operation)
        auth?.selection?.securityProfileNames(context)?.let(::addAll)
        auth?.securitySchemes.orEmpty().forEach { it.bindings?.securityProfileNames(context)?.let(::addAll) }
      }
    }
  val requested = service.servers.mapNotNull { it.securityProfile }
  require(
    requested.all { it in declared },
  ) { "Service '${service.name}' references an unknown client security profile" }
  return buildSet {
    addAll(declared)
    // A policy profile can coexist with unprofiled security; it is not a security profile reference.
    if (generationProfile != null && clientFactorySecurity(service, generationProfile).values.all { it.isNotEmpty() }) {
      add(generationProfile)
    }
    if (clientFactorySecurity(service, null).values.all { it.isNotEmpty() }) add(null)
  }
}

/** Retains direct-constructor defaults while deferring ambiguous factory choices to runtime credentials. */
fun GeneratedApi.clientConstructorSecurity(
  service: GeneratedService,
  operation: GeneratedOperation,
  context: GenerationContext,
  generateClientConfig: Boolean,
): GeneratedClientSecurity? {
  if (!generateClientConfig) return clientSecurity(service, operation, context)
  val auth = effectiveAuth(service, operation) ?: return null
  if (auth.selection == null && auth.securitySchemes.none { it.bindings != null }) return null
  return clientFactorySecurity(service, context.profile)[operation.id]?.singleOrNull()
}

/** Collects the credential and alternative surface for every server accepted by a service factory. */
fun GeneratedApi.clientFactorySecurity(
  service: GeneratedService,
  profile: String?,
  servers: List<GeneratedServer>,
): Map<String, List<GeneratedClientSecurity>> =
  servers
    .flatMap { clientFactorySecurity(service, profile, it).entries }
    .groupBy({ it.key }, { it.value })
    .mapValues { (_, choices) -> choices.flatten().distinct() }
