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

package io.outfoxx.sunday.generator.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import io.outfoxx.sunday.test.extensions.TempDir as CompilerTempDir

class TempDirCleanupTest {
  @Test
  fun `compiler cleanup preserves local runtime symlink targets`(
    @TempDir runtime: Path,
  ) {
    val sentinel = runtime.resolve("source.txt")
    sentinel.writeText("runtime source")
    CompilerTempDir().use { compiler ->
      Files.createSymbolicLink(compiler.path.resolve("local-runtime"), runtime)
    }
    assertEquals("runtime source", sentinel.readText())
  }
}
