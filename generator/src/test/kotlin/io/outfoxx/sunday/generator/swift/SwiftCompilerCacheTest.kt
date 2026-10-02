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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** Ensures dependency reuse cannot make stale fixture declarations satisfy a later compilation. */
@SwiftTest
class SwiftCompilerCacheTest {

  @Test
  fun `deleted fixture declarations cannot survive through cached compiler outputs`(compiler: SwiftCompiler) {
    compiler.synchronizeCompilation {
      try {
        Files.createDirectories(compiler.srcDir)
        Files.createDirectories(compiler.testsDir)
        val original = compiler.srcDir.resolve("CachedDeclaration.swift")
        Files.writeString(original, "public struct CacheOnlyDeclaration {}")
        val (firstResult, firstOutput) = compiler.compile()
        assertEquals(0, firstResult, firstOutput)

        Files.delete(original)
        Files.writeString(compiler.srcDir.resolve("Consumer.swift"), "public let value = CacheOnlyDeclaration()")
        val (secondResult, secondOutput) = compiler.compile()
        assertNotEquals(0, secondResult, "A deleted declaration was reused: $secondOutput")
      } finally {
        compiler.srcDir.toFile().deleteRecursively()
        compiler.testsDir.toFile().deleteRecursively()
      }
    }
  }
}
