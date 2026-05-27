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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class GeneratedCodeSnapshotInvariantTest {

  private val testRoot = Path.of("src", "test", "kotlin", "io", "outfoxx", "sunday", "generator")

  @Test
  fun `generated code snapshot helpers require compiled sources`() {
    val commonSnapshotAssertions =
      testRoot
        .resolve("tools")
        .resolve("SnapshotAssertions.kt")
        .readText()
    val typeScriptSnapshotAssertions =
      testRoot
        .resolve("typescript")
        .resolve("tools")
        .resolve("SnapshotAssertions.kt")
        .readText()

    assertTrue(
      commonSnapshotAssertions.contains(
        "CompiledGeneratedSources.requireCompiled(GeneratedCodeLanguage.Kotlin, actual)",
      ),
      "Kotlin snapshots must be guarded by the compiled-source registry",
    )
    assertTrue(
      commonSnapshotAssertions.contains(
        "CompiledGeneratedSources.requireCompiled(GeneratedCodeLanguage.Swift, actual)",
      ),
      "Swift snapshots must be guarded by the compiled-source registry",
    )
    assertTrue(
      commonSnapshotAssertions.contains(
        "CompiledGeneratedSources.requireCompiled(GeneratedCodeLanguage.Python, actual)",
      ),
      "Python snapshots must be guarded by the compiled-source registry",
    )
    assertTrue(
      typeScriptSnapshotAssertions.contains(
        "CompiledGeneratedSources.requireCompiled(GeneratedCodeLanguage.TypeScript, actual)",
      ),
      "TypeScript snapshots must be guarded by the compiled-source registry",
    )
  }

  @Test
  fun `language compilers record generated sources only after successful compile`() {
    mapOf(
      "kotlin" to "tools/TypeCompiler.kt",
      "python" to "tools/TypeCompiler.kt",
      "swift" to "tools/TypeCompiler.kt",
      "typescript" to "tools/TypeCompiler.kt",
    ).forEach { (language, compilerPath) ->
      val source = testRoot.resolve(language).resolve(compilerPath).readText()

      assertTrue(
        source.contains("CompiledGeneratedSources.beginCompile()"),
        "$language compiler tests must clear the compiled-source registry before compiling generated code",
      )
      assertTrue(
        source.contains("CompiledGeneratedSources.record"),
        "$language compiler tests must record generated code after compile succeeds",
      )
    }
  }

  @Test
  fun `target generator test annotations clear compiled sources before each test`() {
    listOf(
      "kotlin/KotlinTest.kt",
      "python/PythonTest.kt",
      "swift/SwiftTest.kt",
      "typescript/TypeScriptTest.kt",
    ).forEach { annotationPath ->
      val source = testRoot.resolve(annotationPath).readText()

      assertTrue(
        source.contains("CompiledGeneratedSourcesExtension::class"),
        "$annotationPath must clear compiled sources before each test",
      )
    }
  }

  @Test
  fun `target generator tests do not bypass generated code snapshot helpers`() {
    val bypassingFiles =
      Files
        .walk(testRoot)
        .asSequence()
        .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
        .filterNot { it.relativeTo(testRoot).startsWith(Path.of("tools")) }
        .filterNot { it.relativeTo(testRoot).startsWith(Path.of("typescript", "tools")) }
        .filter { it.readText().contains("assertSnapshotAt(") }
        .map { it.relativeTo(testRoot).toString() }
        .toList()

    assertFalse(
      bypassingFiles.isNotEmpty(),
      "Generated-code tests must use language snapshot helpers so compiled-source checks run: $bypassingFiles",
    )
  }
}
