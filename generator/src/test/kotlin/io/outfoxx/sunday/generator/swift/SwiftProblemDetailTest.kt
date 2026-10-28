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
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.narrowedProblemDetailApi
import io.outfoxx.sunday.generator.tools.problemDetailApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@SwiftTest
@Tag("responses")
class SwiftProblemDetailTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `inherited problem encoding respects narrowed detail nullability`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      narrowedProblemDetailApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("ProblemEncodingTests.swift").writeText(
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ProblemEncodingTests: XCTestCase {
        func testEmptyReferencesSurvive() throws {
          let original = try AccountFieldsInvalidProblem(instance: "")
          let value = try JSONDecoder().decode(AccountFieldsInvalidProblem.self, from: JSONEncoder().encode(original))
          XCTAssertEqual(value.instance, "")
          XCTAssertNil(value.instanceURI)
          let encoded = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? [String: Any])
          XCTAssertEqual(encoded["instance"] as? String, "")
        }
        func testNarrowedDetailEncoding() throws {
          for detail: String? in [nil, "", "Invalid field"] {
            let problem = try AccountFieldsInvalidProblem(detail: detail)
            let encoded = try JSONEncoder().encode(problem)
            var object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
            XCTAssertEqual(object.keys.contains("detail"), detail != nil)
            XCTAssertEqual(object["detail"] as? String, detail)
            XCTAssertNil(object["instance"])
            XCTAssertEqual(try JSONDecoder().decode(AccountFieldsInvalidProblem.self, from: encoded).detail, detail)
            object["detail"] = NSNull()
            XCTAssertNil(try JSONDecoder().decode(AccountFieldsInvalidProblem.self,
              from: JSONSerialization.data(withJSONObject: object)).detail)
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val generated =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/AccountFieldsInvalidProblem.swift")
    assertTrue(generated.contains("try container.encodeIfPresent(self.detail, forKey: .detail)"), generated)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `problem details are optional defaults and can be overridden`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      problemDetailApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("ProblemDetailTests.swift").writeText(
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ProblemDetailTests: XCTestCase {
        func testDetailDefaultsAndOverrides() throws {
          XCTAssertEqual(WithDefaultProblem().detail, "Default detail")
          XCTAssertNil(WithoutDefaultProblem().detail)
          XCTAssertEqual(WithDefaultProblem(detail: "Override").detail, "Override")
          XCTAssertEqual(WithoutDefaultProblem(detail: "Override").detail, "Override")
          XCTAssertEqual(WithDefaultProblem(detail: "").detail, "")
          XCTAssertNil(WithDefaultProblem(detail: nil).detail)
          let empty = WithDefaultProblem(instance: "")
          XCTAssertEqual(try JSONDecoder().decode(WithDefaultProblem.self, from: JSONEncoder().encode(empty)).instance, "")
          let encoded = try JSONEncoder().encode(WithDefaultProblem(detail: "Wire detail"))
          XCTAssertEqual(try JSONDecoder().decode(WithDefaultProblem.self, from: encoded).detail, "Wire detail")
        }
        func testAbsentDetailIsOmitted() throws {
          for encoded in [try JSONEncoder().encode(WithoutDefaultProblem()),
                          try JSONEncoder().encode(WithDefaultProblem(detail: nil))] {
            let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
            XCTAssertFalse(object.keys.contains("detail"))
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
