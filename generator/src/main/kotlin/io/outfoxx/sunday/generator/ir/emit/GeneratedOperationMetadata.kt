/*
 * Copyright 2020 Outfox, Inc.
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

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedJaxrs
import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedService

/**
 * True when the auth metadata carries no security, scheme, or Zanzibar data.
 */
val GeneratedAuth.isEmpty: Boolean
  get() =
    schemes.isEmpty() &&
      requirements.isEmpty() &&
      securitySchemes.isEmpty() &&
      zanzibar.isEmpty() &&
      zanzibarUserSource == null

/**
 * Returns auth metadata using operation, service, then API precedence.
 */
fun GeneratedApi.effectiveAuth(
  service: GeneratedService,
  operation: GeneratedOperation,
): GeneratedAuth? =
  listOf(operation.auth, service.auth, auth)
    .firstOrNull { current -> current != null && !current.isEmpty }

/**
 * Flattened security requirement scheme names in declaration order.
 */
val GeneratedAuth.requirementSchemeNames: List<String>
  get() = requirements.flatMap { requirement -> requirement.schemes }.distinct()

/**
 * Returns the named security scheme metadata.
 */
fun GeneratedAuth.schemeOrNull(name: String): GeneratedSecurityScheme? =
  securitySchemes.firstOrNull { scheme -> scheme.name == name }

/**
 * Returns parameters declared by a security scheme for a wire location.
 */
fun GeneratedSecurityScheme.parameters(location: GeneratedParameter.Location): List<GeneratedParameter> =
  when (location) {
    GeneratedParameter.Location.HEADER -> headers
    GeneratedParameter.Location.QUERY -> queryParameters
    GeneratedParameter.Location.COOKIE -> cookieParameters
    else -> listOf()
  }

/**
 * Returns all security scheme parameters for a wire location.
 */
fun GeneratedAuth.securityParameters(location: GeneratedParameter.Location): List<GeneratedParameter> =
  securitySchemes.flatMap { scheme -> scheme.parameters(location) }

/**
 * True when policy metadata carries no configured policy values.
 */
val GeneratedPolicy.isEmpty: Boolean
  get() =
    timeout == null &&
      retry.isEmpty() &&
      circuitBreaker.isEmpty() &&
      clientRateLimit.isEmpty() &&
      serverRateLimit.isEmpty() &&
      source == null

/**
 * Overlays policy metadata, preserving base values unless overrides provide a replacement.
 */
fun GeneratedPolicy.withOverrides(overrides: GeneratedPolicy?): GeneratedPolicy {
  if (overrides == null || overrides.isEmpty) {
    return this
  }

  return GeneratedPolicy(
    timeout = overrides.timeout ?: timeout,
    retry = retry + overrides.retry,
    circuitBreaker = circuitBreaker + overrides.circuitBreaker,
    clientRateLimit = clientRateLimit + overrides.clientRateLimit,
    serverRateLimit = serverRateLimit + overrides.serverRateLimit,
    source = overrides.source ?: source,
  )
}

/**
 * Reads a mode-aware generated flag.
 */
fun GeneratedModeFlag.enabledFor(mode: GenerationMode): Boolean =
  when (mode) {
    GenerationMode.Client -> client ?: all ?: false
    GenerationMode.Server -> server ?: all ?: false
  }

/**
 * True when JAX-RS async behavior is requested.
 */
val GeneratedJaxrs.isAsynchronous: Boolean
  get() = asynchronous == true

/**
 * True when JAX-RS reactive behavior is requested.
 */
val GeneratedJaxrs.isReactive: Boolean
  get() = reactive == true

/**
 * Source-declared JAX-RS context parameter names.
 */
val GeneratedJaxrs.contextParameters: List<String>
  get() = context

/**
 * True when SSE behavior is requested for the generation mode.
 */
fun GeneratedJaxrs.sseEnabled(mode: GenerationMode): Boolean = sse?.enabledFor(mode) == true

/**
 * True when JSON body behavior is requested for the generation mode.
 */
fun GeneratedJaxrs.jsonBodyEnabled(mode: GenerationMode): Boolean = jsonBody?.enabledFor(mode) == true
