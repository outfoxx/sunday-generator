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
import io.outfoxx.sunday.generator.tools.constantDiscriminatorsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
class SwiftConstantDiscriminatorsTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `constant tags are constructed implicitly and decoded strictly`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      constantDiscriminatorsApi(directory, frontend),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("ConstantTests.swift").writeText(
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ConstantTests: XCTestCase {
        func testConstant() throws {
          let constants = try OnlyConstants()
          let constantData = try JSONEncoder().encode(constants)
          let constantObject = try XCTUnwrap(JSONSerialization.jsonObject(with: constantData) as? [String: Any])
          XCTAssertEqual(constantObject["tag-value"] as? String, "fixed")
          XCTAssertEqual(constantObject["zero"] as? Int, 0)
          XCTAssertEqual(constantObject["flag"] as? Bool, false)
          XCTAssertEqual(constantObject["optional"] as? String, "fixed")
          let decodedConstants = try JSONDecoder().decode(OnlyConstants.self, from: Data(#"{"tag-value":"fixed","zero":0,"flag":false}"#.utf8))
          XCTAssertEqual(decodedConstants.optional, "fixed")
          let value = try BeatExtent()
          let encoded = try JSONEncoder().encode(value)
          let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
          XCTAssertEqual(object["kind"] as? String, "beat")
          XCTAssertEqual(try JSONDecoder().decode(BeatExtent.self, from: Data(#"{"kind":"beat"}"#.utf8)).kind, "beat")
          XCTAssertThrowsError(try JSONDecoder().decode(BeatExtent.self, from: Data(#"{"kind":"x"}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
