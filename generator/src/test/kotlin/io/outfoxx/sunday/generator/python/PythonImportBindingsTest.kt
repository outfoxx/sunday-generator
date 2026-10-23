package io.outfoxx.sunday.generator.python

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PythonImportBindingsTest : PythonTest() {
  @Test
  fun `aliases retain module boundaries and reserve declaration names independently of order`() {
    val symbols =
      listOf(
        PythonSymbol("a.b_c", "Record"),
        PythonSymbol("a_b.c", "Record"),
        PythonSymbol("a", "RecordPlacement2"),
      )
    val first = PythonImportBindings(symbols)
    val reversed = PythonImportBindings(symbols.reversed())
    val bindings = symbols.map { first.symbol(it.module, it.name) }
    assertEquals(bindings, symbols.map { reversed.symbol(it.module, it.name) })
    assertEquals(3, bindings.map { it.reference }.distinct().size)
    bindings.take(2).forEach {
      assertNotNull(it.alias)
      assertNotEquals("Record", it.reference)
    }
  }
}
