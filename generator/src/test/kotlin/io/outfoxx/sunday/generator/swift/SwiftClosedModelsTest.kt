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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.closedModelsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
@Tag("validation")
class SwiftClosedModelsTest {
  @ParameterizedTest
  @CsvSource(
    "raml,Schema",
    "raml,On",
    "raml,Off",
    "openapi,Schema",
    "openapi,On",
    "openapi,Off",
    "asyncapi,Schema",
    "asyncapi,On",
    "asyncapi,Off",
    "composed,Schema",
    "composed,On",
    "composed,Off",
    "openapi-unevaluated,Schema",
    "openapi-unevaluated,On",
    "openapi-unevaluated,Off",
    "asyncapi-unevaluated,Schema",
    "asyncapi-unevaluated,On",
    "asyncapi-unevaluated,Off",
    "composed-unevaluated,Schema",
    "composed-unevaluated,On",
    "composed-unevaluated,Off",
  )
  fun `closed objects reject unknown fields`(
    frontend: String,
    mode: UnknownFieldPreservation,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val preserve = mode == UnknownFieldPreservation.On
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      closedModelsApi(frontend, directory),
      registry,
      SwiftSundayOptions(
        "http://example.com/",
        listOf("application/json"),
        "API",
        preserveUnknownFields = mode,
      ),
    ).generateServiceTypes()
    registry.generateFiles(
      GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
      compiler.srcDir,
    )
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
          XCTAssertFalse(Mirror(reflecting: EmptyClosed()).children.contains { $0.label == "additionalProperties" })
          let open = try JSONDecoder().decode(OpenRecord.self, from: Data(#"{"name":"known","future":1}"#.utf8))
          let encodedOpen = try JSONSerialization.jsonObject(with: JSONEncoder().encode(open)) as! NSDictionary
          XCTAssertEqual(encodedOpen["future"] != nil, $preserve)
          XCTAssertEqual(Mirror(reflecting: open).children.contains { $0.label == "additionalProperties" }, $preserve)
          for data in [
            try JSONEncoder().encode(JSONDecoder().decode(ExplicitRecord.self, from: Data(#"{"future":1}"#.utf8))),
            try JSONEncoder().encode(JSONDecoder().decode(ExplicitChild.self, from: Data(#"{"future":1}"#.utf8))),
            try JSONEncoder().encode(JSONDecoder().decode(ExplicitLeaf.self, from: Data(#"{"future":1}"#.utf8)))
          ] {
            XCTAssertEqual((try JSONSerialization.jsonObject(with: data) as! NSDictionary)["future"] != nil, ${mode != UnknownFieldPreservation.Off})
          }
          ${if (frontend.startsWith("composed")) "try check(EventRecord.self, \"\")" else ""}
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
