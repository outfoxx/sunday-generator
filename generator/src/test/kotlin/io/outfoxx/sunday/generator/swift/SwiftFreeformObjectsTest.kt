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
class SwiftFreeformObjectsTest {
  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `inline and named freeform objects round trip`(
    preserve: Boolean,
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/freeform-objects.yaml") uri: URI,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    val api = GeneratedApiIrExporter().export(uri)
    val options =
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API", preserveUnknownFields = preserve)
    SwiftSundayIrGenerator(api, registry, options).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("FreeformTests.swift"),
      """
      import Foundation
      import XCTest
      import PotentCodables
      @testable import SundayGenTest
      final class FreeformTests: XCTestCase {
        func testRoundTrip() throws {
          let value: [String: Any] = ["name": "example", "enabled": true, "count": 3, "fraction": 1.5, "nullValue": NSNull(), "nested": ["value": NSNull()], "items": [1, "two", NSNull()]]
          for field in ["properties", "named", "items"] {
            let original: [String: Any] = [field: field == "items" ? [value, [:]] : value]
            let input = try JSONSerialization.data(withJSONObject: original)
            let model = try JSONDecoder().decode(Document.self, from: input)
            let _: [String: AnyValue?]? = model.properties
            let _: [String: AnyValue?]? = model.named
            let result = try JSONSerialization.jsonObject(with: JSONEncoder().encode(model)) as! NSDictionary
            XCTAssertEqual(result, original as NSDictionary)
            for invalid: Any in ["{}", true, 3] {
              let input = try JSONSerialization.data(withJSONObject: [field: field == "items" ? [invalid] : invalid])
              XCTAssertThrowsError(try JSONDecoder().decode(Document.self, from: input))
            }
          }
          for input in [#"{}"#, #"{"properties":{},"named":{},"items":[]}"#] {
            let model = try JSONDecoder().decode(Document.self, from: Data(input.utf8))
            let _: [String: AnyValue?]? = model.properties
            let _: [String: AnyValue?]? = model.named
            let result = try JSONSerialization.jsonObject(with: JSONEncoder().encode(model)) as! NSDictionary
            XCTAssertEqual(result, try JSONSerialization.jsonObject(with: Data(input.utf8)) as! NSDictionary)
          }
          for field in ["properties", "named", "items"] {
            let input = try JSONSerialization.data(withJSONObject: [field: NSNull()])
            XCTAssertThrowsError(try JSONDecoder().decode(Document.self, from: input))
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
