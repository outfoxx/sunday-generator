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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedClientConfiguration
import io.outfoxx.sunday.generator.ir.emit.GeneratedClientSecurity
import io.outfoxx.sunday.generator.ir.emit.clientFactoryAlternatives
import io.outfoxx.sunday.generator.ir.emit.clientFactoryProfiles
import io.outfoxx.sunday.generator.ir.emit.clientFactorySecurity
import io.outfoxx.sunday.generator.ir.emit.credentialTransport
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection

/** Renders server configuration values and application-supplied Python transport factories. */
internal class PythonClientConfigurations(
  private val api: GeneratedApi,
  private val packageName: String,
  private val generationProfile: String?,
  private val defaultMediaTypes: List<String>,
) {
  /** Emits shared configuration types once per API package. */
  fun configurations(plans: List<GeneratedClientConfiguration>): PythonModule {
    val module = PythonModuleBuilder("$packageName/config.py")
    module.addCode(
      PythonCodeBlock.of(
        "%L",
        "from dataclasses import dataclass, field\nfrom typing import Literal\nfrom sunday import ClientSettings",
      ),
    )
    if (plans.any { it.variables.isNotEmpty() }) {
      module.addCode(PythonCodeBlock.of("from pydantic import TypeAdapter"))
    }
    if (plans.any { plan -> plan.variables.any { it.type.kind == GeneratedTypeRef.Kind.NAMED } }) {
      module.addCode(PythonCodeBlock.of("from . import models as _models"))
    }
    plans.forEach { plan ->
      module.addExport(plan.name)
      val code =
        buildString {
          appendLine("@dataclass(frozen=True, kw_only=True)")
          appendLine("class ${plan.name}:")
          appendLine("    \"\"\"Server variables for ${plan.name}.\"\"\"")
          appendLine()
          appendLine(
            "    server_id: Literal[${plan.discriminator.pythonStringLiteral()}] = field(default=${plan.discriminator.pythonStringLiteral()}, init=False)",
          )
          if (plan.requiresDocumentBaseUri) appendLine("    document_base_url: str")
          plan.variables.forEach { variable ->
            variable.documentation
              ?.description
              ?.lineSequence()
              ?.forEach { appendLine("    #: $it") }
            append("    ${variable.name.pythonIdentifierName}: ${type(variable.type)}")
            variable.defaultValue?.let { append(" = ${literal(it)}") }
            appendLine()
          }
          appendLine()
          appendLine("    def __post_init__(self) -> None:")
          appendLine("        \"\"\"Validate server variables before resolving an endpoint.\"\"\"")
          if (plan.variables.isEmpty()) appendLine("        pass")
          plan.variables.forEach { variable ->
            val name = variable.name.pythonIdentifierName
            appendLine(
              "        object.__setattr__(self, ${name.pythonStringLiteral()}, TypeAdapter(${type(
                variable.type,
              )}).validate_python(self.$name))",
            )
            variable.allowedValues?.let { values ->
              appendLine("        if self.$name not in ${literal(values)}:")
              appendLine(
                "            raise ValueError(${("Invalid server variable '${variable.name}'").pythonStringLiteral()})",
              )
            }
          }
          appendLine()
          appendLine("    def base_url(self) -> str:")
          appendLine("        \"\"\"Resolve this server's endpoint without constructing a transport.\"\"\"")
          val variables =
            plan.variables.joinToString(", ") {
              "${(it.serializationName ?: it.name).pythonStringLiteral()}: " +
                "str(self.${it.name.pythonIdentifierName})" + if (it.type.name == "boolean") ".lower()" else ""
            }
          val document = if (plan.requiresDocumentBaseUri) "self.document_base_url" else literal(plan.documentBaseUri)
          appendLine(
            "        return ClientSettings.server_url(${plan.server.url.pythonStringLiteral()}, {$variables}, $document)",
          )
        }
      module.addCode(PythonCodeBlock.of("%L", code))
    }
    return module.build()
  }

  /** Adds a generic factory to the existing service module without selecting a concrete transport. */
  fun factories(
    module: PythonModule,
    service: GeneratedService,
    plans: List<GeneratedClientConfiguration>,
  ): PythonModule {
    val applicable = plans.filter { service.name in it.services }
    if (applicable.isEmpty()) return module
    val media = service.defaultMediaSelection(defaultMediaTypes)
    val profiles = api.clientFactoryProfiles(service, generationProfile)
    val security =
      profiles.associateWith {
        api.clientFactorySecurity(
          service,
          it,
          applicable.map { plan ->
            plan.server
          },
        )
      }
    val schemes =
      security.values
        .flatMap { it.values.flatten() }
        .flatMap { it.schemes.values }
        .distinctBy { it.name }
    val credentialType = "${service.pythonServiceBaseName.pythonTypeName}Credentials"
    val alternativeType = "${service.pythonServiceBaseName.pythonTypeName}SecurityAlternative"
    val clientType = "${service.pythonServiceBaseName.pythonTypeName}Client"
    val factoryName = "create_${service.pythonServiceBaseName.pythonIdentifierName}"
    val credentialTypes = schemes.map { credentialType(it.type, it.scheme) }.toSet()
    val imports =
      buildString {
        appendLine("from dataclasses import dataclass as _dataclass")
        appendLine("from enum import Enum as _Enum")
        appendLine("from collections.abc import Callable as _Callable, Mapping as _Mapping, Sequence as _Sequence")
        appendLine("from sunday import (")
        appendLine("    ClientSettings as _ClientSettings,")
        appendLine("    UNSET as _UNSET,")
        appendLine("    UnsetType as _UnsetType,")
        appendLine("    MediaType as _MediaType,")
        appendLine("    Credentials as _Credentials,")
        appendLine("    SecurityBinding as _SecurityBinding,")
        if (schemes.isNotEmpty()) appendLine("    SecurityTransport as _SecurityTransport,")
        appendLine(")")
        if (schemes.isNotEmpty()) {
          appendLine(
            "from sunday import ${(credentialTypes + "ProviderCredentials").sorted().joinToString(", ")}",
          )
        }
        appendLine("from .config import ${applicable.joinToString(", ") { it.name }}")
      }
    val code =
      buildString {
        appendLine()
        appendLine()
        appendLine("class $alternativeType(_Enum):")
        appendLine("    \"\"\"Complete security alternatives, retaining each scheme's required scopes.\"\"\"")
        appendLine()
        security.values.clientFactoryAlternatives().forEach { choice ->
          val entries =
            choice.requirement.permissions.map { (scheme, scopes) ->
              val values = scopes.joinToString(", ") { it.pythonStringLiteral() }
              "(${scheme.pythonStringLiteral()}, (${values}${if (scopes.size == 1) "," else ""}))"
            }
          val value = entries.joinToString(", ")
          appendLine("    ${choice.name.pythonEnumMemberName} = ($value${if (entries.size == 1) "," else ""})")
        }
        appendLine()
        appendLine("    def matches(self, bindings: _Sequence[_SecurityBinding]) -> bool:")
        appendLine("        \"\"\"Match a whole alternative without dropping schemes or scopes.\"\"\"")
        appendLine(
          "        requirement = tuple(sorted((binding.scheme, tuple(sorted(binding.scopes))) for binding in bindings))",
        )
        appendLine("        return bool(requirement == self.value)")
        appendLine()
        appendLine()
        appendLine("@_dataclass(frozen=True, kw_only=True)")
        appendLine("class $credentialType:")
        appendLine("    \"\"\"Scheme-specific credentials; one complete alternative is required per operation.\"\"\"")
        appendLine()
        if (schemes.isEmpty()) appendLine("    pass")
        schemes.forEach { scheme ->
          val type = credentialType(scheme.type, scheme.scheme)
          val types = listOf(type, "ProviderCredentials", "None").distinct().joinToString(" | ")
          appendLine("    ${scheme.name.pythonIdentifierName}: $types = None")
        }
        appendLine()
        appendLine()
        appendLine("def $factoryName[TransportRequestT, TransportResponseT](")
        appendLine("    config: ${applicable.joinToString(" | ") { it.name }},")
        appendLine(
          "    transport_factory: _Callable[[_ClientSettings], Transport[TransportRequestT, TransportResponseT]],",
        )
        appendLine("    *,")
        appendLine("    credentials: $credentialType | None = None,")
        appendLine("    default_content_types: _Sequence[_MediaType] | None = None,")
        appendLine("    default_accept_types: _Sequence[_MediaType] | None = None,")
        appendLine("    security_profile: str | None | _UnsetType = _UNSET,")
        appendLine("    security_selection: _Mapping[str, $alternativeType] | None = None,")
        appendLine(") -> $clientType[TransportRequestT, TransportResponseT]:")
        appendLine(
          "    \"\"\"Construct a service with the application's chosen transport and compatible credentials.\"\"\"",
        )
        appendLine("    credentials = credentials or $credentialType()")
        appendLine("    supplied: dict[str, _Credentials] = {}")
        schemes.forEach { scheme ->
          val name = scheme.name.pythonIdentifierName
          appendLine("    if credentials.$name is not None:")
          appendLine("        supplied[${scheme.name.pythonStringLiteral()}] = credentials.$name")
        }
        appendLine("    default_profiles: dict[str, str | None] = {")
        applicable.forEach { plan ->
          val server = plan.discriminator.pythonStringLiteral()
          val profile = literal(plan.server.securityProfile ?: generationProfile)
          appendLine("        $server: $profile,")
        }
        appendLine("    }")
        appendLine(
          "    profile = default_profiles[config.server_id] if isinstance(security_profile, _UnsetType) else security_profile",
        )
        appendLine("    alternatives: dict[tuple[str, str | None], dict[str, list[list[_SecurityBinding]]]] = {")
        applicable.forEach { plan ->
          val selectedSecurity = profiles.associateWith { api.clientFactorySecurity(service, it, plan.server) }
          selectedSecurity.forEach { (profile, operations) ->
            appendLine("        (${plan.discriminator.pythonStringLiteral()}, ${literal(profile)}): {")
            operations.forEach { (id, alternatives) ->
              if (alternatives.all { it.bindings.isEmpty() }) {
                appendLine("            ${id.pythonStringLiteral()}: [[]],")
              } else {
                appendLine("            ${id.pythonStringLiteral()}: [")
                alternatives.forEach { appendLine("                ${bindings(it, profile)},") }
                appendLine("            ],")
              }
            }
            appendLine("        },")
          }
        }
        appendLine("    }")
        appendLine("    key = (config.server_id, profile)")
        appendLine("    if key not in alternatives:")
        appendLine("        raise ValueError(\"Unknown client security profile\")")
        appendLine(
          "    if security_selection and any(operation not in alternatives[key] for operation in security_selection):",
        )
        appendLine("        raise ValueError(\"Unknown operation in security selection\")")
        appendLine("    selected_alternatives: dict[str, list[list[_SecurityBinding]]] = {}")
        appendLine("    for operation, choices in alternatives[key].items():")
        appendLine("        selection = (security_selection or {}).get(operation)")
        appendLine("        if selection is not None:")
        appendLine("            choices = [choice for choice in choices if selection.matches(choice)]")
        appendLine("        selected_alternatives[operation] = choices")
        appendLine("    settings = _ClientSettings.resolve(config.base_url(), selected_alternatives, supplied)")
        appendLine("    transport = transport_factory(settings)")
        appendLine("    return $clientType(")
        appendLine("        transport,")
        appendLine("        client_settings=settings,")
        appendLine(
          "        default_content_types=${mediaTypes(
            media.contentTypes,
          )} if default_content_types is None else default_content_types,",
        )
        appendLine(
          "        default_accept_types=${mediaTypes(
            media.acceptTypes,
          )} if default_accept_types is None else default_accept_types,",
        )
        appendLine("    )")
        appendLine(
          "\n\n__all__ += [${factoryName.pythonStringLiteral()}, ${credentialType.pythonStringLiteral()}, ${alternativeType.pythonStringLiteral()}]",
        )
      }
    val source =
      module.source.replace(
        "from __future__ import annotations\n",
        "from __future__ import annotations\n\n$imports",
      )
    return module.copy(source = source + code)
  }

  private fun credentialType(
    type: String?,
    scheme: String?,
  ): String =
    when (type) {
      "apiKey" -> "ApiKeyCredentials"
      "oauth2", "openIdConnect" -> "OAuthCredentials"
      "http" -> if (scheme.equals("basic", true)) "BasicCredentials" else "BearerCredentials"
      else -> "ProviderCredentials"
    }

  private fun bindings(
    security: GeneratedClientSecurity,
    profile: String?,
  ): String =
    buildString {
      if (security.bindings.isEmpty()) {
        append("[]")
        return@buildString
      }
      appendLine("[")
      security.bindings.forEach { (name, binding) ->
        val transport = security.schemes.getValue(name).credentialTransport()
        appendLine("                    _SecurityBinding(")
        appendLine("                        scheme=${literal(name)},")
        appendLine("                        provider=${literal(binding.provider)},")
        appendLine("                        flow=${literal(binding.flow!!.wireName)},")
        appendLine("                        profile=${literal(profile)},")
        val scopes = security.requirement.permissions[name].orEmpty()
        val scopeTuple = scopes.joinToString(", ", "(", if (scopes.size == 1) ",)" else ")") { literal(it) }
        if (scopeTuple.length + 32 <= 120) {
          appendLine("                        scopes=$scopeTuple,")
        } else {
          appendLine("                        scopes=(")
          scopes.forEach { appendLine("                            ${literal(it)},") }
          appendLine("                        ),")
        }
        mapOf(
          "discovery_url" to binding.discoveryUrl,
          "authorization_url" to binding.authorizationUrl,
          "token_url" to binding.tokenUrl,
          "refresh_url" to binding.refreshUrl,
          "audience" to binding.audience,
          "resource" to binding.resource,
        ).forEach { (key, value) ->
          if (value != null) appendLine("                        $key=${literal(value)},")
        }
        appendLine("                        transport=_SecurityTransport(")
        appendLine("                            location=${literal(transport.location)},")
        appendLine("                            name=${literal(transport.name)},")
        appendLine("                            prefix=${literal(transport.prefix)},")
        appendLine("                        ),")
        appendLine("                    ),")
      }
      append("                ]")
    }

  private fun mediaTypes(values: List<String>): String =
    if (values.isEmpty()) "()" else values.joinToString(", ", "(", ",)") { "_MediaType(${literal(it)})" }

  private fun type(type: GeneratedTypeRef): String =
    when (type.kind) {
      GeneratedTypeRef.Kind.NAMED -> "_models.${type.name.pythonTypeName}"
      else ->
        when (type.name) {
          "integer" -> "int"
          "number" -> "float"
          "boolean" -> "bool"
          else -> "str"
        }
    }

  private fun literal(value: Any?): String = value.pythonValueCode().render(PythonRenderContext(PythonImportSet()))
}
