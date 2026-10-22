package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.GeneratedTypeReferenceRewriter
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex

/** Keeps independently placed declarations distinguishable inside one Python module. */
internal class PythonDeclarationBindings(
  declarations: List<GeneratedModel>,
  private val packageName: String,
  private val module: String,
) {
  private val index = GeneratedModelIndex(declarations)
  private val names = linkedMapOf<GeneratedModel, String>()
  private val used = declarations.map { it.name }.toMutableSet()

  init {
    declarations.groupBy { it.name }.forEach { (name, candidates) ->
      val local = candidates.filter { it.pythonModule(packageName) == module }
      require(local.size <= 1) {
        "Python declaration collision '$module.$name': ${local.map { it.declarationUri ?: it.source.location }}"
      }
      val ordered = candidates.sortedWith(compareBy({ it !in local }, { it.pythonModule(packageName) }))
      ordered.forEachIndexed { index, declaration ->
        names[declaration] =
          if (index == 0) {
            name
          } else {
            var suffix = index + 1
            var alias = "${name}Placement$suffix"
            while (!used.add(alias)) alias = "${name}Placement${++suffix}"
            alias
          }
      }
    }
  }

  /** Rewrites local aliases without changing the exported identity in another module. */
  fun model(model: GeneratedModel): GeneratedModel =
    GeneratedTypeReferenceRewriter(model.source, { reference, _ ->
      if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
        reference.copy(name = index.modelOrNull(reference)?.let(names::getValue) ?: reference.name)
      } else {
        reference
      }
    }).model(model.copy(name = names.getValue(model), placementDeclarations = emptyList()))

  /** Import for a rewritten dependency name. */
  fun symbol(name: String): PythonSymbol? =
    names.entries.firstOrNull { it.value == name }?.let { (declaration, alias) ->
      PythonSymbol(declaration.pythonModule(packageName), declaration.name.pythonTypeName, alias.pythonTypeName)
    }
}
