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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("responses")
class SwiftAboutBlankProblemTest {
  @Test
  fun `schema problems register unique wire types alongside annotated problems`(
    @ResourceUri("openapi/ir/schema-problem-registration.yaml") source: URI,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(GeneratedApiIrExporter().export(source), registry, swiftSundayTestOptions)
      .generateServiceTypes()
    assertTrue(compileTypes(compiler, registry.buildTypes()))
    val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "API.swift")
    assertEquals(1, Regex("https://example.com/problems/missing").findAll(service).count(), service)
    assertEquals(1, Regex("https://example.com/problems/annotated").findAll(service).count(), service)
    assertTrue(!service.contains("about:blank"), service)
  }

  @Test
  fun `standard status models retain constraints while HTTP errors use generic problems`(
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      GeneratedApiIrExporter().export(source),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("AboutBlankProblemTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      import PotentJSON
      @testable import SundayGenTest
      final class AboutBlankProblemTests: XCTestCase {
        func testModels() throws {
          let badRequest = try BadRequestProblem(detail: "Details", traceId: "trace")
          let notFound = try NotFoundProblem(detail: "Details", traceId: "trace")
          for (value, status, title) in [(badRequest as any Sunday.Problem, 400, "Bad Request"),
                                        (notFound as any Sunday.Problem, 404, "Not Found")] {
            XCTAssertEqual(value.type, "about:blank")
            XCTAssertEqual(value.status, status)
            XCTAssertEqual(value.title, title)
            let omitted = Data("{\"title\":\"\(title)\",\"status\":\(status)}".utf8)
            let decoded: any Sunday.Problem = status == 400
              ? try Foundation.JSONDecoder().decode(BadRequestProblem.self, from: omitted)
              : try Foundation.JSONDecoder().decode(NotFoundProblem.self, from: omitted)
            XCTAssertEqual(decoded.type, "about:blank")
            var raw = try XCTUnwrap(JSONSerialization.jsonObject(with: Foundation.JSONEncoder().encode(value)) as? [String: Any])
            for (field, invalid) in [("status", 500 as Any), ("title", "Wrong"), ("type", "https://example.com/wrong")] {
              let saved = raw[field]
              raw[field] = invalid
              let data = try JSONSerialization.data(withJSONObject: raw)
              if status == 400 {
                XCTAssertThrowsError(try Foundation.JSONDecoder().decode(BadRequestProblem.self, from: data))
              } else {
                XCTAssertThrowsError(try Foundation.JSONDecoder().decode(NotFoundProblem.self, from: data))
              }
              raw[field] = saved
            }
          }
        }
        func testOptionalBaseAndRequiredCustomType() throws {
          let decoder = Foundation.JSONDecoder()
          let omitted = Data(#"{"title":"Error","status":400}"#.utf8)
          XCTAssertEqual(try decoder.decode(FlexibleProblem.self, from: omitted).type, "about:blank")
          let custom = Data(#"{"type":"https://example.com/problems/custom","title":"Error","status":400}"#.utf8)
          XCTAssertEqual(try decoder.decode(FlexibleProblem.self, from: custom).type,
                         "https://example.com/problems/custom")
          XCTAssertEqual(try decoder.decode(CustomProblem.self, from: custom).type,
                         "https://example.com/problems/custom")
          XCTAssertThrowsError(try decoder.decode(CustomProblem.self, from: omitted))
          XCTAssertEqual(try decoder.decode(RequiredProblem.self, from: omitted).type, "about:blank")
          XCTAssertEqual(try decoder.decode(RequiredProblem.self, from: custom).type,
                         "https://example.com/problems/custom")
          let empty = try decoder.decode(FlexibleProblem.self, from: Data(#"{"type":""}"#.utf8))
          XCTAssertEqual(empty.type, "")
          XCTAssertNil(empty.typeURI)
          let emptyEncoded = try XCTUnwrap(JSONSerialization.jsonObject(with: Foundation.JSONEncoder().encode(empty)) as? [String: Any])
          XCTAssertEqual(emptyEncoded["type"] as? String, "")
          let explicitNull = Data(#"{"type":null,"title":"Error","status":400}"#.utf8)
          XCTAssertEqual(try decoder.decode(FlexibleProblem.self, from: explicitNull).type, "about:blank")
        }
        func testCustomProblemOmitsAbsentDetail() throws {
          for detail: String? in [nil, "", "Details"] {
            let problem = try CustomProblem(title: "Error", status: 400, detail: detail)
            let encoded = try Foundation.JSONEncoder().encode(problem)
            let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
            XCTAssertEqual(object["detail"] as? String, detail)
            XCTAssertEqual(object.keys.contains("detail"), detail != nil)
            XCTAssertEqual(try Foundation.JSONDecoder().decode(CustomProblem.self, from: encoded).detail, detail)
          }
        }
        func testGenericHTTPProblems() async throws {
          for (status, title) in [(400, "Bad Request"), (404, "Not Found"), (503, "Service Unavailable")] {
            for explicit in [false, true] {
              let config = URLSessionConfiguration.ephemeral
              config.protocolClasses = [BlankProblemProtocol.self]
              let session = URLSession(configuration: config)
              let transport = URLSessionTransport(baseURL: URI.Template(format: "https://example.test/\(status)/\(explicit)"),
                session: session, eventSession: session)
              defer { transport.close() }
              let client = API(transport: transport)
              do {
                try await client.getExample().execute()
                XCTFail("Expected generic HTTP problem")
              } catch let problem as Sunday.GenericProblem {
                XCTAssertEqual(problem.type, "about:blank")
                XCTAssertEqual(problem.status, status)
                XCTAssertEqual(problem.title, title)
                XCTAssertEqual(problem.detail, "Details")
                let encoded = try XCTUnwrap(JSONSerialization.jsonObject(with: JSON.Encoder.default.encode(problem)) as? NSDictionary)
                XCTAssertEqual(encoded["traceId"] as? String, "trace")
                XCTAssertEqual(encoded["extra"] as? NSDictionary, ["nested": [1, 2]] as NSDictionary)
              }
            }
          }
        }
      }
      private final class BlankProblemProtocol: URLProtocol, @unchecked Sendable {
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
          let parts = request.url!.pathComponents
          let status = Int(parts[1])!
          let title = status == 400 ? "Bad Request" : status == 404 ? "Not Found" : "Service Unavailable"
          var raw: [String: Any] = ["status": status, "title": title, "detail": "Details", "traceId": "trace",
                                    "extra": ["nested": [1, 2]]]
          if parts[2] == "true" { raw["type"] = "about:blank" }
          let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
            headerFields: ["Content-Type": "application/problem+json"])!
          client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
          client?.urlProtocol(self, didLoad: try! JSONSerialization.data(withJSONObject: raw))
          client?.urlProtocolDidFinishLoading(self)
        }
        override func stopLoading() {}
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
