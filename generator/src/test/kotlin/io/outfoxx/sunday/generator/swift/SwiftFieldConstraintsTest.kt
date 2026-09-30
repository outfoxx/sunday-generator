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
import io.outfoxx.sunday.generator.tools.fieldConstraintsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftFieldConstraintsTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml", "asyncapi", "composed"])
  fun `ordinary constraints validate constructors and decoding`(
    sourceKind: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      fieldConstraintsApi(directory, sourceKind),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("FieldConstraintsTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class FieldConstraintsTests: XCTestCase {
        func testConstraints() throws {
          let value = try Probe(name: "valid", tags: ["a"], count: 5, id: "ID-ABC")
          let data = try JSONEncoder().encode(value)
          let decoded = try JSONDecoder().decode(Probe.self, from: data)
          XCTAssertEqual(decoded.name, "valid")
          XCTAssertEqual(decoded.tags, ["a"])
          XCTAssertEqual(decoded.count, 5)
          XCTAssertEqual(decoded.id, "ID-ABC")
          XCTAssertThrowsError(try value.withName(name: "BAD"))
          XCTAssertThrowsError(try value.withId(id: "invalid"))
          _ = try Probe(name: "valid")
          _ = try JSONDecoder().decode(Probe.self, from: Data(#"{"name":"valid"}"#.utf8))
          for invalid in ["a", "abcdef", "BAD"] {
            XCTAssertThrowsError(try Probe(name: invalid))
          }
          for invalid in [[], ["a", "b", "c"]] {
            XCTAssertThrowsError(try Probe(name: "valid", tags: invalid))
          }
          for invalid in [-1, 11] { XCTAssertThrowsError(try Probe(name: "valid", count: invalid)) }
          XCTAssertThrowsError(try Probe(name: "valid", id: "invalid"))
          let invalid: [(String, Any)] = [
            ("name", "a"), ("name", "abcdef"), ("name", "BAD"),
            ("tags", [String]()), ("tags", ["a", "b", "c"]),
            ("count", -1), ("count", 11), ("id", "invalid"),
            ("tags", NSNull()), ("count", NSNull()), ("id", NSNull())
          ]
          for (field, bad) in invalid {
            let payload: [String: Any] = ["name": "valid"].merging([field: bad]) { _, new in new }
            let json = try JSONSerialization.data(withJSONObject: payload)
            XCTAssertThrowsError(try JSONDecoder().decode(Probe.self, from: json)) { error in
              guard case DecodingError.dataCorrupted(let context) = error else {
                return XCTFail("Unexpected error: \(error)")
              }
              XCTAssertTrue(context.debugDescription.contains(field))
            }
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
