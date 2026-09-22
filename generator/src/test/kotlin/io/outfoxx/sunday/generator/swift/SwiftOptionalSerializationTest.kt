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
import io.outfoxx.sunday.generator.tools.optionalSerializationApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftOptionalSerializationTest {
  @Test
  fun `optional fields serialize according to presence and nullability`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(optionalSerializationApi(directory), registry, swiftSundayTestOptions).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("OptionalSerializationTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class OptionalSerializationTests: XCTestCase {
        func testSerialization() throws {
          for wire in [
            #"{"nullableEntries":null,"nullableLookup":null}"#,
            #"{"entries":[null],"lookup":{"key":null},"aliasedEntries":[],"aliasedLookup":{},"nullableEntries":null,"nullableLookup":null}"#
          ] {
            let data = Data(wire.utf8)
            let collection = try JSONDecoder().decode(CollectionRequest.self, from: data)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(collection)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: data) as! NSDictionary)
          }
          let alias = AliasRequest(nullableAlias: nil, anyValue: nil)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(alias)) as! NSDictionary,
                         ["nullableAlias": NSNull(), "anyValue": NSNull()] as NSDictionary)
          let value = Request(name: "test", requiredNullable: nil)
          let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as! NSDictionary
          XCTAssertEqual(json, ["name": "test", "requiredNullable": NSNull(), "optionalNullable": NSNull()] as NSDictionary)
          let decoder = JSONDecoder()
          for wire in [
            #"{"name":"test","requiredNullable":null,"optionalNullable":null,"text":"","number":0,"flag":false,"items":[]}"#,
            #"{"name":"test","requiredNullable":"yes","optionalNullable":"yes","text":"main","number":1,"flag":true,"items":["item"]}"#
          ] {
            let data = Data(wire.utf8)
            let decoded = try decoder.decode(Request.self, from: data)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: data) as! NSDictionary)
          }
          XCTAssertThrowsError(try decoder.decode(Request.self, from: Data("{}".utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
