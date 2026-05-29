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

package io.outfoxx.sunday.generator.swift

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions

open class SwiftSundayGenerateCommand :
  SwiftGenerateCommand(name = "swift/sunday", help = "Generate Swift client for Sunday framework") {

  val aggregateServices by option(
    "-aggregate-services",
    help = "Generate a root API that exposes split services as properties",
  ).flag(default = false)

  val aggregateServiceName by option(
    "-aggregate-service-name",
    help = "Name for the aggregate root API service",
  )

  val servicesFromTags by option(
    "-services-from-tags",
    help = "Use the first operation tag as the generated service when no x-sunday-service is present",
  ).flag(default = false)

  override fun run() {
    println("Generating ${this.outputCategories} types")
    println("Processing ${files.joinToString()}")

    val api =
      GeneratedApiIrExporter(
        GeneratedApiIrOptions(deriveServicesFromTags = servicesFromTags, generationMode = GenerationMode.Client),
      ).export(files.map { file -> file.toURI() })

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayOptions())
      .generateServiceTypes()

    typeRegistry.generateFiles(outputCategories.toSet(), outputDirectory.toPath())
  }

  private fun swiftSundayOptions(): SwiftSundayOptions =
    SwiftSundayOptions(
      problemBaseUri,
      mediaTypes.toList(),
      serviceSuffix,
      aggregateServices,
      aggregateServiceName,
      servicesFromTags,
    )
}
