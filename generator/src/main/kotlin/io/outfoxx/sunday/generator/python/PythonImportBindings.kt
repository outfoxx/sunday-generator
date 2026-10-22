package io.outfoxx.sunday.generator.python

/** Allocates distinct local bindings without flattening qualified declaration identities. */
internal class PythonImportBindings(
  symbols: List<PythonSymbol>,
) {
  private val bindings: Map<PythonSymbol, PythonSymbol>

  init {
    val used = symbols.map { it.name }.toMutableSet()
    bindings =
      symbols
        .distinct()
        .groupBy { it.name }
        .flatMap { (name, candidates) ->
          candidates.sortedBy { it.module }.mapIndexed { index, symbol ->
            val alias =
              if (candidates.size == 1) {
                null
              } else {
                // Any declaration can share the service's module, where its original name must
                // remain available. Alias every imported candidate rather than shadowing it.
                var suffix = index + 1
                var candidate = "${name}Placement$suffix"
                while (!used.add(candidate)) candidate = "${name}Placement${++suffix}"
                candidate
              }
            symbol to symbol.copy(alias = alias)
          }
        }.toMap()
  }

  /** Returns the stable import binding for a generated declaration. */
  fun symbol(
    module: String,
    name: String,
  ): PythonSymbol = PythonSymbol(module, name).let { bindings[it] ?: it }
}
