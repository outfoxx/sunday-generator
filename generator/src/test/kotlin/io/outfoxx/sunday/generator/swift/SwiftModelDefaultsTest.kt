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
import io.outfoxx.sunday.generator.tools.modelDefaultsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
class SwiftModelDefaultsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `unset constructor defaults are omitted while decoding applies them`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      modelDefaultsApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ModelDefaultsTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ModelDefaultsTests: XCTestCase {
        func testDefaults() throws {
          let encoder = JSONEncoder()
          let decoder = JSONDecoder()
          XCTAssertNil(DefaultRecord(name: "test").executionMode)
          XCTAssertNil(DefaultChild(name: "test").executionMode)
          for data in [try encoder.encode(DefaultRecord(name: "test")), try encoder.encode(DefaultChild(name: "test"))] {
            XCTAssertEqual(try JSONSerialization.jsonObject(with: data) as! NSDictionary,
                           ["name": "test"] as NSDictionary)
          }
          let supplied = DefaultRecord(name: "test", executionMode: "fast", count: 3, enabled: true, choice: .fast)
          let wire = try JSONSerialization.jsonObject(with: encoder.encode(supplied)) as! NSDictionary
          XCTAssertEqual(wire["execution-mode"] as? String, "fast")
          XCTAssertEqual(wire["count"] as? Int, 3)
          XCTAssertEqual(wire["enabled"] as? Bool, true)
          XCTAssertEqual(wire["choice"] as? String, "fast")
          let data = Data(#"{"name":"test"}"#.utf8)
          let decoded = try decoder.decode(DefaultRecord.self, from: data)
          XCTAssertEqual(decoded.executionMode, ${if (frontend == "raml") "nil" else "\"fast\""})
          XCTAssertEqual(decoded.count, ${if (frontend == "raml") "nil" else "3"})
          XCTAssertEqual(decoded.enabled, ${if (frontend == "raml") "nil" else "true"})
          XCTAssertEqual(decoded.choice, ${if (frontend == "raml") "nil" else ".fast"})
          let child = try decoder.decode(DefaultChild.self, from: data)
          XCTAssertEqual(child.executionMode, ${if (frontend == "raml") "nil" else "\"fast\""})
          XCTAssertEqual(child.local, ${if (frontend == "raml") "nil" else "\"child\""})
          XCTAssertThrowsError(try decoder.decode(DefaultRecord.self, from: Data("{}".utf8)))
          XCTAssertThrowsError(try decoder.decode(DefaultRecord.self, from: Data(#"{"name":"test","execution-mode":null}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
