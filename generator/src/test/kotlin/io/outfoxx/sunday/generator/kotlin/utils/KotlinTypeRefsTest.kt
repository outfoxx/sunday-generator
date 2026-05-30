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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KotlinTypeRefsTest {

  @Test
  fun `maps integer scalar formats to Kotlin width types`() {
    assertEquals(BYTE, GeneratedTypeRef.scalar("integer", format = "int8").kotlinIntegerScalarTypeName())
    assertEquals(SHORT, GeneratedTypeRef.scalar("integer", format = "int16").kotlinIntegerScalarTypeName())
    assertEquals(INT, GeneratedTypeRef.scalar("integer", format = "int").kotlinIntegerScalarTypeName())
    assertEquals(INT, GeneratedTypeRef.scalar("integer", format = "int32").kotlinIntegerScalarTypeName())
    assertEquals(LONG, GeneratedTypeRef.scalar("integer", format = "long").kotlinIntegerScalarTypeName())
    assertEquals(LONG, GeneratedTypeRef.scalar("integer", format = "int64").kotlinIntegerScalarTypeName())
  }

  @Test
  fun `returns null for non width integer formats`() {
    assertNull(GeneratedTypeRef.scalar("integer").kotlinIntegerScalarTypeName())
    assertNull(GeneratedTypeRef.scalar("integer", format = "").kotlinIntegerScalarTypeName())
    assertNull(GeneratedTypeRef.scalar("integer", format = "decimal").kotlinIntegerScalarTypeName())
  }

  @Test
  fun `returns null for non integer scalars`() {
    assertNull(GeneratedTypeRef.scalar("string", format = "int64").kotlinIntegerScalarTypeName())
    assertNull(GeneratedTypeRef.scalar("long").kotlinIntegerScalarTypeName())
  }
}
