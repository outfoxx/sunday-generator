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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.binaryResponseApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftBinaryResponseTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml"])
  fun `binary responses and unions preserve download bytes`(
    frontend: String,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      binaryResponseApi(frontend),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.copy(
      Path.of("src/test/resources/binary-responses/Check.swift"),
      compiler.testsDir.resolve("BinaryTests.swift"),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
