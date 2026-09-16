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

import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy

private const val COLLECTIONS_ABC = "collections.abc"

/** Renders Litestar credential bindings and the complete security requirement evaluator. */
internal class PythonSecurityRenderer(
  private val packageName: String,
) {
  val securityType = PythonSymbol("._sunday_security", "ApiSecurity")

  fun render(
    schemes: Map<String, GeneratedSecurityScheme>,
    policies: Map<String, GeneratedEndpointPolicy>,
  ): PythonModule =
    PythonModuleBuilder("$packageName/_sunday_security.py")
      .addExport("ApiSecurity")
      .addExport("Authenticator")
      .addExport("Identity")
      .addExport("Scheme")
      .addExport("OAuthFlow")
      .addCode(
        PythonCodeBlock.of(
          """
          @%T(frozen=True)
          class OAuthFlow:
              ${"\"\"\"OAuth endpoints and advertised scopes; applications configure the provider.\"\"\""}

              authorization_url: str | None
              token_url: str | None
              refresh_url: str | None
              scopes: %T[str, str]


          @%T(frozen=True)
          class Scheme:
              ${"\"\"\"Credential transport and discovery metadata from the API contract.\"\"\""}

              name: str
              type: str
              http_scheme: str | None
              location: str | None
              parameter_name: str | None
              bearer_format: str | None
              open_id_connect_url: str | None
              oauth_flows: %T[str, OAuthFlow]


          @%T(frozen=True)
          class Identity:
              ${"\"\"\"A validated user and the scopes or roles granted by one named scheme.\"\"\""}

              user: object
              permissions: frozenset[str] = frozenset()


          Connection = %T[%T, %T, %T, %T]
          Requirements = tuple[%T[str, frozenset[str]], ...]
          Authenticator = %T[[Connection, Scheme, str | None], %T[Identity | None]]
          Guard = %T[[Connection, %T], %T[None]]
          """.trimIndent(),
          PythonSymbol("dataclasses", "dataclass"),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol("dataclasses", "dataclass"),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol("dataclasses", "dataclass"),
          PythonSymbol("litestar.connection", "ASGIConnection"),
          PythonSymbol("typing", "Any"),
          PythonSymbol("typing", "Any"),
          PythonSymbol("typing", "Any"),
          PythonSymbol("typing", "Any"),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol(COLLECTIONS_ABC, "Callable"),
          PythonSymbol(COLLECTIONS_ABC, "Awaitable"),
          PythonSymbol(COLLECTIONS_ABC, "Callable"),
          PythonSymbol("litestar.handlers", "BaseRouteHandler"),
          PythonSymbol(COLLECTIONS_ABC, "Awaitable"),
        ),
      ).addCode(
        PythonCodeBlock.of(
          "_SCHEMES: %T[str, Scheme] = %T(\n    %C,\n)\n\n_POLICIES: %T[str, Requirements] = %T(\n    %C,\n)",
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol("types", "MappingProxyType"),
          dictionary(schemes.map { (name, scheme) -> name to renderScheme(scheme, 8) }, 4),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol("types", "MappingProxyType"),
          dictionary(policies.map { (name, policy) -> name to renderPolicy(policy, 8) }, 4),
        ),
      ).addCode(
        PythonCodeBlock.of(
          """
          class ApiSecurity:
              ${"\"\"\"Enforce named schemes and permissions before invoking operation delegates.\"\"\""}

              schemes: %T[%T[str, Scheme]] = _SCHEMES

              def __init__(self, authenticators: %T[str, Authenticator]) -> None:
                  ${"\"\"\"Bind validated credential handlers by exact scheme name; missing bindings fail setup.\"\"\""}
                  missing = self.schemes.keys() - authenticators.keys()
                  if missing:
                      raise ValueError("Missing security authenticators: " + ", ".join(sorted(missing)))
                  self._authenticators = dict(authenticators)

              def guard(self, operation: str) -> Guard:
                  ${"\"\"\"Create a Litestar guard for a generated operation's security alternatives.\"\"\""}
                  requirements = _POLICIES[operation]

                  async def authorize(connection: Connection, _: %T) -> None:
                      await self.authorize(connection, requirements)

                  return authorize

              async def authorize(self, connection: Connection, requirements: Requirements) -> None:
                  ${"\"\"\"Accept any complete alternative; reject missing credentials with 401 and permissions with 403.\"\"\""}
                  if not requirements or any(not alternative for alternative in requirements):
                      return
                  identities: dict[str, Identity | None] = {}
                  forbidden = False
                  for alternative in requirements:
                      for name in alternative:
                          if name not in identities:
                              scheme = self.schemes[name]
                              credential = self._credential(connection, scheme)
                              if credential is not None or (
                                  scheme.type == "mutualTLS" and connection.scope.get("scheme") == "https"
                              ):
                                  identities[name] = await self._authenticators[name](connection, scheme, credential)
                              else:
                                  identities[name] = None
                      resolved = {name: identities[name] for name in alternative}
                      if all(identity is not None and identity.user is not None for identity in resolved.values()):
                          if all(
                              alternative[name] <= identity.permissions
                              for name, identity in resolved.items()
                              if identity is not None
                          ):
                              return
                          forbidden = True
                  challenges = {
                      self._challenge(self.schemes[name], forbidden) for alternative in requirements for name in alternative
                  } - {None}
                  headers = {"WWW-Authenticate": ", ".join(sorted(str(value) for value in challenges))} if challenges else {}
                  if forbidden:
                      raise %T(headers=headers)
                  raise %T(headers=headers)

              @staticmethod
              def _credential(connection: Connection, scheme: Scheme) -> str | None:
                  if scheme.type == "apiKey":
                      name = scheme.parameter_name or ""
                      if scheme.location == "header":
                          values = connection.headers.getall(name, [])
                      elif scheme.location == "query":
                          values = connection.query_params.getall(name, [])
                      elif scheme.location == "cookie":
                          value = connection.cookies.get(name)
                          values = [value] if value is not None else []
                      else:
                          values = []
                      return values[0] if len(values) == 1 and values[0] else None
                  if scheme.type == "mutualTLS":
                      return None
                  values = connection.headers.getall("Authorization", [])
                  if len(values) != 1:
                      return None
                  http_scheme, separator, credential = values[0].partition(" ")
                  expected = scheme.http_scheme if scheme.type == "http" else "Bearer"
                  if not separator or http_scheme.lower() != (expected or "").lower():
                      return None
                  return credential.strip() or None

              @staticmethod
              def _challenge(scheme: Scheme, forbidden: bool) -> str | None:
                  http_scheme = "bearer" if scheme.type in {"oauth2", "openIdConnect"} else (scheme.http_scheme or "").lower()
                  if http_scheme == "bearer":
                      return 'Bearer error="insufficient_scope"' if forbidden else "Bearer"
                  if not forbidden and http_scheme == "basic":
                      return 'Basic realm="api"'
                  return None
          """.trimIndent(),
          PythonSymbol("typing", "ClassVar"),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol(COLLECTIONS_ABC, "Mapping"),
          PythonSymbol("litestar.handlers", "BaseRouteHandler"),
          PythonSymbol("litestar.exceptions", "PermissionDeniedException"),
          PythonSymbol("litestar.exceptions", "NotAuthorizedException"),
        ),
      ).build()

  private fun renderPolicy(
    policy: GeneratedEndpointPolicy,
    indent: Int,
  ): PythonCodeBlock {
    if (policy.requirements.isEmpty()) return PythonCodeBlock.of("()")
    return PythonCodeBlock.of(
      "(\n%C\n%L)",
      PythonCodeBlock.join(
        policy.requirements.map { requirement ->
          val entries =
            requirement.schemes.distinct().map { name ->
              val permissions = requirement.permissions[name].orEmpty()
              val value =
                if (permissions.isEmpty()) {
                  PythonCodeBlock.of("frozenset()")
                } else {
                  PythonCodeBlock.of(
                    "frozenset(\n%L[\n%C\n%L],\n%L)",
                    " ".repeat(indent + 12),
                    PythonCodeBlock.join(
                      permissions.map { PythonCodeBlock.of("%L%S,", " ".repeat(indent + 16), it) },
                      "\n",
                    ),
                    " ".repeat(indent + 12),
                    " ".repeat(indent + 8),
                  )
                }
              name to value
            }
          PythonCodeBlock.of("%L%C,", " ".repeat(indent + 4), dictionary(entries, indent + 4))
        },
        "\n",
      ),
      " ".repeat(indent),
    )
  }

  private fun renderScheme(
    scheme: GeneratedSecurityScheme,
    indent: Int,
  ): PythonCodeBlock {
    val (location, parameter) =
      when {
        scheme.headers.isNotEmpty() -> "header" to scheme.headers.single()
        scheme.queryParameters.isNotEmpty() -> "query" to scheme.queryParameters.single()
        scheme.cookieParameters.isNotEmpty() -> "cookie" to scheme.cookieParameters.single()
        else -> null to null
      }

    fun optional(value: String?) = value?.let { PythonCodeBlock.of("%S", it) } ?: PythonCodeBlock.of("None")
    return constructor(
      "Scheme",
      listOf(
        "name" to optional(scheme.name),
        "type" to optional(scheme.type),
        "http_scheme" to optional(scheme.scheme),
        "location" to optional(location),
        "parameter_name" to optional(parameter?.let { it.serializationName ?: it.name }),
        "bearer_format" to optional(scheme.bearerFormat),
        "open_id_connect_url" to optional(scheme.openIdConnectUrl),
        "oauth_flows" to
          dictionary(
            scheme.oauthFlows.map { (name, flow) ->
              name to
                constructor(
                  "OAuthFlow",
                  listOf(
                    "authorization_url" to optional(flow.authorizationUrl),
                    "token_url" to optional(flow.tokenUrl),
                    "refresh_url" to optional(flow.refreshUrl),
                    "scopes" to
                      dictionary(
                        flow.scopes.map { (scope, description) -> scope to optional(description) },
                        indent + 12,
                      ),
                  ),
                  indent + 8,
                )
            },
            indent + 4,
          ),
      ),
      indent,
    )
  }

  private fun dictionary(
    entries: List<Pair<String, PythonCodeBlock>>,
    indent: Int,
  ): PythonCodeBlock =
    if (entries.isEmpty()) {
      PythonCodeBlock.of("{}")
    } else {
      PythonCodeBlock.of(
        "{\n%C\n%L}",
        PythonCodeBlock.join(
          entries.map { (key, value) -> PythonCodeBlock.of("%L%S: %C,", " ".repeat(indent + 4), key, value) },
          "\n",
        ),
        " ".repeat(indent),
      )
    }

  private fun constructor(
    name: String,
    fields: List<Pair<String, PythonCodeBlock>>,
    indent: Int,
  ): PythonCodeBlock =
    PythonCodeBlock.of(
      "%L(\n%C\n%L)",
      name,
      PythonCodeBlock.join(
        fields.map { (key, value) -> PythonCodeBlock.of("%L%L=%C,", " ".repeat(indent + 4), key, value) },
        "\n",
      ),
      " ".repeat(indent),
    )
}
