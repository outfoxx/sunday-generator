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

package io.outfoxx.sunday.generator.gradle.tests

import io.outfoxx.sunday.generator.gradle.GeneratedOutputOwnership
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import java.io.File
import java.nio.file.Files

/** Ownership checks never adopt unrelated files, even inside an already generated directory. */
class GeneratedOutputOwnershipTest {
  @TempDir
  lateinit var directory: File

  @ParameterizedTest
  @ValueSource(strings = ["added", "modified", "wrong-owner", "malformed-manifest"])
  fun `unowned or modified content is rejected without changing files`(change: String) {
    val output = directory.resolve("output").also { it.mkdirs() }
    val file = output.resolve("data.txt").also { it.writeText("generated fixture") }
    GeneratedOutputOwnership.record(output, ":generate")
    when (change) {
      "added" -> output.resolve("handwritten.txt").writeText("handwritten fixture")
      "modified" -> file.writeText("handwritten replacement")
      "malformed-manifest" -> output.resolve(".sunday-generated-output.json").writeText("invalid JSON")
    }
    val before = contents(output)
    expectThrows<IllegalArgumentException> {
      GeneratedOutputOwnership.validate(output, if (change == "wrong-owner") ":another" else ":generate")
    }
    expectThat(contents(output)).isEqualTo(before)
  }

  @Test
  fun `ownership survives relocation and safely tolerates missing generated files`() {
    val original = directory.resolve("original").also { it.mkdirs() }
    original.resolve("data.txt").writeText("generated fixture")
    GeneratedOutputOwnership.record(original, ":generate")
    val relocated = directory.resolve("relocated")
    original.copyRecursively(relocated)
    GeneratedOutputOwnership.validate(relocated, ":generate")
    expectThat(contents(relocated)).isEqualTo(contents(original))
    expectThat(relocated.resolve("data.txt").delete()).isEqualTo(true)
    GeneratedOutputOwnership.validate(relocated, ":generate")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @EnabledOnOs(OS.MAC, OS.LINUX)
  fun `symbolic links cannot give ownership of external files`(root: Boolean) {
    val output = directory.resolve("output").also { it.mkdirs() }
    GeneratedOutputOwnership.record(output, ":generate")
    val external = directory.resolve("external").also { it.mkdirs() }
    external.resolve("handwritten.txt").writeText("preserve this")
    val link = if (root) directory.resolve("alias") else output.resolve("linked")
    Files.createSymbolicLink(link.toPath(), external.toPath())
    expectThrows<IllegalArgumentException> {
      GeneratedOutputOwnership.validate(if (root) link else output, ":generate")
    }
    expectThat(external.resolve("handwritten.txt").readText()).isEqualTo("preserve this")
  }

  private fun contents(directory: File): Map<String, String> =
    directory.walkTopDown().filter { it.isFile }.associate { it.relativeTo(directory).path to it.readText() }
}
