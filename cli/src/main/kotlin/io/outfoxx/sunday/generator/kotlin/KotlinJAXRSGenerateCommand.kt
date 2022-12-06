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

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator.Options.BaseUriMode
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

  override fun generatorFactory(document: Document, shapeIndex: ShapeIndex, typeRegistry: KotlinTypeRegistry) =
    KotlinJAXRSGenerator(
      document,
      shapeIndex,
      typeRegistry,
      KotlinJAXRSGenerator.Options(
        coroutineServiceMethods,
        reactiveResponseType,
        explicitSecurityParameters,
        baseUriPathMode,
        alwaysUseResponseReturn,
        servicePackageName ?: packageName,
        problemBaseUri,
        mediaTypes.toList(),
        serviceSuffix,
      ),
    )
}
