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

package io.outfoxx.sunday.generator.kotlin.tools

import com.tschuchort.compiletesting.JvmCompilationResult
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame

@OptIn(ExperimentalCompilerApi::class)
internal fun assertTolerantEnumCollectionParity(compilation: JvmCompilationResult) {
  compilation.classLoader.use { classLoader ->
    val enumClass = classLoader.loadClass("io.test.TaskState")
    val fromValue = enumClass.getMethod("fromValue", String::class.java)
    val pending = fromValue.invoke(null, "pending")
    val samePending = fromValue.invoke(null, "pending")
    val unknown = fromValue.invoke(null, "future")
    val sameUnknown = fromValue.invoke(null, "future")
    val otherUnknown = fromValue.invoke(null, "other")

    assertSame(pending, samePending)
    assertEquals(unknown, sameUnknown)
    assertEquals(unknown.hashCode(), sameUnknown.hashCode())
    assertNotEquals(unknown, otherUnknown)
    assertEquals(3, hashSetOf(pending, unknown, sameUnknown, otherUnknown).size)
    assertEquals("found", hashMapOf(unknown to "found")[sameUnknown])
  }
}
