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
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("requests")
class SwiftParameterDefaultsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `client signatures accept nil for defaulted parameters`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      parameterDefaultsApi(frontend, directory).withOptionalParameterTemplates(),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ParameterDefaultsTests.swift"),
      """
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ParameterDefaultsTests: XCTestCase {
        func testNullableArguments() async throws {
          let baseURL = ParametersAPI<URLSessionTransport>.baseURL(host: nil)
          XCTAssertNil(baseURL.parameters["host"] ?? nil)
          XCTAssertEqual(try baseURL.complete().absoluteString, "https://example.com")
          let transport = URLSessionTransport(baseURL: "https://example.com")
          defer { transport.close() }
          let api = ParametersAPI(transport: transport)
          let omittedOperation = try api.probe(pathValue: nil, queryValue: nil, nullableValue: nil, optionalValue: nil,
                            zeroValue: nil, falseValue: nil, headerValue: nil)
          XCTAssertTrue(omittedOperation.spec.pathParameters?.isEmpty == true)
          let collapsed = try await omittedOperation.transportRequest()
          XCTAssertEqual(collapsed.url?.absoluteString, "https://example.com/probe")
          let emptyPath = try await api.probe(pathValue: "", queryValue: nil, nullableValue: nil, optionalValue: nil,
                            zeroValue: nil, falseValue: nil, headerValue: nil).transportRequest()
          XCTAssertEqual(emptyPath.url?.absoluteString, "https://example.com/probe/")
          let omitted = try await api.probe(pathValue: "explicit", queryValue: nil, nullableValue: nil, optionalValue: nil,
                            zeroValue: nil, falseValue: nil, headerValue: nil).transportRequest()
          XCTAssertEqual(omitted.url?.absoluteString, "https://example.com/probe/explicit")
          XCTAssertNil(omitted.value(forHTTPHeaderField: "headerValue"))
          let defaults = try await api.probe().transportRequest()
          XCTAssertEqual(defaults.url?.path, "/probe/fallback")
          let items = URLComponents(url: try XCTUnwrap(defaults.url), resolvingAgainstBaseURL: false)?.queryItems ?? []
          XCTAssertEqual(items.first { ${'$'}0.name == "queryValue" }?.value, "5")
          XCTAssertEqual(items.first { ${'$'}0.name == "zeroValue" }?.value, "0")
          XCTAssertEqual(items.first { ${'$'}0.name == "falseValue" }?.value, "false")
          XCTAssertEqual(defaults.value(forHTTPHeaderField: "headerValue"), "header")
          let formatted = try api.formatted()
          let formattedValues = try XCTUnwrap(formatted.spec.queryParameters)
          XCTAssertEqual((formattedValues["date"] as? Date)?.timeIntervalSince1970, 1790985600)
          XCTAssertEqual((formattedValues["time"] as? Date)?.timeIntervalSince1970, 45296.125)
          XCTAssertEqual((formattedValues["localDateTime"] as? Date)?.timeIntervalSince1970, 1791030896.125)
          XCTAssertEqual((formattedValues["dateTime"] as? Date)?.timeIntervalSince1970, 1791023696.125)
          _ = try await formatted.transportRequest()
          let absentFormats = try api.formatted(date: nil, time: nil, localDateTime: nil, dateTime: nil, uuid: nil)
          XCTAssertTrue(absentFormats.spec.queryParameters?.isEmpty == true)
          let scalars = try await api.scalarDefaults().transportRequest()
          XCTAssertEqual(scalars.url?.path, "/scalar-defaults/active")
          _ = try api.required(pathValue: "explicit", queryValue: 5)
          let defaultBase = try ParametersAPI<URLSessionTransport>.baseURL().complete()
          XCTAssertEqual(defaultBase.absoluteString, "https://example.com/example.com")
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
