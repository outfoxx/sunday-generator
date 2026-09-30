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
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.tools.nominalScalarApi
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftNominalScalarTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `nominal scalars validate and preserve union identity`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      nominalScalarApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("NominalTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class NominalTests: XCTestCase {
        func testValues() throws {
          let encoder = JSONEncoder()
          let decoder = JSONDecoder()
          let fact = try BaseFactSid("sid:f:abc")
          XCTAssertEqual(fact.rawValue, "sid:f:abc")
          XCTAssertNil(BaseFactSid(rawValue: "invalid"))
          XCTAssertThrowsError(try BaseFactSid("invalid"))
          XCTAssertThrowsError(try PositiveCount(0))
          XCTAssertThrowsError(try Ratio(2))
          XCTAssertEqual(try decoder.decode(Defaults.self, from: Data("{}".utf8)).fact?.rawValue, ${if (frontend == "raml") "nil" else "\"sid:f:default\""})
          for (wire, isFact) in [(#""sid:f:abc""#, true), (#""sid:l:abc""#, false)] {
            let value = try decoder.decode(AnySid.self, from: Data(wire.utf8))
            switch value {
              case .baseFactSid: XCTAssertTrue(isFact)
              case .baseLossSid: XCTAssertFalse(isFact)
            }
            XCTAssertEqual(String(data: try encoder.encode(value), encoding: .utf8), wire)
          }
          XCTAssertThrowsError(try decoder.decode(AnySid.self, from: Data(#""invalid""#.utf8)))
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try decoder.decode(AmbiguousSid.self, from: Data(#""sid:f:abc""#.utf8)))
          let wire = #"{"fact":"sid:f:abc","identifiers":["sid:l:abc"],"count":2,"ratio":0.5,"enabled":true}"#
          let record = try decoder.decode(Record.self, from: Data(wire.utf8))
          XCTAssertEqual(record.fact, fact)
          XCTAssertEqual(record.enabled.rawValue, true)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(record)) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: Data(wire.utf8)) as! NSDictionary)
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    if (frontend == "openapi") {
      registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
      Files.writeString(
        compiler.srcDir.resolve("InvalidAssignments.swift"),
        """
        func plain(_ value: String) -> BaseFactSid { value }
        func other(_ value: BaseLossSid) -> BaseFactSid { value }
        """.trimIndent(),
      )
      val error = assertThrows(AssertionFailedError::class.java) { compileGeneratedFiles(compiler) }
      assertTrue(error.message.orEmpty().contains("cannot convert return expression"), error.message)
    }
  }
}
