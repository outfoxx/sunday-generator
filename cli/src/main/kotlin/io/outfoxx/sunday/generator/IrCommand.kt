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
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiIrSourceKind
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions

/**
 * CLI command that exports source documents to Sunday generated API IR YAML.
 */
class IrCommand : CliktCommand(name = "ir") {

  private val referenceOptions by OpenApiReferenceOptionGroup()

  /** Explicit cache directory, or null when using the default. */
  val openApiReferenceCacheDirectory get() = referenceOptions.cacheDirectory

  /** Whether remote documents must be loaded without HTTP requests. */
  val openApiOffline get() = referenceOptions.offline

  /** Whether trusted specifications may access private network destinations and configured proxies. */
  val openApiAllowPrivateNetwork get() = referenceOptions.allowPrivateNetwork

  /** Retrieval options shared by native OpenAPI export and generation. */
  protected fun openApiReferenceOptions(): OpenApiReferenceOptions = referenceOptions.options()

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

  /** Derives patch companions for merge-patch request bodies without requiring a schema annotation. */
  val autoPatchable by option(
    "-auto-patchable",
    help = "Automatically generate patchable types for application/merge-patch+json request bodies",
  ).flag("-no-auto-patchable", default = true, defaultForHelp = "enabled")

  /** Selects metadata for a client or server artifact; omitted to retain all environments. */
  val mode by option("-mode", help = "Project IR metadata for client or server consumption")
    .enum<GenerationMode> { it.name.lowercase() }

  /** Explicit profile retained on projected bindings for downstream generation. */
  val profile by option("-profile", help = "Named profile for environment projection; requires -mode")
    .validate { require(it.isNotBlank()) { "Generation profile must not be blank" } }

  val sourceFiles by argument(
    help = "Source files",
  ).file(mustExist = true, canBeFile = true, canBeDir = false)
    .multiple()

  override fun run() {
    if (profile != null && mode == null) throw UsageError("-profile requires -mode for IR projection")
    if (validateFile != null &&
      mode != null
    ) {
      throw UsageError("-mode projects exports and cannot be used with --validate")
    }
    validateFile?.let { file ->
      val api = GeneratedApiYaml.readPath(file.toPath())
      echo("Valid Sunday IR: ${api.name}")
      return
    }

    if (sourceFiles.isEmpty()) {
      throw UsageError("Missing source file")
    }
    val output = outputFile ?: throw UsageError("Missing required option '-out'")
    GeneratedApiIrExporter(
      GeneratedApiIrOptions(
        deriveServicesFromTags = servicesFromTags,
        autoPatchable = autoPatchable,
        openApiReferences = openApiReferenceOptions(),
        projection = mode?.let { GenerationContext(it, profile) },
      ),
    ).writeYaml(
      sourceFiles.map { sourceFile ->
        sourceFile.toURI()
      },
      output.toPath(),
      sourceKind,
    )
  }
}
