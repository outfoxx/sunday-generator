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

import io.outfoxx.sunday.generator.genError
import java.net.URI

/** Strict readers for scoped scheme bindings and endpoint alternative selection. */
internal object GeneratedSecurityReader {
  fun binding(
    value: Any?,
    location: String,
  ): GeneratedEnvironment<GeneratedSecurityBinding> = GeneratedEnvironmentReader.read(value, location, ::bindingValue)

  private fun bindingValue(
    raw: Any?,
    path: String,
  ): GeneratedSecurityBinding {
    val fields =
      GeneratedEnvironmentReader.objectValue(
        raw,
        path,
        setOf(
          "provider",
          "flow",
          "discoveryUrl",
          "authorizationUrl",
          "tokenUrl",
          "refreshUrl",
          "audience",
          "resource",
        ),
      )
    return GeneratedSecurityBinding(
      provider = fields.text("provider", path),
      flow =
        fields.text("flow", path)?.let { flow ->
          when (flow) {
            "clientCredentials" -> GeneratedSecurityBinding.Flow.CLIENT_CREDENTIALS
            "authorizationCode" -> GeneratedSecurityBinding.Flow.AUTHORIZATION_CODE
            "external" -> GeneratedSecurityBinding.Flow.EXTERNAL
            "static" -> GeneratedSecurityBinding.Flow.STATIC
            else -> genError("Unsupported $path.flow '$flow'; use a supported flow or an external provider")
          }
        },
      discoveryUrl = fields.endpoint("discoveryUrl", path),
      authorizationUrl = fields.endpoint("authorizationUrl", path),
      tokenUrl = fields.endpoint("tokenUrl", path),
      refreshUrl = fields.endpoint("refreshUrl", path),
      audience = fields.text("audience", path),
      resource = fields.text("resource", path),
    )
  }

  fun selection(
    value: Any?,
    location: String,
  ): GeneratedEnvironment<GeneratedSecuritySelection> =
    GeneratedEnvironmentReader.read(value, location) { raw, path ->
      val fields = GeneratedEnvironmentReader.objectValue(raw, path, setOf("alternative", "bindings"))
      val alternative =
        if (fields.containsKey("alternative")) {
          val members = GeneratedEnvironmentReader.objectValue(fields["alternative"], "$path.alternative")
          val permissions =
            members.mapValues { (scheme, rawScopes) ->
              if (scheme.isBlank()) genError("$path.alternative requires non-blank scheme names")
              val scopes = rawScopes as? List<*> ?: genError("$path.alternative.$scheme must be a scope list")
              scopes
                .map { scope ->
                  (scope as? String)?.takeIf { it.isNotBlank() }
                    ?: genError("$path.alternative.$scheme requires non-blank scopes")
                }.distinct()
            }
          GeneratedSecurityRequirement(members.keys.toList(), permissions.filterValues { it.isNotEmpty() })
        } else {
          null
        }
      val bindings =
        if (fields.containsKey("bindings")) {
          GeneratedEnvironmentReader.objectValue(fields["bindings"], "$path.bindings").mapValues { (name, binding) ->
            if (name.isBlank()) genError("$path.bindings requires non-blank scheme names")
            bindingValue(binding, "$path.bindings.$name")
          }
        } else {
          emptyMap()
        }
      GeneratedSecuritySelection(alternative, bindings)
    }

  private fun Map<String, Any?>.text(
    name: String,
    path: String,
  ): String? =
    if (containsKey(name)) {
      (this[name] as? String)?.takeIf { it.isNotBlank() } ?: genError("$path.$name must be a non-blank string")
    } else {
      null
    }

  private fun Map<String, Any?>.endpoint(
    name: String,
    path: String,
  ): String? =
    text(name, path)?.also { value ->
      val uri = runCatching { URI(value) }.getOrNull()
      if (uri == null ||
        uri.scheme?.lowercase() !in setOf("http", "https") ||
        uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null ||
        uri.rawFragment != null
      ) {
        genError("$path.$name must be an absolute HTTP(S) URL without embedded credentials or a fragment")
      }
    }
}
