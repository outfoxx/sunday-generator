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

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.GeneratedTypeReferenceRewriter
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex

/** Resolved Python import identity for a generated model. */
internal fun GeneratedModel.pythonModule(defaultPackage: String): String {
  val target = targets["python"]
  return "${target?.modelPackageName ?: defaultPackage}.${target?.modelModuleName ?: "models"}"
}

/** Source-bound references needed when splitting model definitions between Python modules. */
internal fun GeneratedModel.placementReferences(): Set<GeneratedTypeRef> =
  buildSet {
    GeneratedTypeReferenceRewriter(source, { reference, _ ->
      if (reference.kind == GeneratedTypeRef.Kind.NAMED) add(reference)
      reference
    }).model(copy(placementDeclarations = emptyList(), nested = null))
  }

/** Local names used after assigning unambiguous Python import aliases. */
internal fun GeneratedModel.placementDependencies(): Set<String> = placementReferences().map { it.name }.toSet()

/** Emits model groups using the full model graph for validation and reference lookup. */
internal fun renderPlacedModels(
  models: List<GeneratedModel>,
  selected: Set<GeneratedModel>,
  packageName: String,
  options: PythonGeneratorOptions,
): List<PythonModule> {
  val index = GeneratedModelIndex(models)
  val groups = selected.groupBy { it.pythonModule(packageName) }
  val graph =
    models.groupBy { it.pythonModule(packageName) }.mapValues { (module, declarations) ->
      declarations
        .flatMap { it.placementReferences() }
        .mapNotNull(index::modelOrNull)
        .map { it.pythonModule(packageName) }
        .filter { it != module }
        .toSet()
    }

  fun visit(
    module: String,
    active: Set<String>,
    done: MutableSet<String>,
  ) {
    require(module !in active) { "Cyclic Python module placement involving '$module'" }
    if (!done.add(module)) return
    graph[module].orEmpty().forEach { visit(it, active + module, done) }
  }
  val done = mutableSetOf<String>()
  graph.keys.forEach { visit(it, emptySet(), done) }
  return groups.map { (module, declarations) ->
    PythonModelRenderer(packageName, options.preserveUnknownFields, options.defaultTolerance, module)
      .renderModels(models) { it in declarations }
  }
}

/** Resolved service module, with the normal generated filename as its fallback. */
internal fun GeneratedService.pythonModule(
  defaultPackage: String,
  server: Boolean = false,
): String {
  val target = targets["python"]
  val module = target?.moduleName ?: if (server) pythonServiceServerModuleName else pythonServiceModuleName
  return "${target?.packageName ?: defaultPackage}.$module"
}

/** Completes package directories and rejects colliding generated modules. */
internal fun List<PythonModule>.withPackageInitializers(): List<PythonModule> {
  val grouped = groupBy { it.path }
  val modules =
    grouped
      .mapValues { (path, candidates) ->
        val distinct = candidates.distinctBy { it.source }
        if (distinct.size == 1) {
          distinct.first()
        } else {
          val content =
            distinct.map {
              requireNotNull(
                it.content,
              ) { "Conflicting generated Python modules at '$path'" }
            }
          PythonModuleBuilder(path, content.first().relativeImportBase)
            .apply {
              content.forEach(::addContent)
            }.build()
        }
      }.toMutableMap()
  for (path in grouped.keys) {
    val segments = path.split('/').dropLast(1)
    for (index in segments.indices) {
      val packagePath = segments.take(index + 1).joinToString("/")
      require("$packagePath.py" !in grouped) {
        "Conflicting generated Python module '$packagePath.py' and package required by '$path'"
      }
      val init = "$packagePath/__init__.py"
      modules.putIfAbsent(init, PythonModuleBuilder(init).build())
    }
  }
  return modules.values.toList()
}

/** Uses package-relative imports when the service remains in the consumer's package. */
internal fun GeneratedService.pythonImportModule(
  defaultPackage: String,
  server: Boolean = false,
): String =
  pythonModule(defaultPackage, server).let { module ->
    if (module.startsWith("$defaultPackage.")) module.removePrefix(defaultPackage) else module
  }

/** Resolved module for an explicitly generated problem and its payload companion. */
internal fun GeneratedProblem.pythonModule(defaultPackage: String): String {
  val target = targets["python"]
  return "${target?.modelPackageName ?: defaultPackage}.${target?.modelModuleName ?: "problems"}"
}

/** Groups problem declarations by model placement while preserving their output category. */
internal fun renderPlacedProblems(
  problems: List<GeneratedProblem>,
  packageName: String,
): List<PythonModule> =
  if (problems.isEmpty()) {
    listOf(PythonProblemRenderer(packageName).renderProblems(problems))
  } else {
    problems.groupBy { it.pythonModule(packageName) }.map { (module, declarations) ->
      PythonProblemRenderer(packageName, module).renderProblems(declarations)
    }
  }
