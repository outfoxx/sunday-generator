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

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExport
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiIrSource

/** Shared CLI options for Python source-to-IR generation commands. */
abstract class PythonGenerateCommand(
  name: String,
  help: String,
) : CommonGenerateCommand(name, help) {

  val packageName by option(
    "-package",
    "-pkg",
    help = "Python package directory name",
  )

  val aggregateServices by option(
    "-aggregate-services",
    "-aggregate",
    help = "Generate a root API module that exposes split services",
  ).flag(default = false)

  val aggregateServiceName by option(
    "-aggregate-service-name",
    help = "Name for the aggregate root API type or router factory",
  )

  val servicesFromTags by option(
    "-services-from-tags",
    help = "Use the first operation tag as the generated service when no x-sunday-service is present",
  ).flag(default = false)

  protected fun exportApi(): GeneratedApiIrExport =
    GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = servicesFromTags))
      .exportWithIdentity(files.map { file -> GeneratedApiIrSource(file.toURI()) })

  protected fun pythonOptions(export: GeneratedApiIrExport): PythonGeneratorOptions =
    PythonGeneratorOptions(
      packageName = packageName ?: export.apiId.id,
      aggregateServices = aggregateServices,
      aggregateServiceName = aggregateServiceName,
    )
}
