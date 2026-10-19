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
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftCustomTypeMappingTest {
  @Test
  fun `custom Swift scalars decode encode and validate`(compiler: SwiftCompiler) {
    val api =
      OpenApiToGeneratedApi().convert(
        Path.of("src/test/resources/openapi/ir/custom-types/mapped-api.yaml").toUri(),
      )
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("DomainIdentifier.swift"),
      """
      import Foundation
      public struct DomainIdentifier: Codable, Sendable {
        public let value: String
        public init(from decoder: Decoder) throws { value = try decoder.singleValueContainer().decode(String.self) }
        public func encode(to encoder: Encoder) throws {
          var container = encoder.singleValueContainer()
          try container.encode(value)
        }
      }
      """.trimIndent(),
    )
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("MappingTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class MappingTests: XCTestCase {
        func testMapping() throws {
          let record = try JSONDecoder().decode(Record.self, from: Data(#"{"id":"id:abc"}"#.utf8))
          let id: DomainIdentifier = record.id
          XCTAssertEqual(id.value, "id:abc")
          XCTAssertTrue(record.isValid(.request))
          let again = try JSONDecoder().decode(Record.self, from: JSONEncoder().encode(record))
          XCTAssertEqual(again.id.value, "id:abc")
          XCTAssertThrowsError(try JSONDecoder().decode(Record.self, from: Data(#"{"id":"invalid"}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `Kotlin mapping metadata leaves Swift output unchanged`(compiler: SwiftCompiler) {
    val api = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    val sources =
      listOf(api.models, api.models.map { it.copy(targets = emptyMap()) }).map { models ->
        val registry = SwiftTypeRegistry(setOf())
        SwiftSundayIrGenerator(
          api.copy(models = models),
          registry,
          SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
        ).generateServiceTypes()
        assertTrue(compileTypes(compiler, registry.buildTypes()))
        CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Record.swift")
      }
    assertEquals(sources[0], sources[1])
  }
}
