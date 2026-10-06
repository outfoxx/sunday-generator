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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.net.URI

/** One canonical server configuration shared by every applicable generated service factory. */
data class GeneratedClientConfiguration(
  val name: String,
  val discriminator: String,
  val server: GeneratedServer,
  val services: List<String>,
  val documentBaseUri: String?,
  val requiresDocumentBaseUri: Boolean,
) {
  /** Server variables retain the frontend's types, defaults, constraints, and wire names. */
  val variables: List<GeneratedParameter> get() = server.variables
}

/** Plans server configurations once, independently of target language and transport implementation. */
fun GeneratedApi.clientConfigurations(services: List<GeneratedService>): List<GeneratedClientConfiguration> {
  val servers = linkedMapOf<Pair<GeneratedServer, Int>, MutableList<String>>()
  services.forEach { service ->
    val candidates =
      service.servers.ifEmpty {
        service.protocol
          ?.servers
          .orEmpty()
          .ifEmpty { this.servers }
          .ifEmpty {
            protocol?.servers.orEmpty().filter { it.protocol in setOf("http", "https") || it.url.startsWith("http") }
          }.ifEmpty {
            service.baseUri
              ?.let {
                listOf(GeneratedServer(url = it, variables = service.baseUriParameters, sourceUri = source.location))
              }.orEmpty()
          }
      }
    val occurrences = mutableMapOf<GeneratedServer, Int>()
    candidates.forEach candidateLoop@{ candidate ->
      if (candidate.protocol != null && candidate.protocol !in setOf("http", "https")) return@candidateLoop
      val template = candidate.url.replace(Regex("\\{[^}]+}"), "variable")
      val uri = runCatching { URI(template) }.getOrElse { genError("Invalid server URL for '${service.name}'") }
      if (uri.isAbsolute && uri.scheme.lowercase() !in setOf("http", "https")) return@candidateLoop
      val canonical = candidate.copy(sourceUri = candidate.sourceUri.takeUnless { uri.isAbsolute })
      val occurrence = occurrences.getOrDefault(canonical, 0)
      occurrences[canonical] = occurrence + 1
      servers.getOrPut(canonical to occurrence) { mutableListOf() }.add(service.name)
    }
  }
  val names = models.map { it.name.toUpperCamelCase() }.toMutableSet()
  names += services.map { it.name.toUpperCamelCase() }
  return servers.entries.mapIndexed { index, (key, serviceNames) ->
    val server = key.first
    val suffix = if (servers.size == 1) "" else (server.name ?: "Server${index + 1}").configurationIdentifier()
    val typeName = name.configurationIdentifier() + suffix + "Config"
    require(names.add(typeName)) { "Client configuration name '$typeName' collides with another generated type" }
    val variables = server.variables.groupBy { it.serializationName ?: it.name }
    require(variables.values.all { it.size == 1 }) { "Server '$typeName' declares duplicate variables" }
    require(
      server.variables
        .map { it.name.configurationIdentifier() }
        .distinct()
        .size == server.variables.size,
    ) {
      "Server '$typeName' has colliding generated variable names"
    }
    val reserved = setOf("serverid", "documentbaseurl", "baseurl", "postinit")
    require(server.variables.none { it.name.configurationIdentifier().lowercase() in reserved }) {
      "Server '$typeName' has a variable that collides with a generated configuration member"
    }
    val referenced = Regex("\\{([^}]+)}").findAll(server.url).map { it.groupValues[1] }.toSet()
    require(variables.keys.containsAll(referenced)) {
      "Server '$typeName' has undefined template variables: ${referenced - variables.keys}"
    }
    server.variables.forEach { variable ->
      require(
        variable.allowedValues == null ||
          variable.defaultValue == null ||
          variable.defaultValue in variable.allowedValues,
      ) {
        "Server '$typeName' variable '${variable.name}' has an invalid default"
      }
    }
    val relative = !URI(server.url.replace(Regex("\\{[^}]+}"), "variable")).isAbsolute
    val documentBase =
      server.sourceUri
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?.takeIf { it.scheme?.lowercase() in setOf("http", "https") && it.host != null }
        ?.toString()
    GeneratedClientConfiguration(
      name = typeName,
      discriminator = server.name ?: "server${index + 1}",
      server = server,
      services = serviceNames.distinct(),
      documentBaseUri = documentBase,
      requiresDocumentBaseUri = relative && documentBase == null,
    )
  }
}

private fun String.configurationIdentifier(): String =
  replace(Regex("[^A-Za-z0-9_]"), "_").toUpperCamelCase().let { value ->
    if (value.firstOrNull()?.isLetter() == true) value else "Api$value"
  }

/** Aggregates can use one transport only when every constituent service has the same server choices. */
fun List<GeneratedClientConfiguration>.requireCompatibleAggregate(services: List<GeneratedService>) {
  val choices = services.map { service -> filter { service.name in it.services }.map { it.server } }
  require(choices.distinct().size <= 1) { "Aggregate services have incompatible effective server lists" }
}
