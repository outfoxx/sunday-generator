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
import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.parameterNameCollisionApi
import io.outfoxx.sunday.generator.tools.parameterToleranceApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("requests")
@Tag("validation")
class SwiftParameterToleranceTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `validation helper names do not shadow operation parameters`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      parameterNameCollisionApi(frontend, directory, listOf("mode", "context", "valid", "key", "parameter")),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("CollisionTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class CollisionTests: XCTestCase {
        func testRequestParameters() async throws {
          let transport = URLSessionTransport(baseURL: URI.Template(format: "https://example.com"))
          defer { transport.close() }
          let api = ParametersAPI(transport: transport)
          _ = try await api.parameters().transportRequest()
          _ = try await api.parameters(mode: .active, context: .active, valid: [.active], key: ["state": .active], parameter: .active).transportRequest()
          for operation in [
            try api.parameters(mode: .unknown("future")),
            try api.parameters(context: .unknown("future")),
            try api.parameters(valid: [.unknown("future")]),
            try api.parameters(key: ["state": .unknown("future")]),
            try api.parameters(parameter: .unknown("future")),
          ] {
            do {
              _ = try await operation.transportRequest()
              XCTFail("Shadowed request parameter accepted")
            } catch SundayError.requestEncodingFailed(reason: .parameterValidationFailed(let error)) {
              XCTAssertTrue(error is ModelValidationError)
            }
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed", "openapi-all"])
  fun `typed parameters validate on bodyless requests`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val tolerance = if (frontend.endsWith("-all")) Tolerance.All else Tolerance.Response
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      parameterToleranceApi(frontend.removeSuffix("-all"), directory),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API", defaultTolerance = tolerance),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ParameterTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ParameterTests: XCTestCase {
        func testRequestParameters() async throws {
          let transport = URLSessionTransport(baseURL: URI.Template(format: "https://example.com"))
          defer { transport.close() }
          let api = ParametersAPI(transport: transport)
          let valid = try api.parameters(pathState: .active, openState: .unknown("future"))
          _ = try await valid.transportRequest()
          ${if (tolerance == Tolerance.All) "_ = try await api.parameters(pathState: .active, defaultState: .unknown(\"future\")).transportRequest()" else ""}
          for operation in [
            try api.parameters(pathState: .unknown("future")),
            try api.parameters(pathState: .active, queryStates: [.active, .unknown("future")]),
            try api.parameters(pathState: .active, headerState: .unknown("future")),
            ${if (tolerance == Tolerance.Response) "try api.parameters(pathState: .active, defaultState: .unknown(\"future\"))," else ""}
          ] {
            do {
              _ = try await operation.transportRequest()
              XCTFail("Unknown request parameter accepted")
            } catch SundayError.requestEncodingFailed(reason: .parameterValidationFailed(let error)) {
              XCTAssertTrue(error is ModelValidationError)
            }
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
