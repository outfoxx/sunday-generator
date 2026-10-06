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
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedEnvironment
import io.outfoxx.sunday.generator.ir.GeneratedProtocol

/**
 * Projects deployment metadata for an artifact without changing wire requirements, schemes, scopes, or issuer metadata.
 * A selected profile remains explicit so downstream generators cannot silently select another environment.
 */
fun GeneratedApi.projectEnvironment(context: GenerationContext): GeneratedApi {
  services.forEach { service ->
    service.operations.forEach { operation ->
      when (context.role) {
        GenerationMode.Client -> Unit
        GenerationMode.Server -> endpointSecurityPolicy(service, operation, context)
      }
    }
  }

  fun auth(value: GeneratedAuth?): GeneratedAuth? =
    value?.copy(
      securitySchemes =
        value.securitySchemes.map { scheme ->
          scheme.copy(
            bindings =
              if (context.role == GenerationMode.Client) {
                scheme.bindings?.retainClientProfiles()
              } else {
                scheme.bindings
                  ?.takeIf { bindings ->
                    bindings.securityProfileNames(context).let { it.isEmpty() || context.profile in it }
                  }?.project(context) { first, second -> first.merge(second) }
              },
          )
        },
      selection =
        if (context.role ==
          GenerationMode.Client
        ) {
          value.selection?.retainClientProfiles()
        } else {
          value.selection?.project(context) { first, second -> first.merge(second) }
        },
    )

  fun protocol(value: GeneratedProtocol?): GeneratedProtocol? =
    value?.copy(
      servers = value.servers.map { it.copy(auth = auth(it.auth)) },
    )
  return copy(
    auth = auth(auth),
    servers = servers.map { it.copy(auth = auth(it.auth)) },
    protocol = protocol(protocol),
    services =
      services.map { service ->
        service.copy(
          auth = auth(service.auth),
          servers = service.servers.map { it.copy(auth = auth(it.auth)) },
          protocol = protocol(service.protocol),
          operations =
            service.operations.map { operation ->
              operation.copy(
                auth = auth(operation.auth),
                serverAuth = operation.serverAuth.mapValues { (_, value) -> auth(value)!! },
                protocol = protocol(operation.protocol),
                policy = operation.policy?.project(context) { first, second -> first.merge(second) },
              )
            },
        )
      },
    tags =
      tags.map { tag ->
        tag.copy(policy = tag.policy?.project(context) { first, second -> first.merge(second) })
      },
  )
}

private fun <T> GeneratedEnvironment<T>.project(
  context: GenerationContext,
  merge: (T, T) -> T,
): GeneratedEnvironment<T>? {
  val value = resolve(context, merge) ?: return null
  val scope: GeneratedEnvironment.Scope<T> =
    if (context.role ==
      GenerationMode.Client
    ) {
      GeneratedEnvironment.Scope(client = value)
    } else {
      GeneratedEnvironment.Scope(server = value)
    }
  return if (context.profile != null) {
    GeneratedEnvironment(profiles = mapOf(context.profile to scope))
  } else {
    GeneratedEnvironment(client = scope.client, server = scope.server)
  }
}

private fun <T> GeneratedEnvironment<T>.retainClientProfiles(): GeneratedEnvironment<T> =
  copy(
    server = null,
    profiles =
      profiles.mapValues { (_, scope) -> scope.copy(server = null) }.filterValues {
        it.all != null ||
          it.client != null
      },
    inherited = inherited.map { it.retainClientProfiles() },
  )
