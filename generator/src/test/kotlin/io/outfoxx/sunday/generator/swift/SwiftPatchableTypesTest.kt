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
import io.outfoxx.sunday.generator.tools.patchableApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
@Tag("validation")
@Tag("requests")
class SwiftPatchableTypesTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference"])
  fun `PATCH models preserve presence and enforce deletion permissions`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      patchableApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("PatchTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class PatchTests: XCTestCase {
        func testPresence() throws {
          let decoder = JSONDecoder()
          let encoder = JSONEncoder()
          for json in [#"{}"#, #"{"title":"new title"}"#, #"{"description":null}"#, #"{"title":null,"display-name":null,"optional-alias":null}"#,
                       #"{"required-nullable":"new","required-alias":"new"}"#,
                       #"{"description":"text","count":2,"state":"active","display-name":"Name"}"#] {
            let data = Data(json.utf8)
            let patch = try decoder.decode(SomeRequestPatch.self, from: data)
            XCTAssertTrue(patch.isValid(.request))
            XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: data) as! NSDictionary)
          }
          let empty = try SomeRequestPatch()
          XCTAssertEqual(empty.title, .unchanged)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(empty)) as! NSDictionary, [:])
          for json in [#"{"required-nullable":null}"#, #"{"title":"x"}"#, #"{"count":0}"#,
                       #"{"count":null}"#, #"{"required-alias":null}"#] {
            XCTAssertThrowsError(try decoder.decode(SomeRequestPatch.self, from: Data(json.utf8)), json)
          }
          XCTAssertThrowsError(try decoder.decode(SomeRequest.self, from: Data("{}".utf8)))
          XCTAssertEqual(try decoder.decode(SomeRequest.self, from: Data(#"{"count":2,"required-nullable":null,"required-alias":null}"#.utf8)).title, ${if (frontend
          .startsWith(
            "raml",
          )
      ) {
        "nil"
      } else {
        "\"initial\""
      }})
          var patch = try SomeRequestPatch(title: .set("valid"))
          XCTAssertTrue(patch.isValid(.request))
          patch.title = .set("x")
          XCTAssertFalse(patch.isValid(.request))
          patch.title = .delete
          XCTAssertTrue(patch.isValid(.request))
          patch.title = .unchanged
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as! NSDictionary, [:])
          XCTAssertTrue(try SomeRequestPatch(requiredNullable: .set("valid")).isValid(.request))
          let unknown = try decoder.decode(SomeRequestPatch.self, from: Data(#"{"state":"future"}"#.utf8))
          XCTAssertTrue(unknown.isValid(.response))
          XCTAssertFalse(unknown.isValid(.request))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
