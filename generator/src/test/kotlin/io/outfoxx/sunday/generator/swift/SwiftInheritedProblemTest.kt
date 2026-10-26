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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.tools.TEAM_NOT_FOUND_JSON
import io.outfoxx.sunday.generator.tools.USER_NOT_FOUND_JSON
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("responses")
class SwiftInheritedProblemTest {
  @Test
  fun `inherited problem fields survive construction decoding and encoding`(
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") source: URI,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      GeneratedApiIrExporter().export(listOf(source)),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(
      GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()),
      compiler.srcDir,
    )
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("InheritedProblemTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class InheritedProblemTests: XCTestCase {
        func testHTTPProblem() async throws {
          let config = URLSessionConfiguration.ephemeral
          config.protocolClasses = [ProblemProtocol.self]
          let session = URLSession(configuration: config)
          let transport = URLSessionTransport(baseURL: "https://example.test", session: session, eventSession: session)
          defer { transport.close() }
          let client = API(transport: transport)
          do {
            try await client.readResource().execute()
            XCTFail("Expected an HTTP problem")
          } catch let problem as QueuedProblem {
            XCTAssertEqual(problem.resourceKey, "A")
            XCTAssertEqual(problem.queuePosition, 2)
            let expected = try JSONSerialization.jsonObject(with: Data(#"$INHERITED_PROBLEM_JSON"#.utf8)) as? NSDictionary
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(problem)) as? NSDictionary, expected)
          }
        }
        func testMissingProblems() async throws {
          let config = URLSessionConfiguration.ephemeral
          config.protocolClasses = [ProblemProtocol.self]
          let session = URLSession(configuration: config)
          let transport = URLSessionTransport(baseURL: "https://example.test", session: session, eventSession: session)
          defer { transport.close() }
          let client = API(transport: transport)
          let team = try TeamNotFoundProblem()
          let user = try UserNotFoundProblem()
          let overridden = try TeamNotFoundProblem(detail: "Override", resourceId: "123")
          XCTAssertEqual(overridden.detail, "Override")
          XCTAssertEqual(overridden.resourceId, "123")
          for (kind, raw, value) in [("team", #"$TEAM_NOT_FOUND_JSON"#, team as any NotFoundProblem),
                                      ("user", #"$USER_NOT_FOUND_JSON"#, user as any NotFoundProblem)] {
            XCTAssertEqual(value.resourceId, "unknown")
            XCTAssertEqual(value.errorCode, "missing")
            let data = Data(raw.utf8)
            let expected = try JSONSerialization.jsonObject(with: data) as? NSDictionary
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? NSDictionary, expected)
            let decoded = try JSONDecoder().decode(NotFoundProblems.self, from: data)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? NSDictionary, expected)
            for (field, invalid) in [("status", 400 as Any), ("error-code", "wrong"), ("resourceId", "missing")] {
              var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
              if invalid as? String == "missing" { object.removeValue(forKey: field) } else { object[field] = invalid }
              XCTAssertThrowsError(try JSONDecoder().decode(NotFoundProblems.self, from: JSONSerialization.data(withJSONObject: object)))
            }
            for request in [try client.readMissing(kind: kind), try client.readMissingNamed(kind: kind)] {
              do {
                try await request.execute()
                XCTFail("Expected 404 problem")
              } catch let problem as any NotFoundProblem {
                XCTAssertTrue(kind == "team" ? problem is TeamNotFoundProblem : problem is UserNotFoundProblem)
                XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(problem)) as? NSDictionary, expected)
              }
            }
          }
        }
        func testWireParity() throws {
          let raw = Data(#"$INHERITED_PROBLEM_JSON"#.utf8)
          let expected = try XCTUnwrap(JSONSerialization.jsonObject(with: raw) as? NSDictionary)
          let value = try QueuedProblem(detail: "Resource A is rebuilding", traceId: "trace-A",
            resourceKey: "A", nullableChild: nil, metadata: Metadata(region: "west"), queuePosition: 2)
          let baseDefaults = try DefaultedProblem()
          XCTAssertEqual(baseDefaults.detail, "Something bad happened")
          XCTAssertEqual(baseDefaults.enabled, false)
          XCTAssertEqual(baseDefaults.count, 0)
          XCTAssertEqual(baseDefaults.note, "fallback")
          XCTAssertEqual(baseDefaults.location, URL(string: "https://example.com/default"))
          XCTAssertEqual(baseDefaults.revision, 1)
          XCTAssertNil(try DefaultedProblem(enabled: true, count: 3, note: nil).note)
          let defaulted = try QueuedProblem(nullableChild: nil, metadata: Metadata(region: "west"))
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(defaulted)) as? NSDictionary, expected)
          let overridden = try QueuedProblem(detail: "Override", traceId: "trace-B", resourceKey: "B",
            nullableChild: nil, metadata: Metadata(region: "east"), queuePosition: 3)
          XCTAssertEqual(overridden.detail, "Override")
          XCTAssertEqual(overridden.traceId, "trace-B")
          XCTAssertEqual(overridden.resourceKey, "B")
          XCTAssertEqual(overridden.queuePosition, 3)
          let problem: any Sunday.Problem = value
          XCTAssertEqual(problem.status, 409)
          let inherited: any RebuildingProblem = value
          XCTAssertEqual(inherited.resourceKey, "A")
          for model in [value, try JSONDecoder().decode(QueuedProblem.self, from: raw)] {
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(model)) as? NSDictionary, expected)
          }
          for (field, invalid) in [("resourceKey", "" as Any), ("retryAfterSeconds", 2),
            ("queuePosition", 0), ("type", "https://example.com/wrong"), ("nullableChild", "missing"), ("queuePosition", "missing"), ("resourceKey", "missing"), ("detail", NSNull())] {
            var object = try XCTUnwrap(JSONSerialization.jsonObject(with: raw) as? [String: Any])
            if invalid as? String == "missing" { object.removeValue(forKey: field) } else { object[field] = invalid }
            XCTAssertThrowsError(try JSONDecoder().decode(QueuedProblem.self, from: JSONSerialization.data(withJSONObject: object)))
          }
          XCTAssertThrowsError(try QueuedProblem(resourceKey: "", nullableChild: nil,
            metadata: Metadata(region: "west"), queuePosition: 2))
          XCTAssertThrowsError(try QueuedProblem(resourceKey: "A", nullableChild: nil,
            metadata: Metadata(region: "west"), queuePosition: 0))
        }
      }
      private final class ProblemProtocol: URLProtocol, @unchecked Sendable {
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
          let missing = request.url!.path.hasPrefix("/missing")
          let raw = missing ? (request.url!.path.hasSuffix("/team") ? #"$TEAM_NOT_FOUND_JSON"# : #"$USER_NOT_FOUND_JSON"#)
            : #"$INHERITED_PROBLEM_JSON"#
          let response = HTTPURLResponse(url: request.url!, statusCode: missing ? 404 : 409, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/problem+json"])!
          client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
          client?.urlProtocol(self, didLoad: Data(raw.utf8))
          client?.urlProtocolDidFinishLoading(self)
        }
        override func stopLoading() {}
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
