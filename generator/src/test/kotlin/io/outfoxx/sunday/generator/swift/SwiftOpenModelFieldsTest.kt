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
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.openModelWire
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftOpenModelFieldsTest {
  @ParameterizedTest
  @CsvSource(
    "raml,true",
    "openapi,true",
    "asyncapi,true",
    "composed,true",
    "raml,false",
    "openapi,false",
    "asyncapi,false",
    "composed,false",
  )
  fun `open model fields round trip by default and can be discarded`(
    frontend: String,
    preserve: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    val options =
      if (preserve) {
        swiftSundayTestOptions
      } else {
        SwiftSundayOptions(
          "http://example.com/",
          listOf("application/json"),
          "API",
          preserveUnknownFields = false,
        )
      }
    SwiftSundayIrGenerator(openModelFieldsApi(frontend, directory), registry, options).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("OpenFieldsTests.swift"),
      """
      import Foundation
      import XCTest
      import PotentCodables
      @testable import SundayGenTest
      final class OpenFieldsTests: XCTestCase {
        func testPreservation() throws {
          let input = #"$openModelWire"#
          let model = try JSONDecoder().decode(Notice.self, from: Data(input.utf8))
          let result = try JSONSerialization.jsonObject(with: JSONEncoder().encode(model)) as! NSDictionary
          let data = result["data"] as! NSDictionary
          XCTAssertEqual(data["additionalProperties"] as? String, "declared")
          if $preserve {
            XCTAssertEqual(result, try JSONSerialization.jsonObject(with: Data(input.utf8)) as! NSDictionary)
          } else {
            XCTAssertNil(result["future"])
            XCTAssertNil(data["future"])
            XCTAssertNil(data["nested"])
          }
          let inherited = try JSONDecoder().decode(ExtendedRecord.self, from: Data(#"{"id":"one","kind":"extended","future":null}"#.utf8))
          let inheritedResult = try JSONSerialization.jsonObject(with: JSONEncoder().encode(inherited)) as! NSDictionary
          XCTAssertEqual(inheritedResult["future"] != nil, $preserve)
          XCTAssertThrowsError(try JSONDecoder().decode(ClosedChild.self, from: Data(#"{"id":"one","name":"name","future":1}"#.utf8)))
          XCTAssertThrowsError(try JSONDecoder().decode(TypedRecord.self, from: Data(#"{"id":"one","future":"wrong"}"#.utf8)))
          ${if (preserve) {
        """
          let created = NoticeData(name: "before", additionalProperties: "declared", additionalProperties_: ["future": .nil, "nested": ["x": [1, ["y": true]]]])
          let copied = created.withName(name: "after")
          XCTAssertEqual(copied.additionalProperties_, created.additionalProperties_)
          let copiedJSON = try JSONSerialization.jsonObject(with: JSONEncoder().encode(copied)) as! NSDictionary
          XCTAssertEqual(copiedJSON["name"] as? String, "after")
          XCTAssertTrue(copiedJSON["nested"] is NSDictionary)
          XCTAssertThrowsError(try JSONEncoder().encode(ClosedRecord(id: "one", additionalProperties_: ["future": 1])))
          XCTAssertThrowsError(try JSONEncoder().encode(TypedRecord(id: "one", additionalProperties_: ["future": "wrong"])))
          """
      } else {
        ""
      }}
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
