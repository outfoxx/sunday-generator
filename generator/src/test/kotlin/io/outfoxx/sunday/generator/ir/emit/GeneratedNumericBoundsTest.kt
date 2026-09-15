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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedNumericBoundsTest {
  @Test
  fun `multiples require positive finite divisors`() {
    for (literal in listOf("0", "-2", "NaN", "Infinity", "bad")) {
      val error =
        assertThrows(GenerationException::class.java) {
          GeneratedNumericBounds.multipleOf(mapOf("multipleOf" to literal), "Child.count")
        }
      assertTrue(error.message.orEmpty().contains("Child.count"))
    }
    assertTrue(GeneratedNumericBounds.multipleOf(mapOf("multipleOf" to "0.25"), "Child.count") == "0.25".toBigDecimal())
  }

  @Test
  fun `boolean modifiers and numeric assertions enforce the same intervals`() {
    for (exclusive in listOf("false", "true", "1")) {
      val bounds = GeneratedNumericBounds.parse(mapOf("minimum" to "1", "exclusiveMinimum" to exclusive), "Count.value")
      assertTrue(bounds.all { it.accepts("2".toBigDecimal()) })
      assertFalse(bounds.all { it.accepts("0".toBigDecimal()) })
      assertTrue(bounds.all { it.accepts("1".toBigDecimal()) } == (exclusive == "false"))
    }
    for (exclusive in listOf("false", "true", "3")) {
      val bounds = GeneratedNumericBounds.parse(mapOf("maximum" to "3", "exclusiveMaximum" to exclusive), "Count.value")
      assertTrue(bounds.all { it.accepts("2".toBigDecimal()) })
      assertFalse(bounds.all { it.accepts("4".toBigDecimal()) })
      assertTrue(bounds.all { it.accepts("3".toBigDecimal()) } == (exclusive == "false"))
    }
    assertTrue(
      GeneratedNumericBounds
        .parse(
          mapOf("exclusiveMinimum" to "false", "exclusiveMaximum" to "false"),
          "Count.value",
        ).isEmpty(),
    )
  }

  @Test
  fun `invalid modifiers diagnose their property`() {
    for (key in listOf("exclusiveMinimum", "exclusiveMaximum")) {
      for (value in listOf("true", "no", "NaN", "Infinity")) {
        val error =
          assertThrows(GenerationException::class.java) {
            GeneratedNumericBounds.parse(mapOf(key to value), "Count.value")
          }
        assertTrue(error.message.orEmpty().contains("Count.value"))
        assertTrue(error.message.orEmpty().contains(key))
      }
    }
  }
}
