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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedExceptionRef
import io.outfoxx.sunday.generator.ir.GeneratedHttpStatusRange
import io.outfoxx.sunday.generator.ir.GeneratedPolicyDuration
import io.outfoxx.sunday.generator.ir.GeneratedPolicyValues
import java.math.BigInteger
import java.security.MessageDigest
import java.time.temporal.ChronoUnit

/** Collects operation-owned guards while sharing construction code for identical effective policies. */
internal class KotlinPolicyGuards(
  private val problem: KotlinProblemLibrarySupport,
  private val exceptionType: (GeneratedExceptionRef) -> ClassName,
) {
  private val names = NameAllocator().apply { newName("classify") }
  private val factories = linkedMapOf<GeneratedPolicyValues, String>()
  private val producers = mutableListOf<PropertySpec>()

  fun add(
    container: ClassName,
    operation: String,
    policy: GeneratedPolicyValues,
  ): String {
    val factory =
      factories.getOrPut(policy) {
        val digest = MessageDigest.getInstance("SHA-256").digest(policy.toString().toByteArray(Charsets.UTF_8))
        names.newName("policy_" + digest.take(6).joinToString("") { "%02x".format(it) })
      }
    val name = names.newName(operation)
    val identifier = "${container.canonicalName}.$name"
    producers.add(
      PropertySpec
        .builder(name, guard)
        .addKdoc("Operation-owned fault-tolerance state. Configuration identifier: %L.\n", identifier)
        .addAnnotation(JvmField::class)
        .addAnnotation(
          AnnotationSpec
            .builder(ClassName("jakarta.enterprise.inject", "Produces"))
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
            .build(),
        ).addAnnotation(
          AnnotationSpec
            .builder(ClassName("io.smallrye.common.annotation", "Identifier"))
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
            .addMember("%S", identifier)
            .build(),
        ).initializer("%N(%S)", factory, identifier)
        .build(),
    )
    return identifier
  }

  fun generate(): TypeSpec =
    TypeSpec
      .objectBuilder("Guards")
      .addKdoc("CDI guards with independent state for each operation and shared policy factories.\n")
      .addAnnotation(ClassName("jakarta.inject", "Singleton"))
      .addProperties(producers)
      .addFunctions(factories.map { (policy, name) -> factory(name, policy) })
      .addFunction(classifier())
      .build()

  private fun factory(
    name: String,
    policy: GeneratedPolicyValues,
  ): FunSpec {
    val code =
      CodeBlock
        .builder()
        .add("val guard = %T.create()\n", guard)
        .indent()
        .add(".withDescription(identifier)\n")
    policy.timeout?.value?.let {
      if (it == GeneratedPolicyDuration()) genError("Quarkus timeout must be positive")
      code
        .add(".withTimeout()\n")
        .indent()
        .duration("duration", it)
        .add(".done()\n")
        .unindent()
    }
    policy.retry?.value?.let { retry ->
      code.add(".withRetry()\n").indent()
      retry.maxRetries?.let { code.add(".maxRetries(%L)\n", it) }
      retry.delay?.let { code.duration("delay", it) }
      retry.maxDuration?.let { code.duration("maxDuration", it) }
      retry.jitter?.let { code.duration("jitter", it) }
      code.add(".whenException { error ->\n").indent()
      predicate(
        code,
        retry.retryOn,
        retry.abortOn,
        listOf(ClassName("java.util.concurrent", "CancellationException")),
      )
      code.unindent().add("}\n.done()\n").unindent()
    }
    policy.circuitBreaker?.value?.let { breaker ->
      code.add(".withCircuitBreaker()\n").indent()
      code.add(".name(identifier)\n")
      breaker.requestVolumeThreshold?.let { code.add(".requestVolumeThreshold(%L)\n", it) }
      breaker.successThreshold?.let { code.add(".successThreshold(%L)\n", it) }
      breaker.failureRatio?.let { code.add(".failureRatio(%L)\n", it) }
      breaker.delay?.let { code.duration("delay", it) }
      code.add(".`when` { error ->\n").indent()
      predicate(code, breaker.failOn, breaker.skipOn)
      code.unindent().add("}\n.done()\n").unindent()
    }
    policy.rateLimit?.value?.let { rate ->
      val value = rate.value ?: genError("Quarkus rateLimit policy requires integer key 'value'")
      if (rate.window == GeneratedPolicyDuration()) genError("Quarkus rateLimit.window must be positive")
      code.add(".withRateLimit()\n").indent().add(".limit(%L)\n", value)
      rate.window?.let { code.duration("window", it) }
      rate.minSpacing?.let { code.duration("minSpacing", it) }
      rate.type?.let {
        code.add(
          ".type(%T.%L)\n",
          ClassName("io.smallrye.faulttolerance.api", "RateLimitType"),
          it.name,
        )
      }
      code.add(".done()\n").unindent()
    }
    code.add(".build()\n").unindent().add("return guard\n")
    return FunSpec
      .builder(name)
      .addModifiers(KModifier.PRIVATE)
      .addParameter("identifier", String::class)
      .returns(guard)
      .addCode(code.build())
      .build()
  }

  private fun predicate(
    code: CodeBlock.Builder,
    includes: List<GeneratedExceptionRef>?,
    excludes: List<GeneratedExceptionRef>?,
    additionalExcludes: List<ClassName> = emptyList(),
  ) {
    val includeTypes =
      includes?.filter { it.status == null }?.map(exceptionType)
        ?: listOf(ClassName("java.lang", "Exception"))
    code.add(
      "classify(\n  error,\n  { failure -> %L },\n  { failure -> %L },\n)\n",
      condition(includeTypes, includes.orEmpty().mapNotNull { it.status }),
      condition(
        excludes.orEmpty().filter { it.status == null }.map(exceptionType) + additionalExcludes,
        excludes.orEmpty().mapNotNull { it.status },
      ),
    )
  }

  private fun condition(
    types: List<ClassName>,
    statuses: List<GeneratedHttpStatusRange>?,
  ): CodeBlock {
    val tests = types.distinct().map { CodeBlock.of("failure·is·%T", it) }.toMutableList()
    if (!statuses.isNullOrEmpty()) {
      val ranges = statuses.map { CodeBlock.of("%L in %L..%L", problem.statusCodeAccess("failure"), it.first, it.last) }
      tests.add(CodeBlock.of("(failure is %T && (%L))", problem.throwableType, ranges.joinToCode(" || ")))
      val responseRanges = statuses.map { CodeBlock.of("failure.response.status in %L..%L", it.first, it.last) }
      tests.add(
        CodeBlock.of(
          "(failure is %T && (%L))",
          ClassName("jakarta.ws.rs", "WebApplicationException"),
          responseRanges.joinToCode(" || "),
        ),
      )
    }
    // Referencing the parameter also avoids unused-lambda-parameter warnings for explicitly empty lists.
    return if (tests.isEmpty()) CodeBlock.of("failure.let { false }") else tests.joinToCode(" || ")
  }

  private fun classifier(): FunSpec =
    FunSpec
      .builder("classify")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("error", Throwable::class)
      .addParameter(
        "include",
        LambdaTypeName.get(
          parameters =
            arrayOf(
              ParameterSpec.unnamed(Throwable::class),
            ),
          returnType = BOOLEAN,
        ),
      ).addParameter(
        "exclude",
        LambdaTypeName.get(
          parameters =
            arrayOf(
              ParameterSpec.unnamed(Throwable::class),
            ),
          returnType = BOOLEAN,
        ),
      ).returns(Boolean::class)
      .addComment("Exclusions win across the entire cause chain, including registered Problem subtypes.")
      .addStatement("val seen = %T<Throwable, Boolean>()", java.util.IdentityHashMap::class)
      .addStatement("var current: Throwable? = error")
      .addStatement("var matched = false")
      .beginControlFlow("while (current != null && seen.put(current, true) == null)")
      .addStatement("if (exclude(current)) return false")
      .addStatement("matched = matched || include(current)")
      .addStatement("current = current.cause")
      .endControlFlow()
      .addStatement("return matched")
      .build()

  private fun CodeBlock.Builder.duration(
    method: String,
    duration: GeneratedPolicyDuration,
  ): CodeBlock.Builder {
    val nanos =
      BigInteger.valueOf(duration.seconds) * BigInteger.valueOf(1_000_000_000) +
        BigInteger.valueOf(duration.nanos.toLong())
    val (unit, amount) =
      listOf(
        ChronoUnit.HOURS,
        ChronoUnit.MINUTES,
        ChronoUnit.SECONDS,
        ChronoUnit.MILLIS,
        ChronoUnit.MICROS,
        ChronoUnit.NANOS,
      ).firstNotNullOfOrNull { unit ->
        val parts = nanos.divideAndRemainder(BigInteger.valueOf(unit.duration.toNanos()))
        if (parts[1] == BigInteger.ZERO && parts[0] <= BigInteger.valueOf(Long.MAX_VALUE)) unit to parts[0] else null
      } ?: genError("Policy duration cannot be represented exactly by a Quarkus guard")
    return add(".%L(%L, %T.%L)\n", method, amount, ChronoUnit::class, unit.name)
  }

  companion object {
    private val guard = ClassName("io.smallrye.faulttolerance.api", "Guard")
    val applyGuard = ClassName("io.smallrye.faulttolerance.api", "ApplyGuard")
  }
}
