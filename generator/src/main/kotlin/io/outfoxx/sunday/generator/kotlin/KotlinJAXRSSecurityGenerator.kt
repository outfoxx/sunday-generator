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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy
import io.outfoxx.sunday.generator.kotlin.utils.JaxRsTypes

/** Emits a scheme-aware policy evaluator with application-owned credential validation. */
internal class KotlinJAXRSSecurityGenerator(
  val typeName: ClassName,
  private val jaxRsTypes: JaxRsTypes,
) {
  private val scheme = typeName.nestedClass("Scheme")
  private val flow = typeName.nestedClass("OAuthFlow")
  private val identity = typeName.nestedClass("Identity")
  private val request = typeName.nestedClass("Request")
  private val authenticator = typeName.nestedClass("Authenticator")
  private val requirements = LIST.parameterizedBy(MAP.parameterizedBy(STRING, SET.parameterizedBy(STRING)))

  fun generate(schemes: Map<String, GeneratedSecurityScheme>): TypeSpec.Builder =
    TypeSpec
      .classBuilder(typeName)
      .addKdoc(
        "Enforces named security schemes and permissions before operation delegation.\n" +
          "Register one authenticator per scheme; authenticators must validate credentials and return trusted permissions.\n",
      ).primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter("authenticators", MAP.parameterizedBy(STRING, authenticator))
          .build(),
      ).addProperty(
        PropertySpec
          .builder("authenticators", MAP.parameterizedBy(STRING, authenticator), KModifier.PRIVATE)
          .initializer("authenticators.toMap()")
          .build(),
      ).addInitializerBlock(
        CodeBlock.of(
          "require(authenticators.keys.containsAll(schemes.keys)) {\n" +
            "  %S + (schemes.keys - authenticators.keys).joinToString()\n" +
            "}\n",
          "Missing security authenticators: ",
        ),
      ).addType(
        dataType(
          "Scheme",
          "Credential transport and discovery metadata from the API contract.",
          linkedMapOf(
            "name" to STRING,
            "type" to STRING,
            "httpScheme" to STRING.copy(nullable = true),
            "location" to STRING.copy(nullable = true),
            "parameterName" to STRING.copy(nullable = true),
            "bearerFormat" to STRING.copy(nullable = true),
            "openIdConnectUrl" to STRING.copy(nullable = true),
            "oauthFlows" to MAP.parameterizedBy(STRING, flow),
          ),
        ),
      ).addType(
        dataType(
          "OAuthFlow",
          "OAuth endpoints and advertised scopes; applications select and configure their provider.",
          linkedMapOf(
            "authorizationUrl" to STRING.copy(nullable = true),
            "tokenUrl" to STRING.copy(nullable = true),
            "refreshUrl" to STRING.copy(nullable = true),
            "scopes" to MAP.parameterizedBy(STRING, STRING),
          ),
        ),
      ).addType(
        dataType(
          "Identity",
          "A validated identity and its granted OAuth scopes or role names for one named scheme.",
          linkedMapOf(
            "principal" to ClassName("java.security", "Principal"),
            "permissions" to SET.parameterizedBy(STRING),
          ),
        ),
      ).addType(
        dataType(
          "Request",
          "Request context for credential validation. TLS authenticators must verify the peer certificate.",
          linkedMapOf(
            "headers" to ClassName(jaxRsTypes.securityContext.packageName, "HttpHeaders"),
            "uriInfo" to jaxRsTypes.uriInfo,
            "securityContext" to jaxRsTypes.securityContext,
          ),
        ),
      ).addType(
        TypeSpec
          .funInterfaceBuilder("Authenticator")
          .addKdoc(
            "Validates credentials for its registered scheme. Return null for absent or invalid credentials.\n" +
              "Use non-blocking, local validation or a trusted framework identity on event-loop endpoints.\n",
          ).addFunction(
            FunSpec
              .builder("authenticate")
              .addKdoc(
                "Returns a trusted identity. Credential is null only for mutual TLS; validate the peer separately.\n",
              ).addModifiers(KModifier.ABSTRACT)
              .addParameter("request", request)
              .addParameter("scheme", scheme)
              .addParameter("credential", STRING.copy(nullable = true))
              .returns(identity.copy(nullable = true))
              .build(),
          ).build(),
      ).addType(
        TypeSpec
          .companionObjectBuilder()
          .addProperty(
            PropertySpec
              .builder("schemes", MAP.parameterizedBy(STRING, scheme))
              .addKdoc("Referenced scheme definitions, keyed by the exact contract name.\n")
              .initializer(
                "mapOf(%L)",
                schemes
                  .map { (name, definition) ->
                    CodeBlock.of("%S to %L", name, renderScheme(definition))
                  }.joinToCode(",\n"),
              ).build(),
          ).build(),
      ).addFunction(authorize())
      .addFunction(credential())
      .addFunction(challenge())

  fun renderPolicy(policy: GeneratedEndpointPolicy): CodeBlock =
    CodeBlock.of(
      "listOf(%L)",
      policy.requirements
        .map { requirement ->
          CodeBlock.of(
            "mapOf(%L)",
            requirement.schemes
              .distinct()
              .map { name ->
                CodeBlock.of(
                  "%S to setOf(%L)",
                  name,
                  requirement.permissions[name]
                    .orEmpty()
                    .map { CodeBlock.of("%S", it) }
                    .joinToCode(", "),
                )
              }.joinToCode(", "),
          )
        }.joinToCode(", "),
    )

  private fun dataType(
    name: String,
    documentation: String,
    fields: Map<String, TypeName>,
  ): TypeSpec =
    TypeSpec
      .classBuilder(name)
      .addModifiers(KModifier.DATA)
      .addKdoc("%L\n", documentation)
      .primaryConstructor(
        FunSpec.constructorBuilder().apply { fields.forEach { (name, type) -> addParameter(name, type) } }.build(),
      ).addProperties(
        fields.map { (name, type) ->
          PropertySpec.builder(name, type).initializer("%N", name).build()
        },
      ).build()

  private fun renderScheme(value: GeneratedSecurityScheme): CodeBlock {
    val (location, parameter) =
      when {
        value.headers.isNotEmpty() -> "header" to value.headers.single()
        value.queryParameters.isNotEmpty() -> "query" to value.queryParameters.single()
        value.cookieParameters.isNotEmpty() -> "cookie" to value.cookieParameters.single()
        else -> null to null
      }
    val flows =
      value.oauthFlows
        .map { (name, value) ->
          CodeBlock.of(
            "%S to %T(%S, %S, %S, mapOf(%L))",
            name,
            flow,
            value.authorizationUrl,
            value.tokenUrl,
            value.refreshUrl,
            value.scopes.map { (scope, description) -> CodeBlock.of("%S to %S", scope, description) }.joinToCode(", "),
          )
        }.joinToCode(", ")
    return CodeBlock.of(
      "%T(%S, %S, %S, %S, %S, %S, %S, mapOf(%L))",
      scheme,
      value.name,
      value.type,
      value.scheme,
      location,
      parameter?.let { it.serializationName ?: it.name },
      value.bearerFormat,
      value.openIdConnectUrl,
      flows,
    )
  }

  private fun authorize(): FunSpec =
    FunSpec
      .builder("authorize")
      .addKdoc(
        "Accepts any complete requirement alternative, requiring every scheme and permission within it.\n" +
          "Rejects missing/invalid authentication with 401 and insufficient permissions with 403.\n",
      ).addParameter("request", request)
      .addParameter("requirements", requirements)
      .addCode(
        """
        if (requirements.isEmpty() || requirements.any { it.isEmpty() }) return
        val identities = mutableMapOf<String, %T?>()
        var forbidden = false
        for (alternative in requirements) {
          for (name in alternative.keys) {
            if (!identities.containsKey(name)) {
              val scheme = schemes.getValue(name)
              val credential = credential(request, scheme)
              identities[name] =
                if (credential != null || (scheme.type == "mutualTLS" && request.securityContext.isSecure)) {
                  authenticators.getValue(name).authenticate(request, scheme, credential)
                } else {
                  null
                }
            }
          }
          if (alternative.keys.all { identities[it] != null }) {
            if (alternative.all { (name, permissions) -> identities.getValue(name)!!.permissions.containsAll(permissions) }) {
              return
            }
            forbidden = true
          }
        }
        val response = %T.status(if (forbidden) 403 else 401)
        requirements.flatMap { it.keys }.distinct().mapNotNull { challenge(schemes.getValue(it), forbidden) }
          .distinct().forEach { response.header("WWW-Authenticate", it) }
        if (forbidden) throw %T(response.build())
        throw %T(response.build())
        """.trimIndent(),
        identity,
        ClassName(jaxRsTypes.securityContext.packageName, "Response"),
        ClassName(jaxRsTypes.path.packageName, "ForbiddenException"),
        ClassName(jaxRsTypes.path.packageName, "NotAuthorizedException"),
      ).build()

  private fun credential(): FunSpec =
    FunSpec
      .builder("credential")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("request", request)
      .addParameter("scheme", scheme)
      .returns(STRING.copy(nullable = true))
      .addCode(
        """
        if (scheme.type == "apiKey") {
          return when (scheme.location) {
            "header" -> request.headers.getRequestHeader(scheme.parameterName)?.singleOrNull()
            "query" -> request.uriInfo.queryParameters[scheme.parameterName]?.singleOrNull()
            "cookie" -> request.headers.cookies[scheme.parameterName]?.value
            else -> null
          }?.takeIf { it.isNotEmpty() }
        }
        if (scheme.type == "mutualTLS") return null
        val expected = if (scheme.type == "http") scheme.httpScheme else "Bearer"
        val authorization = request.headers.getRequestHeader("Authorization")?.singleOrNull()
        if (authorization == null) return null
        val separator = authorization.indexOf(' ')
        if (separator <= 0 || !authorization.substring(0, separator).equals(expected, ignoreCase = true)) return null
        return authorization.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
        """.trimIndent(),
      ).build()

  private fun challenge(): FunSpec =
    FunSpec
      .builder("challenge")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("scheme", scheme)
      .addParameter("forbidden", com.squareup.kotlinpoet.BOOLEAN)
      .returns(STRING.copy(nullable = true))
      .addCode(
        """
        val httpScheme = if (scheme.type in setOf("oauth2", "openIdConnect")) "Bearer" else scheme.httpScheme
        return when {
          httpScheme.equals("Bearer", ignoreCase = true) ->
            if (forbidden) "Bearer error=\"insufficient_scope\"" else "Bearer"
          !forbidden && httpScheme.equals("Basic", ignoreCase = true) -> "Basic realm=\"api\""
          else -> null
        }
        """.trimIndent(),
      ).build()
}
