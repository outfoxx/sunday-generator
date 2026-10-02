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

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.sunday.generator.ir.emit.GeneratedClientSecurity
import io.outfoxx.sunday.generator.ir.emit.credentialTransport
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.joinToCode

/** Emits complete selected alternatives; credentials remain application/runtime bindings. */
internal fun GeneratedClientSecurity.swiftBindings(profile: String?): CodeBlock {
  val type = DeclaredTypeName.typeName("Sunday.SecurityBinding")
  val values =
    requirement.schemes.map { name ->
      val binding = bindings.getValue(name)
      val transport = schemes.getValue(name).credentialTransport()
      CodeBlock
        .builder()
        .add("%T(%>\n", type)
        .add("scheme: %S,\nprovider: %S,\nflow: .%L", name, binding.provider, binding.flow!!.wireName)
        .apply {
          profile?.let { add(",\nprofile: %S", it) }
          requirement.permissions[name]?.takeIf { it.isNotEmpty() }?.let { scopes ->
            add(",\nscopes: [%L]", scopes.map { CodeBlock.of("%S", it) }.joinToCode(", "))
          }
          val endpoints =
            listOf(
              "discoveryURL" to binding.discoveryUrl,
              "authorizationURL" to binding.authorizationUrl,
              "tokenURL" to binding.tokenUrl,
              "refreshURL" to binding.refreshUrl,
            ).mapNotNull { (key, value) -> value?.let { CodeBlock.of("%L: %S", key, it) } }
          if (endpoints.isNotEmpty()) add(",\nendpoints: .init(%L)", endpoints.joinToCode(", "))
          binding.audience?.let { add(",\naudience: %S", it) }
          binding.resource?.let { add(",\nresource: %S", it) }
        }.add(",\ntransport: .init(location: .%L, name: %S", transport.location, transport.name)
        .apply { transport.prefix?.let { add(", prefix: %S", it) } }
        .add(")%<\n)")
        .build()
    }
  return CodeBlock.of("[%>\n%L%<\n]", values.joinToCode(",\n"))
}
