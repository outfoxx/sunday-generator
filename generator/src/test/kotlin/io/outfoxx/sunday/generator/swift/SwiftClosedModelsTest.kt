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
import io.outfoxx.sunday.generator.tools.closedModelsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftClosedModelsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `closed objects reject unknown fields`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      closedModelsApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ClosedModelsTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ClosedModelsTests: XCTestCase {
        func check<T: Decodable>(_ type: T.Type, _ fields: String) throws {
          let decoder = JSONDecoder()
          _ = try decoder.decode(type, from: Data("{\(fields)}".utf8))
          for extra in ["1", "null", "{}", "[]"] {
            let wire = "{" + (fields.isEmpty ? "" : fields + ",") + "\"extra\":" + extra + "}"
            XCTAssertThrowsError(try decoder.decode(type, from: Data(wire.utf8))) { error in
              guard case DecodingError.dataCorrupted(let context) = error else {
                return XCTFail("Unexpected error: \(error)")
              }
              XCTAssertEqual(context.codingPath.last?.stringValue, "extra")
            }
          }
        }
        func testClosedModels() throws {
          _ = try JSONDecoder().decode(ClosedRecord.self,
            from: Data(#"{"display-name":"valid","choice":{"kind":"cat","name":"cat"}}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(ClosedRecord.self,
            from: Data(#"{"display-name":"valid","choice":{"kind":"cat","name":"cat","extra":1}}"#.utf8)))
          try check(ClosedRecord.self, #""display-name":"valid""#)
          try check(ClosedChild.self, #""name":"valid","count":1"#)
          try check(EmptyClosed.self, "")
          ${if (frontend == "composed") "try check(EventRecord.self, \"\")" else ""}
          _ = try JSONDecoder().decode(OpenRecord.self, from: Data(#"{"name":"valid","extra":1}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(ClosedRecord.self,
            from: Data(#"{"display-name":"valid","empty":{"extra":1}}"#.utf8)))
          XCTAssertThrowsError(try JSONDecoder().decode(ClosedRecord.self,
            from: Data(#"{"display-name":"valid","nested":{"display-name":"nested","extra":1}}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
