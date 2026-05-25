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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiIrSourceKind
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml

/**
 * CLI command that exports source documents to Sunday generated API IR YAML.
 */
class IrCommand : CliktCommand(name = "ir") {

  override fun help(context: Context): String = "Export source specs to Sunday IR YAML"

  val outputFile by option(
    "-out",
    help = "Output IR YAML file",
  ).file(mustExist = false, canBeFile = true, canBeDir = false)

  val sourceKind by option(
    "--source",
    help = "Source format: auto, raml, openapi, or asyncapi",
  ).convert { value ->
    GeneratedApiIrSourceKind.entries.firstOrNull { sourceKind ->
      sourceKind.name.equals(value, ignoreCase = true)
    } ?: fail("unsupported source format '$value'")
  }.default(GeneratedApiIrSourceKind.AUTO)

  val validateFile by option(
    "--validate",
    help = "Validate an existing Sunday IR YAML file",
  ).file(mustExist = true, canBeFile = true, canBeDir = false)

  val servicesFromTags by option(
    "-services-from-tags",
    help = "Use the first operation tag as the generated service when no x-sunday-service is present",
  ).flag(default = false)

  val sourceFiles by argument(
    help = "Source files",
  ).file(mustExist = true, canBeFile = true, canBeDir = false)
    .multiple()

  override fun run() {
    validateFile?.let { file ->
      val api = GeneratedApiYaml.readPath(file.toPath())
      echo("Valid Sunday IR: ${api.name}")
      return
    }

    if (sourceFiles.isEmpty()) {
      throw UsageError("Missing source file")
    }
    val output = outputFile ?: throw UsageError("Missing required option '-out'")
    GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = servicesFromTags))
      .writeYaml(
        sourceFiles.map { sourceFile ->
          sourceFile.toURI()
        },
        output.toPath(),
        sourceKind,
      )
  }
}
