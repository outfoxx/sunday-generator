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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedService

/** Resolved security alternatives, retaining each scheme's required permissions and credential metadata. */
data class GeneratedEndpointPolicy(
  val requirements: List<GeneratedSecurityRequirement>,
  val schemes: Map<String, GeneratedSecurityScheme>,
)

/** Resolves a complete endpoint policy and rejects security metadata that cannot be enforced. */
fun GeneratedApi.endpointSecurityPolicy(
  service: GeneratedService,
  operation: GeneratedOperation,
): GeneratedEndpointPolicy? {
  val access = endpointAuthentication(service, operation)
  if (access == GeneratedEndpointAccess.UNSPECIFIED) {
    return null
  }
  if (access == GeneratedEndpointAccess.PUBLIC) {
    return GeneratedEndpointPolicy(emptyList(), emptyMap())
  }
  val auth = effectiveAuth(service, operation)!!
  val requirements =
    auth.requirements.ifEmpty { listOf(GeneratedSecurityRequirement(auth.schemes)) }
  requirements.forEach { requirement ->
    if (!requirement.schemes.containsAll(requirement.permissions.keys)) {
      genError("Security permissions must refer to schemes in the same requirement: " + operation.id)
    }
  }
  val schemes =
    requirements.flatMap { it.schemes }.distinct().associateWith { name ->
      val definitions = auth.securitySchemes.filter { it.name == name }.distinct()
      if (definitions.isEmpty()) genError("Undefined security scheme '$name' on " + operation.id)
      val scheme = definitions.singleOrNull() ?: genError("Conflicting security scheme definitions for '$name'")
      val normalized =
        when (scheme.type?.lowercase()?.replace(" ", "")) {
          "apikey", "httpapikey" -> scheme.copy(type = "apiKey")
          "http" -> scheme.copy(type = "http")
          "basicauthentication" -> scheme.copy(type = "http", scheme = "basic")
          "digestauthentication" -> scheme.copy(type = "http", scheme = "digest")
          "oauth2", "oauth2.0" -> scheme.copy(type = "oauth2")
          "openidconnect" -> scheme.copy(type = "openIdConnect")
          "mutualtls", "x509" -> scheme.copy(type = "mutualTLS")
          else -> genError("Unsupported security scheme type '" + scheme.type + "' for '$name'")
        }
      if (normalized.type == "http" && normalized.scheme.isNullOrBlank()) {
        genError("HTTP security scheme '$name' must declare its authentication scheme")
      }
      if (
        normalized.type == "apiKey" &&
        normalized.headers.size + normalized.queryParameters.size + normalized.cookieParameters.size != 1
      ) {
        genError("API key security scheme '$name' must declare exactly one header, query, or cookie parameter")
      }
      normalized
    }
  return GeneratedEndpointPolicy(requirements, schemes)
}

/** Combines referenced scheme definitions without silently conflating different contracts under one name. */
fun Iterable<GeneratedEndpointPolicy>.endpointSecuritySchemes(): Map<String, GeneratedSecurityScheme> =
  flatMap { it.schemes.entries }.groupBy({ it.key }, { it.value }).mapValues { (name, definitions) ->
    definitions.distinct().singleOrNull() ?: genError("Conflicting security scheme definitions for '$name'")
  }
