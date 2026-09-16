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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy
import io.outfoxx.sunday.generator.ir.emit.endpointSecuritySchemes
import io.outfoxx.sunday.generator.kotlin.utils.JaxRsTypes

/** Emits native authentication strategies and constant authorization policies shared by equivalent endpoints. */
internal class KotlinQuarkusSecurityGenerator(
  val typeName: ClassName,
  policies: List<GeneratedEndpointPolicy>,
) {
  private val plan = KotlinQuarkusSecurityPlan(policies)
  private val schemes = policies.endpointSecuritySchemes()
  private val scheme = typeName.nestedClass("Scheme")
  private val request = typeName.nestedClass("Request")
  private val binding = typeName.nestedClass("SchemeBinding")
  private val authenticator = typeName.nestedClass("Authenticator")
  private val identity = ClassName("io.quarkus.security.identity", "SecurityIdentity")
  private val manager = ClassName("io.quarkus.security.identity", "IdentityProviderManager")
  private val routingContext = ClassName("io.vertx.ext.web", "RoutingContext")
  private val uni = ClassName("io.smallrye.mutiny", "Uni")
  private val mechanism = ClassName("io.quarkus.vertx.http.runtime.security", "HttpAuthenticationMechanism")
  private val httpPolicy = ClassName("io.quarkus.vertx.http.runtime.security", "HttpSecurityPolicy")
  private val checkResult = httpPolicy.nestedClass("CheckResult")
  private val singleton = ClassName("jakarta.inject", "Singleton")
  private val requirements = LIST.parameterizedBy(MAP.parameterizedBy(STRING, SET.parameterizedBy(STRING)))

  fun generate(): Map<ClassName, TypeSpec.Builder> {
    if (plan.policies.isEmpty()) return emptyMap()
    return buildMap {
      put(typeName, runtime())
      plan.authentication.forEach { policy -> put(authenticationType(policy), authentication(policy)) }
      plan.policies.filterNot { it.simple }.forEach { policy -> put(policyType(policy), authorization(policy)) }
    }
  }

  fun annotations(value: GeneratedEndpointPolicy?): List<AnnotationSpec> {
    if (value == null) return emptyList()
    if (value.requirements.isEmpty()) {
      return listOf(
        AnnotationSpec.builder(ClassName("jakarta.annotation.security", "PermitAll")).build(),
      )
    }
    val policy = KotlinQuarkusSecurityPlan.policy(value)
    return buildList {
      add(
        AnnotationSpec
          .builder(
            ClassName("io.quarkus.vertx.http.runtime.security.annotation", "HttpAuthenticationMechanism"),
          ).addMember("%T.NAME", authenticationType(policy))
          .build(),
      )
      if (policy.simple) {
        add(AnnotationSpec.builder(ClassName("io.quarkus.security", "Authenticated")).build())
      } else {
        add(
          AnnotationSpec
            .builder(ClassName("io.quarkus.vertx.http.security", "AuthorizationPolicy"))
            .addMember("name = %T.NAME", policyType(policy))
            .build(),
        )
      }
    }
  }

  private fun authenticationType(policy: KotlinQuarkusSecurityPlan.Policy) =
    typeName.peerClass("OpenAPIAuthentication_${policy.authenticationId}")

  private fun policyType(policy: KotlinQuarkusSecurityPlan.Policy) = typeName.peerClass("OpenAPIPolicy_${policy.id}")

  private fun injectedBean(
    name: ClassName,
    description: String,
  ): TypeSpec.Builder =
    TypeSpec
      .classBuilder(name)
      .addKdoc("%L\n", description)
      .addAnnotation(singleton)
      .addType(
        TypeSpec
          .companionObjectBuilder()
          .addProperty(
            PropertySpec
              .builder("NAME", STRING, KModifier.CONST)
              .addKdoc("Canonical identifier shared by generated endpoint bindings.\n")
              .initializer("%S", name.canonicalName)
              .build(),
          ).build(),
      ).primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addAnnotation(ClassName("jakarta.inject", "Inject"))
          .addParameter("security", typeName)
          .build(),
      ).addProperty(PropertySpec.builder("security", typeName, KModifier.PRIVATE).initializer("security").build())

  private fun authentication(policy: KotlinQuarkusSecurityPlan.Policy): TypeSpec.Builder =
    injectedBean(
      authenticationType(policy),
      "Authenticates a fixed set of named schemes selected by generated resource annotations.",
    ).addAnnotation(ClassName("io.quarkus.runtime", "Startup"))
      .addSuperinterface(mechanism)
      .addProperty(
        PropertySpec
          .builder("names", LIST.parameterizedBy(STRING), KModifier.PRIVATE)
          .initializer("listOf(%L)", strings(policy.schemes))
          .build(),
      ).addFunction(
        FunSpec
          .builder("authenticate")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Validates this strategy only when Quarkus explicitly selects it for a generated endpoint.\n")
          .addParameter("context", routingContext)
          .addParameter("identityProviderManager", manager)
          .returns(uni.parameterizedBy(identity.copy(nullable = true)))
          .addCode(
            """
            // Quarkus also tries installed mechanisms on unrelated routes; only its selected mechanism owns this request.
            if (context.get<%T>(%T::class.java.name) !== this) return %T.createFrom().nullItem()
            return security.authenticate(%T(context, identityProviderManager), names)
            """.trimIndent().replace(' ', '·'),
            mechanism,
            mechanism,
            uni,
            request,
          ).build(),
      ).addFunction(
        FunSpec
          .builder("getCredentialTransport")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Identifies this strategy for Quarkus's annotation-based selection.\n")
          .addParameter("context", routingContext)
          .returns(uni.parameterizedBy(ClassName("io.quarkus.vertx.http.runtime.security", "HttpCredentialTransport")))
          .addStatement(
            "return %T.createFrom().item(%T(%T.Type.AUTHORIZATION, %S, NAME))",
            uni,
            ClassName("io.quarkus.vertx.http.runtime.security", "HttpCredentialTransport"),
            ClassName("io.quarkus.vertx.http.runtime.security", "HttpCredentialTransport"),
            "Authorization",
          ).build(),
      ).addFunction(
        FunSpec
          .builder("getChallenge")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Returns the scheme-specific challenges after an authentication failure.\n")
          .addParameter("context", routingContext)
          .returns(uni.parameterizedBy(ClassName("io.quarkus.vertx.http.runtime.security", "ChallengeData")))
          .addStatement("security.challengeHeaders(context, names, false)")
          .addStatement(
            "return %T.createFrom().item(%T(401, null, null))",
            uni,
            ClassName("io.quarkus.vertx.http.runtime.security", "ChallengeData"),
          ).build(),
      )

  private fun authorization(policy: KotlinQuarkusSecurityPlan.Policy): TypeSpec.Builder =
    injectedBean(policyType(policy), "Enforces one fixed OpenAPI policy before Zanzibar or application delegation.")
      .addSuperinterface(httpPolicy)
      .addProperty(
        PropertySpec
          .builder("requirements", requirements, KModifier.PRIVATE)
          .initializer(
            "listOf(%L)",
            policy.alternatives
              .map { alternative ->
                CodeBlock.of(
                  "mapOf(%L)",
                  alternative
                    .map { (name, permissions) ->
                      CodeBlock.of("%S to setOf<String>(%L)", name, strings(permissions))
                    }.joinToCode(", "),
                )
              }.joinToCode(", "),
          ).build(),
      ).addProperty(
        PropertySpec
          .builder("names", LIST.parameterizedBy(STRING), KModifier.PRIVATE)
          .initializer("listOf(%L)", strings(policy.schemes))
          .build(),
      ).addFunction(
        FunSpec
          .builder("name")
          .addModifiers(KModifier.OVERRIDE)
          .returns(STRING)
          .addKdoc("Identifies this policy for generated endpoint bindings.\n")
          .addStatement("return NAME")
          .build(),
      ).addFunction(
        FunSpec
          .builder("checkPermission")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Reuses authenticated scheme identities and checks this policy exactly once.\n")
          .addParameter("request", routingContext)
          .addParameter("identity", uni.parameterizedBy(identity))
          .addParameter("requestContext", httpPolicy.nestedClass("AuthorizationRequestContext"))
          .returns(uni.parameterizedBy(checkResult))
          .addStatement("return identity.map { security.authorize(request, it, names, requirements) }")
          .build(),
      )

  private fun runtime(): TypeSpec.Builder {
    // The public scheme metadata and HTTP challenges are identical across the two Kotlin runtimes.
    val portable = KotlinJAXRSSecurityGenerator(typeName, JaxRsTypes.JAKARTA).generate(schemes).build()
    val permissionReader =
      LambdaTypeName.get(
        parameters = listOf(ParameterSpec.builder("identity", identity).build()),
        returnType = SET.parameterizedBy(STRING),
      )
    return TypeSpec
      .classBuilder(typeName)
      .addKdoc(
        "Native Quarkus security bindings with trusted authenticators and explicit permission readers.\n",
      ).primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter("bindings", MAP.parameterizedBy(STRING, binding))
          .addParameter(
            ParameterSpec
              .builder("subjectSchemes", MAP.parameterizedBy(SET.parameterizedBy(STRING), STRING))
              .defaultValue("emptyMap()")
              .build(),
          ).build(),
      ).addProperty(
        PropertySpec
          .builder(
            "bindings",
            MAP.parameterizedBy(STRING, binding),
            KModifier.PRIVATE,
          ).initializer("bindings.toMap()")
          .build(),
      ).addProperty(
        PropertySpec
          .builder(
            "subjectSchemes",
            MAP.parameterizedBy(SET.parameterizedBy(STRING), STRING),
            KModifier.PRIVATE,
          ).initializer("subjectSchemes.mapKeys { it.key.toSet() }")
          .build(),
      ).addInitializerBlock(
        CodeBlock.of(
          """
          require(bindings.keys.containsAll(schemes.keys)) { "Missing security authenticators: " + (schemes.keys - bindings.keys) }
          require(permissionSchemes.all { bindings.getValue(it).permissions != null }) { "Missing security permission readers: " + permissionSchemes.filter { bindings.getValue(it).permissions == null } }
          require(subjectSchemes.keys == subjectRequirements) { "Subject bindings must match the multi-scheme requirement groups: " + subjectRequirements }
          require(subjectSchemes.all { (names, subject) -> subject in names }) { "Subject scheme must belong to its requirement group" }
          """.trimIndent().replace(' ', '·'),
        ),
      ).addTypes(portable.typeSpecs.filter { it.name in setOf("Scheme", "OAuthFlow") })
      .addType(
        dataType(
          "Request",
          "Native request and identity provider context available to credential validators.",
          mapOf("context" to routingContext, "identityProviderManager" to manager),
        ),
      ).addType(
        TypeSpec
          .funInterfaceBuilder("Authenticator")
          .addKdoc("Authenticates one named scheme asynchronously. Return null for invalid or missing credentials.\n")
          .addFunction(
            FunSpec
              .builder("authenticate")
              .addModifiers(KModifier.ABSTRACT)
              .addKdoc("Returns the provider's trusted native identity; never block the event loop.\n")
              .addParameter(
                "request",
                request,
              ).addParameter("scheme", scheme)
              .addParameter("credential", STRING.copy(nullable = true))
              .returns(uni.parameterizedBy(identity.copy(nullable = true)))
              .build(),
          ).build(),
      ).addType(
        TypeSpec
          .classBuilder("SchemeBinding")
          .addModifiers(KModifier.DATA)
          .addKdoc("Pairs credential validation with an explicit reader for the scheme's trusted scopes or roles.\n")
          .primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addParameter("authenticator", authenticator)
              .addParameter(
                ParameterSpec
                  .builder(
                    "permissions",
                    permissionReader.copy(nullable = true),
                  ).defaultValue("null")
                  .build(),
              ).build(),
          ).addProperty(
            PropertySpec
              .builder(
                "authenticator",
                authenticator,
              ).initializer("authenticator")
              .addKdoc("Validates this named scheme.\n")
              .build(),
          ).addProperty(
            PropertySpec
              .builder("permissions", permissionReader.copy(nullable = true))
              .initializer("permissions")
              .addKdoc("Extracts trusted permissions only when required by an operation.\n")
              .build(),
          ).build(),
      ).addType(
        TypeSpec
          .companionObjectBuilder()
          .addProperty(
            portable.typeSpecs
              .single { it.isCompanion }
              .propertySpecs
              .single { it.name == "schemes" },
          ).addProperty(
            PropertySpec
              .builder("subjectRequirements", SET.parameterizedBy(SET.parameterizedBy(STRING)))
              .addKdoc("Canonical multi-scheme groups requiring an application-selected subject.\n")
              .initializer(
                "setOf(%L)",
                plan.subjectRequirements
                  .map {
                    CodeBlock.of("setOf(%L)", strings(it))
                  }.joinToCode(", "),
              ).build(),
          ).addProperty(
            PropertySpec
              .builder("permissionSchemes", SET.parameterizedBy(STRING), KModifier.PRIVATE)
              .initializer("setOf(%L)", strings(plan.permissionSchemes))
              .build(),
          ).addProperty(
            PropertySpec
              .builder("identitiesKey", STRING, KModifier.PRIVATE)
              .initializer("%S", "${typeName.canonicalName}.identities")
              .build(),
          ).addProperty(
            PropertySpec
              .builder("failuresKey", STRING, KModifier.PRIVATE)
              .initializer("%S", "${typeName.canonicalName}.failures")
              .build(),
          ).build(),
      ).addFunction(authenticate())
      .addFunction(authenticateScheme())
      .apply { if (plan.policies.any { !it.simple }) addFunction(authorize()) }
      .addFunction(credential())
      .addFunction(portable.funSpecs.single { it.name == "challenge" })
      .addFunction(
        FunSpec
          .builder("challengeHeaders")
          .addKdoc("Writes distinct challenges without allocating an additional policy evaluator.\n")
          .addParameter(
            "context",
            routingContext,
          ).addParameter("names", LIST.parameterizedBy(STRING))
          .addParameter("forbidden", BOOLEAN)
          .addCode(
            "names.mapNotNull { challenge(schemes.getValue(it), forbidden) }.distinct().forEach { context.response().headers().add(\"WWW-Authenticate\", it) }\n",
          ).build(),
      )
  }

  private fun authenticate(): FunSpec =
    FunSpec
      .builder("authenticate")
      .addKdoc("Authenticates this fixed strategy once. Single-scheme strategies use the native identity directly.\n")
      .addParameter("request", request)
      .addParameter("names", LIST.parameterizedBy(STRING))
      .returns(uni.parameterizedBy(identity.copy(nullable = true)))
      .addCode(
        """
        if (names.size == 1) return authenticateScheme(request, names.single())
        val identities = linkedMapOf<String, %T?>()
        val failures = linkedMapOf<String, Throwable>()
        var pending = %T.createFrom().item(identities)
        for (name in names) {
          pending = pending.flatMap { results ->
            // Shared strategies cannot know which permission alternative will authorize this endpoint.
            authenticateScheme(request, name).onFailure().recoverWithItem { failure ->
              failures[name] = failure
              null
            }.map { result -> results.apply { put(name, result) } }
          }
        }
        return pending.map { results ->
          val identity = results.values.firstOrNull { it != null }
          if (identity == null && failures.isNotEmpty()) throw failures.values.first()
          request.context.put(identitiesKey, results.toMap())
          request.context.put(failuresKey, failures.toMap())
          identity
        }
        """.trimIndent().replace(' ', '·'),
        identity,
        uni,
      ).build()

  private fun authenticateScheme(): FunSpec =
    FunSpec
      .builder("authenticateScheme")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("request", request)
      .addParameter("name", STRING)
      .returns(uni.parameterizedBy(identity.copy(nullable = true)))
      .addCode(
        """
        val scheme = schemes.getValue(name)
        val credential = credential(request, scheme)
        if (credential == null && !(scheme.type == "mutualTLS" && request.context.request().isSSL)) return %T.createFrom().nullItem()
        return %T.createFrom().deferred {
          bindings.getValue(name).authenticator.authenticate(request, scheme, credential)
        }.onFailure(%T::class.java).recoverWithNull().map { it?.takeUnless { identity -> identity.isAnonymous } }
        """.trimIndent().replace(' ', '·'),
        uni,
        uni,
        ClassName("io.quarkus.security", "AuthenticationFailedException"),
      ).build()

  private fun authorize(): FunSpec =
    FunSpec
      .builder("authorize")
      .addKdoc("Checks named evidence once and publishes the chosen provider identity before request filters run.\n")
      .addParameter("context", routingContext)
      .addParameter("identity", identity)
      .addParameter("names", LIST.parameterizedBy(STRING))
      .addParameter("requirements", requirements)
      .returns(checkResult)
      .addCode(
        """
        val identities = if (names.size == 1) {
          mapOf(names.single() to identity.takeUnless { it.isAnonymous })
        } else {
          checkNotNull(context.get<Map<String, %T?>>(identitiesKey)) { "Generated authentication strategy did not run" }
        }
        val failures = context.get<Map<String, Throwable>>(failuresKey).orEmpty()
        val permissions = mutableMapOf<String, Set<String>>()
        var forbidden = false
        for (alternative in requirements) {
          // A successful earlier alternative makes later provider failures irrelevant.
          alternative.keys.forEach { name -> failures[name]?.let { throw it } }
          if (alternative.keys.any { identities[it] == null }) continue
          if (alternative.all { (name, required) ->
            required.isEmpty() || permissions.getOrPut(name) { bindings.getValue(name).permissions!!.invoke(identities.getValue(name)!!) }.containsAll(required)
          }) {
            val subject = if (alternative.size == 1) alternative.keys.single() else subjectSchemes.getValue(alternative.keys)
            // A distinct native identity makes Quarkus publish it to both the HTTP user and CDI before Zanzibar executes.
            return %T(true, %T.builder(identities.getValue(subject)!!).build())
          }
          forbidden = true
        }
        if (forbidden) {
          challengeHeaders(context, names, true)
          throw %T()
        }
        throw %T()
        """.trimIndent().replace(' ', '·'),
        identity,
        checkResult,
        ClassName("io.quarkus.security.runtime", "QuarkusSecurityIdentity"),
        ClassName("io.quarkus.security", "ForbiddenException"),
        ClassName("io.quarkus.security", "UnauthorizedException"),
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
        val http = request.context.request()
        if (scheme.type == "apiKey") {
          return when (scheme.location) {
            "header" -> http.headers().getAll(scheme.parameterName).singleOrNull()
            "query" -> http.params().getAll(scheme.parameterName).singleOrNull()
            "cookie" -> http.getCookie(scheme.parameterName)?.value
            else -> null
          }?.takeIf { it.isNotEmpty() }
        }
        if (scheme.type == "mutualTLS") return null
        val expected = if (scheme.type == "http") scheme.httpScheme else "Bearer"
        val authorization = http.headers().getAll("Authorization").singleOrNull() ?: return null
        val separator = authorization.indexOf(' ')
        if (separator <= 0 || !authorization.substring(0, separator).equals(expected, ignoreCase = true)) return null
        return authorization.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
        """.trimIndent().replace(' ', '·'),
      ).build()

  private fun dataType(
    name: String,
    description: String,
    fields: Map<String, TypeName>,
  ): TypeSpec =
    TypeSpec
      .classBuilder(name)
      .addModifiers(KModifier.DATA)
      .addKdoc("%L\n", description)
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .apply {
            fields.forEach { (name, type) ->
              addParameter(name, type)
            }
          }.build(),
      ).addProperties(
        fields.map { (name, type) ->
          PropertySpec.builder(name, type).initializer("%N", name).build()
        },
      ).build()

  private fun strings(values: Iterable<String>) = values.map { CodeBlock.of("%S", it) }.joinToCode(", ")
}
