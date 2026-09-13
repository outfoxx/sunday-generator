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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.testing.test
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiReferenceOptionsTest {
  private class GenerateCommand : CommonGenerateCommand("test", "Test command") {
    val references get() = openApiReferenceOptions()

    override fun run() {}
  }

  @Test
  fun `export and generation share defaults and explicit retrieval options`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText("openapi: 3.1.0\ninfo: {title: Empty, version: 1.0.0}\npaths: {}")
    for (explicit in listOf(false, true)) {
      val flags =
        if (explicit) {
          arrayOf(
            "--openapi-offline",
            "--openapi-allow-private-network",
            "--openapi-reference-cache-dir",
            directory.resolve("new-cache").toString(),
          )
        } else {
          emptyArray()
        }
      val generate = GenerateCommand()
      val generated = generate.test(arrayOf(*flags, "-out", directory.toString(), source.toString()))
      assertEquals(0, generated.statusCode, generated.output)
      val export = IrCommand()
      val exported =
        export.test(
          arrayOf(*flags, "-out", directory.resolve("api.ir.yaml").toString(), source.toString()),
        )
      assertEquals(0, exported.statusCode, exported.output)
      assertEquals(explicit, generate.openApiOffline)
      assertEquals(generate.openApiOffline, export.openApiOffline)
      assertEquals(explicit, generate.openApiAllowPrivateNetwork)
      assertEquals(generate.openApiAllowPrivateNetwork, export.openApiAllowPrivateNetwork)
      assertEquals(generate.openApiReferenceCacheDirectory, export.openApiReferenceCacheDirectory)
      assertEquals(
        OpenApiReferenceOptions(
          cacheDirectory = if (explicit) directory.resolve("new-cache") else OpenApiReferenceOptions().cacheDirectory,
          offline = explicit,
          allowPrivateNetwork = explicit,
        ),
        generate.references,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "ir",
      "kotlin/sunday",
      "kotlin/jaxrs",
      "swift/sunday",
      "typescript/sunday",
      "python/sunday",
      "python/litestar",
    ],
  )
  fun `commands register shared flags once and validate cache directories`(
    command: String,
    @TempDir directory: Path,
  ) {
    val help = sundayCommand().test(arrayOf(command, "--help"))
    assertEquals(0, help.statusCode, help.output)
    for (flag in listOf("--openapi-offline", "--openapi-reference-cache-dir", "--openapi-allow-private-network")) {
      assertEquals(1, Regex(Regex.escape(flag)).findAll(help.output).count(), help.output)
    }
    val file = directory.resolve("file")
    file.writeText("not a directory")
    val invalid = sundayCommand().test(arrayOf(command, "--openapi-reference-cache-dir", file.toString()))
    assertNotEquals(0, invalid.statusCode)
    assertTrue(invalid.output.contains("--openapi-reference-cache-dir"), invalid.output)
    assertTrue(invalid.output.contains("directory", ignoreCase = true), invalid.output)
  }
}
