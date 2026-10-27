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
import io.outfoxx.sunday.generator.tools.rfc9457ProblemOneOfCases
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
@Tag("validation")
class SwiftProblemOneOfTest {
  @Test
  fun `problem oneOf accepts exactly one valid alternative`(
    @ResourceUri("openapi/ir/problem-oneof.yaml") source: URI,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      GeneratedApiIrExporter().export(source),
      registry,
      SwiftSundayOptions("https://example.org/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("ProblemOneOfTests.swift").writeText(
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ProblemOneOfTests: XCTestCase {
        func testAlternatives() throws {
          let cases = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(#"$rfc9457ProblemOneOfCases"#.utf8)) as? [[String: Any]])
          for test in cases {
            let union = try XCTUnwrap(test["union"] as? String)
            let raw = try XCTUnwrap(test["wire"])
            let data = try JSONSerialization.data(withJSONObject: raw)
            let expected = test["expected"] as? String
            switch union {
            case "Problems":
              try check(Problems.self, data, expected) { value in
                switch value {
                case .collectionMissingProblem: return "CollectionMissingProblem"
                case .itemMissingProblem: return "ItemMissingProblem"
                }
              }
            case "ReversedProblems":
              try check(ReversedProblems.self, data, expected) { value in
                switch value {
                case .collectionMissingProblem: return "CollectionMissingProblem"
                case .itemMissingProblem: return "ItemMissingProblem"
                }
              }
            case "TaggedProblems":
              try check(TaggedProblems.self, data, expected) { value in
                switch value {
                case .collectionMissingProblem: return "CollectionMissingProblem"
                case .itemMissingProblem: return "ItemMissingProblem"
                }
              }
            case "AmbiguousProblems":
              try check(AmbiguousProblems.self, data, expected) { _ in "ambiguous" }
            case "ConstrainedProblems":
              try check(ConstrainedProblems.self, data, expected) { value in
                switch value {
                case .shortDetailProblem: return "ShortDetailProblem"
                case .longDetailProblem: return "LongDetailProblem"
                }
              }
            default: XCTFail("Unknown test union: \(union)")
            }
          }
        }
        private func check<T: Codable>(_ type: T.Type, _ data: Data, _ expected: String?, _ kind: (T) -> String) throws {
          if let expected {
            let value = try JSONDecoder().decode(type, from: data)
            XCTAssertEqual(kind(value), expected)
            let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            for member in ["type", "title", "status", "detail", "instance"] {
              var missing = object; missing.removeValue(forKey: member)
              func outcome(_ raw: [String: Any]) -> NSDictionary? {
                guard let data = try? JSONSerialization.data(withJSONObject: raw),
                      let value = try? JSONDecoder().decode(type, from: data),
                      let encoded = try? JSONEncoder().encode(value) else { return nil }
                return (try? JSONSerialization.jsonObject(with: encoded)) as? NSDictionary
              }
              let wrongValues: [Any] = member == "status" ? [NSNull(), true, "404", [], [:]] : [NSNull(), true, 42, [], [:]]
              for wrong in wrongValues {
                var raw = missing; raw[member] = wrong
                XCTAssertEqual(outcome(raw), outcome(missing), "\(member)=\(wrong)")
              }
            }

            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? NSDictionary,
              try JSONSerialization.jsonObject(with: data) as? NSDictionary)
          } else {
            XCTAssertThrowsError(try JSONDecoder().decode(type, from: data), String(decoding: data, as: UTF8.self))
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
