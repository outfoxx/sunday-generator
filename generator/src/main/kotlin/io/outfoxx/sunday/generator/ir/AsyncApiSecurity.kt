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

import com.fasterxml.jackson.databind.ObjectMapper
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** Normalizes AsyncAPI security into the shared named-scheme policy model. */
internal class AsyncApiSecurity(
  private val source: Map<*, *>,
  private val location: String,
) {

  /** Selects the source security shape without changing the shared IR contract. */
  val isVersion3 = (source["asyncapi"] as? String)?.substringBefore('.') == "3"
  private val components =
    (source["components"] as? Map<*, *>)?.get("securitySchemes") as? Map<*, *> ?: emptyMap<Any, Any>()
  private val inlineDefinitions = mutableMapOf<String, String>()

  /** Resolves ordered alternatives and their named credential definitions. */
  fun parse(
    security: List<Map<*, *>>,
    path: String,
  ): GeneratedAuth? {
    if (security.isEmpty()) return null
    val definitions = linkedMapOf<String, GeneratedSecurityScheme>()
    val requirements =
      security.mapIndexed { index, entry ->
        val entryPath = "$path/$index"
        if (isVersion3) {
          val resolved = resolve(entry, entryPath)
          val ref = entry["\$ref"] as? String
          val tokens = ref?.let { pointer(it, entryPath) }
          val name =
            if (tokens?.size == 3 && tokens.take(2) == listOf("components", "securitySchemes")) {
              tokens.last()
            } else {
              inlineName(resolved, entryPath)
            }
          definitions[name] =
            scheme(
              name,
              if (name.startsWith("inline_") &&
                name !in components
              ) {
                resolved.filterKeys { it != "summary" && it != "description" }
              } else {
                resolved
              },
              entryPath,
            )
          GeneratedSecurityRequirement(
            listOf(name),
            permissions(if (resolved.containsKey("scopes")) resolved["scopes"] else emptyList<String>(), entryPath)
              .takeIf { it.isNotEmpty() }
              ?.let { mapOf(name to it) }
              .orEmpty(),
          )
        } else {
          val required =
            entry.entries.associate { (key, value) ->
              val name = key as? String ?: fail("Security requirement scheme names must be strings", entryPath)
              val definition = components[name] as? Map<*, *> ?: fail("Undefined security scheme '$name'", entryPath)
              definitions[name] = scheme(name, resolve(definition, entryPath), entryPath)
              name to permissions(value, entryPath)
            }
          GeneratedSecurityRequirement(required.keys.toList(), required.filterValues { it.isNotEmpty() })
        }
      }
    return GeneratedAuth(
      schemes = requirements.flatMap { it.schemes }.distinct(),
      requirements = requirements,
      securitySchemes = definitions.values.toList(),
    )
  }

  /** Resolves server alternatives and conjoins them with the operation requirements before emission. */
  fun operationAuth(
    channel: Map<*, *>,
    security: List<Map<*, *>>,
    path: String,
  ): GeneratedAuth? {
    val servers = source["servers"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val references =
      if (channel.containsKey("servers")) {
        channel["servers"] as? List<*> ?: fail("Channel servers must be a reference list", path)
      } else {
        emptyList<Any>()
      }
    val applicable =
      if (references.isEmpty()) {
        servers.values.map { resolve(it as? Map<*, *> ?: fail("Invalid server", path), path) }
      } else {
        references.mapIndexed { index, value ->
          resolve(value as? Map<*, *> ?: fail("Channel servers must be references", path), "$path/servers/$index")
        }
      }
    val serverPolicies =
      applicable.mapIndexed { index, server ->
        parse(declarations(server, "$path/servers/$index/security"), "$path/servers/$index/security")
      }
    val serverAuth =
      if (serverPolicies.all { it == null }) {
        null
      } else {
        val alternatives =
          serverPolicies.flatMap {
            it?.requirements
              ?: listOf(GeneratedSecurityRequirement(emptyList()))
          }
        auth(alternatives, serverPolicies.filterNotNull().flatMap { it.securitySchemes }, path)
      }
    val operationAuth = parse(security, "$path/security")
    if (serverAuth == null) return operationAuth
    if (operationAuth == null) return serverAuth
    val requirements =
      serverAuth.requirements.flatMap { server ->
        operationAuth.requirements.map { operation ->
          val schemes = (server.schemes + operation.schemes).distinct()
          GeneratedSecurityRequirement(
            schemes,
            schemes
              .associateWith { (server.permissions[it].orEmpty() + operation.permissions[it].orEmpty()).distinct() }
              .filterValues { it.isNotEmpty() },
          )
        }
      }
    return auth(requirements, serverAuth.securitySchemes + operationAuth.securitySchemes, path)
  }

  private fun auth(
    requirements: List<GeneratedSecurityRequirement>,
    schemes: List<GeneratedSecurityScheme>,
    path: String,
  ): GeneratedAuth {
    val definitions =
      schemes.groupBy { it.name }.map { (name, values) ->
        values.distinct().singleOrNull() ?: fail("Conflicting security scheme definitions for '$name'", path)
      }
    return GeneratedAuth(requirements.flatMap { it.schemes }.distinct(), requirements, definitions)
  }

  /** Rejects malformed declarations instead of allowing them to become unprotected endpoints. */
  fun declarations(
    owner: Map<*, *>,
    path: String,
  ): List<Map<*, *>> {
    if (!owner.containsKey("security")) return emptyList()
    val values = owner["security"] as? List<*> ?: fail("Security must be a list", path)
    return values.mapIndexed { index, value ->
      value as? Map<*, *> ?: fail("Security entries must be objects", "$path/$index")
    }
  }

  private fun permissions(
    value: Any?,
    path: String,
  ): List<String> =
    (value as? List<*> ?: fail("Security requirement must contain a permission list", path)).map {
      it as? String ?: fail("Security permissions must be strings", path)
    }

  private fun resolve(
    value: Map<*, *>,
    path: String,
    visited: Set<String> = emptySet(),
  ): Map<*, *> {
    if (!value.containsKey("\$ref")) return value
    val reference = value["\$ref"] as? String ?: fail("Security reference must be a string", path)
    val tokens = pointer(reference, path)
    val canonical = tokens.joinToString("/") { it.replace("~", "~0").replace("/", "~1") }
    if (canonical in visited) fail("Cyclic security reference '$reference'", path)
    val target =
      tokens.fold(source as Any?) { current, token ->
        when (current) {
          is Map<*, *> -> current[token]
          is List<*> -> token.toIntOrNull()?.let { current.getOrNull(it) }
          else -> null
        }
      } ?: fail("Missing security reference target '$reference'", path)
    return resolve(
      target as? Map<*, *> ?: fail("Invalid security reference target '$reference'", path),
      path,
      visited + canonical,
    )
  }

  private fun pointer(
    reference: String,
    path: String,
  ): List<String> {
    if (!reference.startsWith("#")) fail("External security references are unsupported: '$reference'", path)
    val fragment =
      try {
        URI(reference).fragment
      } catch (_: URISyntaxException) {
        fail("Invalid local security reference '$reference'", path)
      }
    if (fragment == null || !fragment.startsWith('/')) fail("Invalid local security reference '$reference'", path)
    return fragment.drop(1).split('/').map { token ->
      if (Regex("~(?![01])").containsMatchIn(token)) fail("Invalid JSON Pointer escape in '$reference'", path)
      token.replace("~1", "/").replace("~0", "~")
    }
  }

  private fun inlineName(
    value: Map<*, *>,
    path: String,
  ): String {
    val definition =
      value.filterKeys {
        it in
          setOf("type", "scheme", "bearerFormat", "name", "in", "openIdConnectUrl", "flows")
      }
    val canonical = ObjectMapper().writeValueAsString(canonical(definition))
    val digest =
      MessageDigest
        .getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val name = "inline_$digest"
    if (components.containsKey(name) ||
      inlineDefinitions.putIfAbsent(name, canonical)?.let { it != canonical } == true
    ) {
      fail("Inline security scheme name collision '$name'", path)
    }
    return name
  }

  private fun canonical(value: Any?): Any? =
    when (value) {
      is Map<*, *> -> value.entries.associate { it.key.toString() to canonical(it.value) }.toSortedMap()
      is List<*> -> value.map(::canonical)
      else -> value
    }

  private fun scheme(
    name: String,
    value: Map<*, *>,
    path: String,
  ): GeneratedSecurityScheme {
    val type = value["type"] as? String ?: fail("Security scheme '$name' must declare its type", path)
    val parameter =
      if (type == "httpApiKey") {
        val wireName =
          (value["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: fail("API key security scheme '$name' must declare its parameter name", path)
        GeneratedParameter(
          name = wireName.toLowerCamelCase(),
          location =
            when (value["in"] as? String) {
              "header" -> GeneratedParameter.Location.HEADER
              "query" -> GeneratedParameter.Location.QUERY
              "cookie" -> GeneratedParameter.Location.COOKIE
              else -> fail("API key security scheme '$name' must use header, query, or cookie location", path)
            },
          type = GeneratedTypeRef.scalar("string"),
          required = true,
          serializationName = wireName.takeUnless { it == it.toLowerCamelCase() },
        )
      } else {
        null
      }
    return GeneratedSecurityScheme(
      name = name,
      type = type,
      scheme = value["scheme"] as? String,
      bearerFormat = value["bearerFormat"] as? String,
      headers = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.HEADER }),
      queryParameters = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.QUERY }),
      cookieParameters = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.COOKIE }),
      openIdConnectUrl = value["openIdConnectUrl"] as? String,
      oauthFlows =
        (value["flows"] as? Map<*, *>).orEmpty().entries.associate { (flowName, raw) ->
          val flow = (raw as? Map<*, *>).orEmpty()
          flowName.toString() to
            GeneratedOAuthFlow(
              authorizationUrl = flow["authorizationUrl"] as? String,
              tokenUrl = flow["tokenUrl"] as? String,
              refreshUrl = flow["refreshUrl"] as? String,
              scopes =
                (flow[if (isVersion3) "availableScopes" else "scopes"] as? Map<*, *>).orEmpty().entries.associate {
                  it.key.toString() to it.value.toString()
                },
            )
        },
      documentation =
        GeneratedDocumentation(
          summary = value["summary"] as? String,
          description = value["description"] as? String,
        ).takeUnless { it == GeneratedDocumentation() },
    )
  }

  private fun fail(
    message: String,
    path: String,
  ): Nothing = throw GenerationException("$message at $path", location, 0, 0)
}
