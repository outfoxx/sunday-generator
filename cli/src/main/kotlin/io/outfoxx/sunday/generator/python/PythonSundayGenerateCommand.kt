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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode

/** CLI command that generates transport-neutral Python Sunday clients from supported source specs. */
open class PythonSundayGenerateCommand :
  PythonGenerateCommand(name = "python/sunday", help = "Generate Python client for Sunday framework") {

  override val generationMode: GenerationMode = GenerationMode.Client

  override fun run() {
    println("Generating ${this.outputCategories} types")
    println("Processing ${files.joinToString()}")

    val export = exportApi()
    val modules =
      PythonSundayIrGenerator(export.api, pythonOptions(export))
        .generateModules(outputCategories.toSet())

    val obsoleteModulePaths =
      if (GeneratedTypeCategory.Service in outputCategories) {
        listOf(modules.first().path.substringBeforeLast('/') + "/runtime.py")
      } else {
        emptyList()
      }
    PythonModuleWriter().writeModules(modules, outputDirectory.toPath(), obsoleteModulePaths)
  }
}
