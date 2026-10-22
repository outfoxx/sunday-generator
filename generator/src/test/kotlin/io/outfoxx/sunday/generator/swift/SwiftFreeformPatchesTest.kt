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
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files

@SwiftTest
@Tag("models")
class SwiftFreeformPatchesTest {
  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `freeform patch set values retain objects and preserve operation states`(
    preserve: Boolean,
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/freeform-patches.yaml") uri: URI,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    val api = GeneratedApiIrExporter().export(uri)
    val options =
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API", preserveUnknownFields = preserve)
    SwiftSundayIrGenerator(api, registry, options).generateServiceTypes()
    registry.generateFiles(
      GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
      compiler.srcDir,
    )
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("FreeformPatchTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      import PotentCodables
      @testable import SundayGenTest
      final class FreeformPatchTests: XCTestCase {
        func testRoundTrip() throws {
          let value: [String: AnyValue] = ["nested": ["enabled": true], "items": [["null": .nil], 1, true], "nullValue": .nil]
          let patch = DocumentPatch(properties: .set(value), named: .set(value), items: .set([value]))
          let expected = #"{"properties":{"nested":{"enabled":true},"items":[{"null":null},1,true],"nullValue":null},"named":{"nested":{"enabled":true},"items":[{"null":null},1,true],"nullValue":null},"items":[{"nested":{"enabled":true},"items":[{"null":null},1,true],"nullValue":null}]}"#
          let encoded = try JSONEncoder().encode(patch)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoded) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: Data(expected.utf8)) as! NSDictionary)
          for input in [expected, #"{}"#, #"{"properties":null,"items":null}"#] {
            let decoded = try JSONDecoder().decode(DocumentPatch.self, from: Data(input.utf8))
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: Data(input.utf8)) as! NSDictionary)
          }
          let empty = try JSONDecoder().decode(DocumentPatch.self, from: Data("{}".utf8))
          XCTAssertEqual(empty.properties, .unchanged)
          XCTAssertEqual(empty.named, .unchanged)
          let deleted = DocumentPatch(properties: .delete, items: .delete)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(deleted)) as! NSDictionary,
                         ["properties": NSNull(), "items": NSNull()] as NSDictionary)
          XCTAssertThrowsError(try JSONDecoder().decode(DocumentPatch.self, from: Data(#"{"named":null}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
