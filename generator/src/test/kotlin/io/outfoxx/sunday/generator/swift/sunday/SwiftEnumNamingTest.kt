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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.enumNamingApi
import io.outfoxx.sunday.generator.tools.enumNamingUpperCamelNames
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files

@SwiftTest
@Tag("models")
class SwiftEnumNamingTest : SwiftSundayIrTestSupport() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `maps enum words and preserves strict and tolerant wire values`(
    source: String,
    compiler: SwiftCompiler,
  ) {
    val api = enumNamingApi(source)
    generateSwiftSundayFiles(compiler, api)
    val checks =
      api.models
        .single { it.name == "JobKind" }
        .values
        .zip(
          enumNamingUpperCamelNames,
        ).joinToString("\n") { (wire, name) ->
          val member = name.replaceFirstChar { it.lowercase() }
          val tolerantMember = if (name == "UnknownValue") "$member(\"$wire\")" else member
          """
          XCTAssertEqual(JobKind.$member.rawValue, "$wire")
          XCTAssertEqual(TolerantKind.$tolerantMember.rawValue, "$wire")
          XCTAssertEqual(try JSONDecoder().decode(JobKind.self, from: Data("\"$wire\"".utf8)), .$member)
          XCTAssertEqual(try JSONEncoder().encode(TolerantKind.$tolerantMember), Data("\"$wire\"".utf8))
          """.trimIndent()
        }
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("EnumNamingTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class EnumNamingTests: XCTestCase {
        func testWireValues() throws {
          $checks
          let unknown = try JSONDecoder().decode(TolerantKind.self, from: Data("\"future-value\"".utf8))
          XCTAssertEqual(unknown.rawValue, "future-value")
          XCTAssertEqual(try JSONEncoder().encode(unknown), Data("\"future-value\"".utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(JobKind.self, from: Data("\"future-value\"".utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `rejects acronym names that normalize to the same member`() {
    val original = enumNamingApi("openapi")
    val model = original.models.single { it.name == "JobKind" }.copy(values = listOf("HTTPServer", "http_server"))
    val api = original.copy(models = listOf(model))
    val error =
      assertThrows(GenerationException::class.java) {
        SwiftSundayIrGenerator(api, SwiftTypeRegistry(setOf()), swiftSundayTestOptions).generateServiceTypes()
      }
    assertTrue(error.message!!.contains("'httpServer' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }
}
