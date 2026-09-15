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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointAccess
import io.outfoxx.sunday.generator.ir.emit.GeneratedEndpointPolicy
import io.outfoxx.sunday.generator.kotlin.utils.JaxRsTypes
import io.outfoxx.sunday.generator.kotlin.utils.addAnnotation

/** Splits generated endpoint contracts into application delegates and concrete transport resources. */
internal class KotlinJAXRSResourceAdapterGenerator(
  private val jaxRsTypes: JaxRsTypes,
  private val quarkus: Boolean,
  private val securityGenerator: KotlinJAXRSSecurityGenerator? = null,
  private val quarkusSecurityGenerator: KotlinQuarkusSecurityGenerator? = null,
) {

  fun resourceTypeName(serviceTypeName: ClassName): ClassName =
    serviceTypeName.peerClass("${serviceTypeName.simpleName}Resource")

  fun handler(service: TypeSpec): TypeSpec.Builder =
    service.toBuilder().apply {
      annotations.clear()
      addKdoc("Application-owned operation behavior. Register the generated resource with the server.\n")
      funSpecs.clear()
      addFunctions(
        service.funSpecs.map { function ->
          function
            .toBuilder()
            .apply {
              annotations.clear()
              parameters.clear()
              addParameters(function.parameters.map { it.withoutAnnotations() })
              returns(function.returnType.withoutAnnotations())
              addKdoc("Handles the %L operation.\n", function.name)
            }.build()
        },
      )
    }

  fun resource(
    serviceTypeName: ClassName,
    service: TypeSpec,
    authentication: Map<String, GeneratedEndpointAccess>,
    root: Boolean,
    policies: Map<String, GeneratedEndpointPolicy?> = emptyMap(),
  ): TypeSpec.Builder =
    resourceBuilder(resourceTypeName(serviceTypeName), service)
      .apply {
        addKdoc("Generated endpoint implementation delegating operation behavior to [%T].\n", serviceTypeName)
        addDelegates(
          mapOf("delegate" to serviceTypeName) +
            (securityGenerator?.let { mapOf("endpointSecurity" to it.typeName) } ?: emptyMap()),
        )
        if (root && service.annotations.none { it.typeName == jaxRsTypes.path }) {
          addAnnotation(jaxRsTypes.path, "/")
        }
        val nativeAnnotations =
          quarkusSecurityGenerator?.let { generator ->
            service.funSpecs.associate { it.name to generator.annotations(policies.getValue(it.name)) }
          }
        val sharedAnnotations = nativeAnnotations?.values?.distinct()?.singleOrNull()
        sharedAnnotations?.let(::addAnnotations)
        service.funSpecs.forEach { function ->
          val endpoint = function.toBuilder()
          endpoint.modifiers.remove(KModifier.ABSTRACT)
          endpoint.addKdoc("Invokes the application delegate for %L.\n", function.name)
          if (nativeAnnotations != null) {
            if (sharedAnnotations == null) endpoint.addAnnotations(nativeAnnotations.getValue(function.name))
          } else if (securityGenerator != null) {
            policies.getValue(function.name)?.let { endpoint.applySecurityPolicy(it, securityGenerator) }
          } else {
            endpoint.applyAuthentication(authentication.getValue(function.name))
          }
          val arguments = function.parameters.map { CodeBlock.of("%N", it.name) }.joinToCode(", ")
          val statement = if (function.returnType == UNIT) "this.delegate.%N(%L)" else "return this.delegate.%N(%L)"
          endpoint.addStatement(statement, function.name, arguments)
          addFunction(endpoint.build())
        }
      }

  // Subresources stay CDI-managed so Quarkus interceptors run on their endpoint methods.
  fun aggregateResource(
    aggregateTypeName: ClassName,
    aggregate: TypeSpec,
  ): TypeSpec.Builder =
    resourceBuilder(resourceTypeName(aggregateTypeName), aggregate)
      .apply {
        addKdoc("Generated root resource composing the service resources.\n")
        val names = NameAllocator()
        val delegates =
          aggregate.funSpecs.associate { function ->
            names.newName(function.name) to resourceTypeName(function.returnType as ClassName)
          }
        addDelegates(delegates)
        aggregate.funSpecs.zip(delegates.keys).forEach { (function, name) ->
          addFunction(
            function
              .toBuilder()
              .apply {
                modifiers.remove(KModifier.ABSTRACT)
                returns(delegates.getValue(name))
                addKdoc("Returns the managed %L subresource.\n", name)
                addStatement("return this.%N", name)
              }.build(),
          )
        }
      }

  private fun resourceBuilder(
    resourceTypeName: ClassName,
    contract: TypeSpec,
  ): TypeSpec.Builder =
    TypeSpec.classBuilder(resourceTypeName).apply {
      addAnnotations(contract.annotations)
      if (quarkus) {
        addAnnotation(ClassName("jakarta.inject", "Singleton"))
      }
    }

  private fun TypeSpec.Builder.addDelegates(delegates: Map<String, ClassName>) {
    primaryConstructor(
      FunSpec
        .constructorBuilder()
        .apply {
          if (quarkus) {
            addAnnotation(ClassName("jakarta.inject", "Inject"))
          }
          delegates.forEach { (name, type) -> addParameter(name, type) }
        }.build(),
    )
    delegates.forEach { (name, type) ->
      addProperty(PropertySpec.builder(name, type, KModifier.PRIVATE).initializer("%N", name).build())
    }
  }

  private fun FunSpec.Builder.applyAuthentication(access: GeneratedEndpointAccess) {
    when (access) {
      GeneratedEndpointAccess.UNSPECIFIED -> Unit
      GeneratedEndpointAccess.PUBLIC -> {
        val namespace = if (jaxRsTypes == JaxRsTypes.JAVAX) "javax" else "jakarta"
        addAnnotation(ClassName("$namespace.annotation.security", "PermitAll"))
      }
      GeneratedEndpointAccess.AUTHENTICATED -> {
        if (quarkus) {
          addAnnotation(ClassName("io.quarkus.security", "Authenticated"))
        } else {
          val existingContext =
            parameters.firstOrNull {
              it.type == jaxRsTypes.securityContext &&
                it.annotations.any { annotation -> annotation.typeName == jaxRsTypes.context }
            }
          val contextName =
            existingContext?.name ?: run {
              val names = NameAllocator()
              parameters.forEach { names.newName(it.name) }
              names.newName("securityContext").also { name ->
                addParameter(
                  ParameterSpec.builder(name, jaxRsTypes.securityContext).addAnnotation(jaxRsTypes.context).build(),
                )
              }
            }
          beginControlFlow("if (%N.userPrincipal == null)", contextName)
          addStatement(
            "throw %T(%T.status(401).build())",
            ClassName(jaxRsTypes.path.packageName, "NotAuthorizedException"),
            jaxRsTypes.rawResponse,
          )
          endControlFlow()
        }
      }
    }
  }

  private fun FunSpec.Builder.applySecurityPolicy(
    policy: GeneratedEndpointPolicy,
    generator: KotlinJAXRSSecurityGenerator,
  ) {
    // The generated evaluator owns authentication, including schemes outside Quarkus's configured mechanisms.
    applyAuthentication(GeneratedEndpointAccess.PUBLIC)
    if (policy.requirements.isEmpty()) return
    val headers = contextParameter(ClassName(jaxRsTypes.securityContext.packageName, "HttpHeaders"), "httpHeaders")
    val uriInfo = contextParameter(jaxRsTypes.uriInfo, "uriInfo")
    val securityContext = contextParameter(jaxRsTypes.securityContext, "securityContext")
    addStatement(
      "this.endpointSecurity.authorize(%T(%N, %N, %N), %L)",
      generator.typeName.nestedClass("Request"),
      headers,
      uriInfo,
      securityContext,
      generator.renderPolicy(policy),
    )
  }

  private fun FunSpec.Builder.contextParameter(
    type: TypeName,
    suggestedName: String,
  ): String {
    parameters
      .firstOrNull { parameter ->
        parameter.type == type && parameter.annotations.any { it.typeName == jaxRsTypes.context }
      }?.let { return it.name }
    val names = NameAllocator()
    parameters.forEach { names.newName(it.name) }
    val name = names.newName(suggestedName)
    addParameter(ParameterSpec.builder(name, type).addAnnotation(jaxRsTypes.context).build())
    return name
  }

  private fun ParameterSpec.withoutAnnotations(): ParameterSpec =
    toBuilder(type = type.withoutAnnotations())
      .apply {
        annotations.clear()
      }.build()

  private fun TypeName.withoutAnnotations(): TypeName =
    when (this) {
      is ParameterizedTypeName ->
        rawType
          .parameterizedBy(typeArguments.map { it.withoutAnnotations() })
          .copy(nullable = isNullable)
      else -> copy(annotations = emptyList())
    }
}
