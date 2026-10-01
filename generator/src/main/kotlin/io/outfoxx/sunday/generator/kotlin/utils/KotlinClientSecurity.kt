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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.emit.GeneratedClientSecurity
import io.outfoxx.sunday.generator.ir.emit.credentialTransport

/** Emits the selected complete security alternative without embedding application credentials. */
internal fun GeneratedClientSecurity?.kotlinTransport(profile: String?): CodeBlock {
  if (this == null || bindings.isEmpty()) return CodeBlock.of("this.transport")
  val bindingType = ClassName("io.outfoxx.sunday.security", "SecurityBinding")
  val endpointType = ClassName("io.outfoxx.sunday.security", "SecurityEndpoints")
  val transportType = bindingType.nestedClass("CredentialTransport")
  val values =
    requirement.schemes.map { name ->
      val binding = bindings.getValue(name)
      val transport = schemes.getValue(name).credentialTransport()
      CodeBlock
        .builder()
        .add("%T(⇥\n", bindingType)
        .add("scheme = %S,\nprovider = %S,\n", name, binding.provider)
        .add(
          "flow = %T.%L,\n",
          bindingType.nestedClass("Flow"),
          binding.flow!!.wireName.replaceFirstChar { it.uppercase() },
        ).apply {
          profile?.let { add("profile = %S,\n", it) }
          requirement.permissions[name]?.takeIf { it.isNotEmpty() }?.let { scopes ->
            add("scopes = setOf(%L),\n", scopes.map { CodeBlock.of("%S", it) }.joinToCode())
          }
          val endpoints =
            listOf(
              "discoveryUrl" to binding.discoveryUrl,
              "authorizationUrl" to binding.authorizationUrl,
              "tokenUrl" to binding.tokenUrl,
              "refreshUrl" to binding.refreshUrl,
            ).mapNotNull { (key, value) -> value?.let { CodeBlock.of("%L = %S", key, it) } }
          if (endpoints.isNotEmpty()) add("endpoints = %T(%L),\n", endpointType, endpoints.joinToCode())
          binding.audience?.let { add("audience = %S,\n", it) }
          binding.resource?.let { add("resource = %S,\n", it) }
        }.add(
          "transport = %T(%T.%L, %S",
          transportType,
          transportType.nestedClass("Location"),
          transport.location.replaceFirstChar { it.uppercase() },
          transport.name,
        ).apply { transport.prefix?.let { add(", %S", it) } }
        .add(")⇤\n)")
        .build()
    }
  return CodeBlock.of(
    "this.transport.%M(listOf(⇥\n%L⇤\n))",
    MemberName("io.outfoxx.sunday", "withSecurity"),
    values.joinToCode(",\n"),
  )
}
