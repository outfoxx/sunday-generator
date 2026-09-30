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
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftPatternModelsTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      patternModelsApi(directory, composed),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("PatternTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class PatternTests: XCTestCase {
        func testPatterns() throws {
          let valid = [${patternModelValid.joinToString { "#\"$it\"#" }}]
          let invalid = [${patternModelInvalid.joinToString { "#\"$it\"#" }}]
          for wire in valid { _ = try JSONDecoder().decode(PatternRecord.self, from: Data(wire.utf8)) }
          for wire in invalid {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(wire.utf8)), wire)
          }
          ${patternModelRegressions.entries.joinToString("\n") { (name, values) ->
        """
        for wire in [${values.first.joinToString { "#\"$it\"#" }}] {
          _ = try JSONDecoder().decode($name.self, from: Data(wire.utf8))
        }
        for wire in [${values.second.joinToString { "#\"$it\"#" }}] {
          XCTAssertThrowsError(try JSONDecoder().decode($name.self, from: Data(wire.utf8)), wire)
        }
        """.trimIndent()
      }}
          _ = try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"x-valid":"ok"}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"x-invalid":"a"}"#.utf8)))
          XCTAssertThrowsError(try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"extra":1}"#.utf8)))
          _ = try JSONDecoder().decode(OpenPattern.self, from: Data(#"{"extra":1,"x-valid":"ok"}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(OpenPattern.self, from: Data(#"{"extra":"wrong"}"#.utf8)))
          _ = try JSONDecoder().decode(PatternOnly.self, from: Data(#"{"x-valid":"ok"}"#.utf8))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
