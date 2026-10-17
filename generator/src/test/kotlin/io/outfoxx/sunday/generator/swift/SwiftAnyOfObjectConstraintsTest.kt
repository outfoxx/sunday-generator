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
import io.outfoxx.sunday.generator.tools.anyOfObjectConstraintsApi
import io.outfoxx.sunday.generator.tools.anyOfObjectInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfObjectValidWires
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

/** Compile-backed runtime coverage for inclusive object constraints from issue #277. */
@SwiftTest
@Tag("models")
@Tag("validation")
class SwiftAnyOfObjectConstraintsTest {
  @ParameterizedTest
  @ValueSource(strings = ["shared", "inline", "named", "shared-composed"])
  fun `object anyOf preserves inclusive requirements and round trips`(
    shape: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {

    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      anyOfObjectConstraintsApi(shape, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("AnyOfTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class AnyOfTests: XCTestCase {
        func testAlternatives() throws {
          let decoder = JSONDecoder()
          decoder.dateDecodingStrategy = .iso8601
          let encoder = JSONEncoder()
          encoder.dateEncodingStrategy = .iso8601
          let inputs = try JSONSerialization.jsonObject(with: Data(#"$anyOfObjectValidWires"#.utf8)) as! [NSDictionary]
          for raw in inputs {
            let value = try decoder.decode(CheckoutRequest.self, from: JSONSerialization.data(withJSONObject: raw))
            let encoded = try JSONSerialization.jsonObject(with: encoder.encode(value)) as! NSDictionary
            XCTAssertEqual(encoded, raw)
          }
          let invalid = try JSONSerialization.jsonObject(with: Data(#"$anyOfObjectInvalidWires"#.utf8)) as! [Any]
          for raw in invalid {
            XCTAssertThrowsError(try decoder.decode(CheckoutRequest.self, from: JSONSerialization.data(withJSONObject: raw, options: .fragmentsAllowed)))
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
