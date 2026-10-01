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
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftUnionAliasValidationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `erased unions retain canonical branch constraints`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = objectUnionValidationApi(frontend, directory)
    val text =
      GeneratedModel(
        "CheckedText",
        GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
        validation = mapOf("minLength" to "2"),
      )
    val count =
      GeneratedModel(
        "CheckedCount",
        GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.scalar("integer")),
        validation = mapOf("minimum" to "1"),
      )
    val choice =
      GeneratedModel(
        "ScalarChoice",
        GeneratedModel.Kind.UNION,
        aliases = listOf(GeneratedTypeRef.named(text.name), GeneratedTypeRef.named(count.name)),
      )
    val holder =
      GeneratedModel(
        "ScalarHolder",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("value", GeneratedTypeRef.named(choice.name), required = true),
            GeneratedModelProperty(
              "inline",
              GeneratedTypeRef(GeneratedTypeRef.Kind.UNION, "union", arguments = choice.aliases),
            ),
          ),
      )
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      source.copy(models = source.models + listOf(text, count, choice, holder)),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ScalarUnionTests.swift"),
      """
      import Foundation
      import XCTest
      import PotentCodables
      import Sunday
      @testable import SundayGenTest
      final class ScalarUnionTests: XCTestCase {
        func testUnions() throws {
          for value: AnyValue in [.string("ok"), .int64(2)] {
            XCTAssertTrue(ScalarChoiceValidation.isValid(value, .request))
            XCTAssertTrue(try ScalarHolder(value: value, inline: value).isValid(.request))
          }
          for value: AnyValue in [.string("x"), .int64(0), .bool(true), .nil] {
            XCTAssertFalse(ScalarChoiceValidation.isValid(value, .response))
            XCTAssertThrowsError(try ScalarChoiceValidation.validate(value, .request))
            XCTAssertThrowsError(try ScalarHolder(value: value, inline: nil))
            XCTAssertThrowsError(try ScalarHolder(value: .string("ok"), inline: value))
          }
          for json in [#"{"value":"x"}"#, #"{"value":0}"#, #"{"value":true}"#, #"{"value":"ok","inline":false}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(ScalarHolder.self, from: Data(json.utf8)))
          }
          XCTAssertNoThrow(try JSONDecoder().decode(ScalarHolder.self, from: Data(#"{"value":"ok"}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
