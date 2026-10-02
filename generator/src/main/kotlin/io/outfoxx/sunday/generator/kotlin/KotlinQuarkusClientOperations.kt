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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.genError

/** Keeps one native retry owner outside the concrete authenticated transport attempt. */
internal object KotlinQuarkusClientOperations {
  private val runtimePackage = "io.outfoxx.sunday.client.quarkus"
  private val invocation = ClassName(runtimePackage, "ClientInvocation")
  private val authentication = ClassName(runtimePackage, "ClientAuthentication")
  private val retry = ClassName("org.eclipse.microprofile.faulttolerance", "Retry")

  fun requiredAbortTypes(operation: FunSpec.Builder): List<ClassName> =
    if (operation.annotations.any { it.typeName == authentication }) {
      listOf(invocation.nestedClass("Stopped"), ClassName("java.util.concurrent", "CancellationException"))
    } else {
      emptyList()
    }

  fun add(
    operation: FunSpec,
    service: TypeSpec.Builder,
    names: NameAllocator,
  ) {
    if (operation.annotations.none { it.typeName == authentication }) {
      service.addFunction(operation)
      return
    }
    val defaults = ClassName("kotlin.jvm", "JvmDefaultWithoutCompatibility")
    if (service.annotations.none { it.typeName == defaults }) {
      service.addAnnotation(defaults)
      service.addAnnotation(
        AnnotationSpec
          .builder(ClassName("org.eclipse.microprofile.rest.client.annotation", "RegisterProvider"))
          .addMember("%T::class", ClassName(runtimePackage, "ClientAuthenticationFilter"))
          .build(),
      )
    }
    val policyName = names.newName(operation.name + "WithRetry")
    val transportName = names.newName(operation.name + "Transport")
    val parameterNames = NameAllocator().apply { operation.parameters.forEach { newName(it.name) } }
    val invocationName = parameterNames.newName("invocation")
    val attemptName = parameterNames.newName("attempt")
    val rawReturn = (operation.returnType as? ParameterizedTypeName)?.rawType
    val suffix =
      when {
        KModifier.SUSPEND in operation.modifiers -> "Suspend"
        rawReturn == ClassName("io.smallrye.mutiny", "Uni") -> "Uni"
        rawReturn == ClassName("io.smallrye.mutiny", "Multi") -> "Multi"
        rawReturn == ClassName("java.util.concurrent", "CompletionStage") -> "Stage"
        else -> ""
      }
    val declaredRetry = operation.annotations.singleOrNull { it.typeName == retry }
    if (suffix == "Multi" && declaredRetry != null) {
      genError(
        "Authenticated streaming operation '${operation.name}' cannot replay a subscription; disable its client retry policy",
      )
    }
    val authenticationOnly = declaredRetry == null
    val callArguments =
      (
        operation.parameters.map {
          CodeBlock.of("%N", it)
        } + CodeBlock.of("%N", invocationName)
      ).joinToCode(", ")

    fun declaration(name: String): FunSpec.Builder =
      operation.toBuilder(name).apply {
        modifiers.remove(KModifier.ABSTRACT)
        annotations.clear()
        parameters.clear()
        operation.parameters.forEach { addParameter(it.toBuilder().apply { annotations.clear() }.build()) }
      }
    service.addFunction(
      declaration(operation.name)
        .addKdoc("Executes this operation with one invocation-local authentication recovery budget.\n")
        .addStatement(
          "return %T.execute%L(%L, { %N -> %N(%L) })",
          invocation,
          suffix,
          authenticationOnly,
          invocationName,
          policyName,
          callArguments,
        ).build(),
    )
    val policy =
      declaration(policyName)
        .addKdoc("Native retry boundary used by [%N]; applications should call that operation.\n", operation.name)
        .addParameter(invocationName, invocation)
        .addStatement(
          "return %N.attempt%L({ %N -> %N(%L) })",
          invocationName,
          suffix,
          attemptName,
          transportName,
          (operation.parameters.map { CodeBlock.of("%N", it) } + CodeBlock.of("%N", attemptName)).joinToCode(", "),
        )
    if (suffix != "Multi") policy.addAnnotation(retryAnnotation(declaredRetry))
    service.addFunction(policy.build())
    service.addFunction(
      operation
        .toBuilder(transportName)
        .apply {
          annotations.removeAll { it.typeName == retry }
          addKdoc("Single native transport attempt used by [%N].\n", operation.name)
          addParameter(
            ParameterSpec
              .builder(attemptName, invocation.nestedClass("Attempt"))
              .addAnnotation(ClassName("jakarta.ws.rs.core", "Context"))
              .build(),
          )
        }.build(),
    )
  }

  private fun retryAnnotation(declared: AnnotationSpec?): AnnotationSpec =
    declared ?: AnnotationSpec
      .builder(retry)
      .apply {
        addMember("maxRetries = 1")
        addMember("delay = 0")
        addMember("jitter = 0")
        addMember("retryOn = [%T::class]", invocation.nestedClass("Recoverable"))
        addMember(
          "abortOn = [%T::class, %T::class]",
          invocation.nestedClass("Stopped"),
          ClassName("java.util.concurrent", "CancellationException"),
        )
      }.build()
}
