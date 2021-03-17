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

import amf.client.model.document.Document
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import kotlin.system.exitProcess

abstract class CommonGenerateCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

  val mediaTypes
    by option(
    "-media-type",
    help = "Specify to control order of initialized media types"
  ).multiple().unique()

  val outputCategories
    by option(
    "-category",
    help = "Add category of type to output ${GeneratedTypeCategory.values()}"
  ).enum<GeneratedTypeCategory>()
    .multiple(GeneratedTypeCategory.values().toList())

  val outputDirectory
    by option(
    "-out",
    help = "Output directory"
  ).file(mustExist = true, canBeFile = false, canBeDir = true)
    .required()

  val files
    by argument(
    help = "RAML source files"
  ).file(mustExist = true, canBeFile = true, canBeDir = false)
    .multiple(required = true)

  abstract val typeRegistry: TypeRegistry
  abstract fun generatorFactory(document: Document): Generator

  override fun run() {

    println("Generating ${this.outputCategories} types")

    files.forEach { file ->

      println("Processing $file")

      val processed = process(file.toURI())

      processed.entries.forEach {
        val out = if (it.level == ProcessResult.Level.Error) System.err else System.out
        out.println("${it.level.toString().toLowerCase()}| ${it.file}:${it.line}: ${it.message}")
      }

      if (!processed.isValid) {
        exitProcess(1)
      }

      val generator = generatorFactory(processed.document)

      try {
        generator.generateServiceTypes()
      } catch (x: GenerationException) {
        System.err.println("${x.file}:${x.line}: ${x.message}")
      }
    }

    typeRegistry.generateFiles(outputCategories.toSet(), outputDirectory.toPath())
  }
}
