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

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions.BaseUriMode
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

open class KotlinJAXRSGenerateCommand :
  KotlinGenerateCommand(name = "kotlin/jaxrs", help = "Generate Kotlin for JAX-RS framework") {

  override val mode by option(
    "-mode",
    help = "Target 'client' or 'server' for generated services",
  ).enum<GenerationMode> { it.name.camelCaseToKebabCase() }
    .default(GenerationMode.Client)

  val coroutineServiceMethods by option(
    "-coroutines",
    help = "Generate suspendable service methods for coroutine support",
  ).flag()

  val coroutineFlowMethods by option(
    "-flow-coroutines",
    help = "Generate suspendable flow service methods for coroutine support",
  ).flag(default = false)

  val reactiveResponseType by option(
    "-reactive",
    help = "Generic result type for reactive service methods",
  )

  val explicitSecurityParameters by option(
    "-explicit-security-parameters",
    help = "Include security parameters in service methods",
  ).flag(default = false)

  val baseUriPathMode by option(
    "-base-uri-path-mode",
    help = "Portion of the baseUri that will be used in each generated service's @Path annotation",
  ).enum<BaseUriMode> { it.name.replace("_", "-").lowercase() }

  val alwaysUseResponseReturn by option(
    "-always-use-response-return",
    help = "Service methods will always use the JAX-RS Response as the return type",
  ).flag(default = false)

  val aggregateServices by option(
    "-aggregate-services",
    help = "Generate an aggregate JAX-RS service with subresource locator methods for grouped services",
  ).flag(default = false)

  val aggregateServiceSuffix by option(
    "-aggregate-service-suffix",
    "-aggregate-service-name",
    help = "Aggregate JAX-RS service type name",
  )

  val servicesFromTags by option(
    "-services-from-tags",
    help = "Derive service grouping from source operation tags when x-sunday-service is not present",
  ).flag(default = false)

  val quarkus by option(
    "-quarkus",
    help =
      """
      Enable Quarkus-specific types:
        * `@RestPath`, `@RestQuery`, `@RestHeader` annotations (implied parameter names)
        * `RestResponse<T>` return type
        * `Uni<T>` return type for reactive methods
      """.trimIndent(),
  ).flag(default = false)

  override fun run() {
    println("Generating ${this.outputCategories} types")
    println("Processing ${files.joinToString()}")

    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = servicesFromTags))
        .export(files.map { file -> file.toURI() })

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJaxrsOptions())
      .generateServiceTypes()

    typeRegistry.generateFiles(outputCategories.toSet(), outputDirectory.toPath())
  }

  override fun effectiveProblemLibrary(): KotlinProblemLibrary =
    if (!quarkus && problemLibrary == KotlinProblemLibrary.QUARKUS) {
      KotlinProblemLibrary.ZALANDO
    } else {
      problemLibrary
    }

  private fun kotlinJaxrsOptions(): KotlinJAXRSOptions =
    KotlinJAXRSOptions(
      coroutineFlowMethods,
      coroutineServiceMethods,
      reactiveResponseType,
      explicitSecurityParameters,
      baseUriPathMode,
      alwaysUseResponseReturn,
      servicePackageName ?: packageName,
      problemBaseUri,
      mediaTypes.toList(),
      serviceSuffix,
      quarkus,
      aggregateServices,
      aggregateServiceSuffix,
      servicesFromTags,
    )
}
