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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec

/** Adapts the runtime-owned SPI at the generated package boundary, preserving explicit application overrides. */
internal object KotlinQuarkusProviderFactory {
  fun generate(security: ClassName): TypeSpec.Builder {
    val provider = ClassName("io.outfoxx.sunday.jaxrs.quarkus", "ServerSecurityProvider")
    val selector = ClassName("io.outfoxx.sunday.jaxrs.quarkus", "ServerSecuritySubjectSelector")
    val instance = ClassName("jakarta.enterprise.inject", "Instance")
    val any = ClassName("jakarta.enterprise.inject", "Any")
    return TypeSpec
      .classBuilder(security.peerClass("OpenAPISecurityFactory"))
      .addKdoc("Resolves shared runtime providers for this contract; an application producer overrides this default.\n")
      .addAnnotation(ClassName("jakarta.inject", "Singleton"))
      .addFunction(
        FunSpec
          .builder("security")
          .addKdoc("Fails at startup for missing or ambiguous providers and subject selections.\n")
          .addAnnotation(ClassName("jakarta.enterprise.inject", "Produces"))
          .addAnnotation(ClassName("jakarta.inject", "Singleton"))
          .addAnnotation(ClassName("io.quarkus.arc", "DefaultBean"))
          .addParameter(
            ParameterSpec.builder("providers", instance.parameterizedBy(provider)).addAnnotation(any).build(),
          ).addParameter(
            ParameterSpec.builder("selectors", instance.parameterizedBy(selector)).addAnnotation(any).build(),
          ).returns(security)
          .addCode(
            """
            val available = providers.toList().groupBy { it.name }
            val bindings = %1T.providers.values.toSet().associateWith { name ->
              val matches = available[name].orEmpty()
              require(matches.size == 1) { "Expected exactly one server security provider for '${'$'}name', found " + matches.size }
              val binding = matches.single().binding()
              %1T.SchemeBinding(
                %1T.Authenticator { request, scheme, credential ->
                  binding.authenticator.authenticate(
                    %2T.Request(request.context, request.identityProviderManager),
                    %2T.Scheme(
                      scheme.name, scheme.type, scheme.httpScheme, scheme.location, scheme.parameterName,
                      scheme.bearerFormat, scheme.openIdConnectUrl,
                      scheme.oauthFlows.mapValues { (_, flow) ->
                        %2T.OAuthFlow(flow.authorizationUrl, flow.tokenUrl, flow.refreshUrl, flow.scopes)
                      },
                    ),
                    credential,
                  )
                },
                binding.permissions,
              )
            }
            val subjects = %1T.subjectRequirements.associateWith { schemes ->
              require(!selectors.isUnsatisfied && !selectors.isAmbiguous) {
                "Expected exactly one server security subject selector for " + %3S
              }
              selectors.get().select(%3S, schemes)
            }
            return %1T(bindings, subjects)
            """.trimIndent().replace(' ', '·'),
            security,
            provider,
            security.canonicalName,
          ).build(),
      )
  }
}
