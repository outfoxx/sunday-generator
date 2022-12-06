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

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.common.ShapeIndex
import java.net.URI
import kotlin.system.exitProcess

abstract class CommonGenerateCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

  val serviceSuffix by option(
    "-service-suffix",
    help = "Suffix for generated services",
  ).default("API")

  val mediaTypes by option(
    "-media-type",
    help = "Specify order of default media types",
  ).multiple()
    .unique()

  val outputCategories by option(
    "-category",
    help = "Add category of type to output ${GeneratedTypeCategory.values().joinToString { it.name }}",
  ).enum<GeneratedTypeCategory>()
    .multiple(GeneratedTypeCategory.values().toList())
    .unique()

  val outputDirectory by option(
    "-out",
    help = "Output directory",
  ).file(mustExist = true, canBeFile = false, canBeDir = true)
    .required()

  val problemBaseUri by option(
    "-problem-base",
    help = "Default problem base URI",
  ).default("http://example.com/")
    .validate { URI(it) }

  val files by argument(
    help = "RAML source files",
  ).file(mustExist = true, canBeFile = true, canBeDir = false)
    .multiple(required = true)

  abstract val typeRegistry: TypeRegistry
  abstract fun generatorFactory(document: Document, shapeIndex: ShapeIndex): Generator

  override fun run() {
    println("Generating ${this.outputCategories} types")

    val apiProcessor = APIProcessor()

    files.forEach { file ->

      println("Processing $file")

      val processed = apiProcessor.process(file.toURI())

      processed.validationLog.forEach {
        val out = if (it.level == APIProcessor.Result.Level.Error) System.err else System.out
        out.println("${it.level.toString().lowercase()}| ${it.file}:${it.line}: ${it.message}")
      }

      if (!processed.isValid) {
        exitProcess(1)
      }

      val generator = generatorFactory(processed.document, processed.shapeIndex)

      try {
        generator.generateServiceTypes()
      } catch (x: GenerationException) {
        System.err.println("${x.file}:${x.line}: ${x.message}")
      }
    }

    typeRegistry.generateFiles(outputCategories.toSet(), outputDirectory.toPath())
  }
}
