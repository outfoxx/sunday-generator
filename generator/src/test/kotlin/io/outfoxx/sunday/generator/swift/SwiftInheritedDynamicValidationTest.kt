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
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.fieldConstraintsApi
import io.outfoxx.sunday.generator.tools.inheritedAdditionalPropertiesModels
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
@Tag("validation")
class SwiftInheritedDynamicValidationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `inherited dynamic rules and discriminator leaves use normalized validators`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = fieldConstraintsApi(directory, frontend)
    val array =
      GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(GeneratedTypeRef.scalar("integer")))
    val soloBase =
      GeneratedModel(
        "SoloBase",
        GeneratedModel.Kind.OBJECT,
        discriminator = "kind",
        properties =
          listOf(
            GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), allowedValues = listOf("only")),
            GeneratedModelProperty("items", array, validation = mapOf("minItems" to "1")),
          ),
      )
    val solo =
      soloBase.copy(
        name = "Solo",
        inherits = listOf(GeneratedTypeRef.named(soloBase.name)),
        properties = emptyList(),
        discriminatorValue = "only",
      )
    val holder =
      GeneratedModel(
        "SoloHolder",
        GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("label", GeneratedTypeRef.scalar("string"), required = false)),
        additionalProperties = GeneratedAdditionalProperties(type = GeneratedTypeRef.named(solo.name)),
      )
    val registry = SwiftTypeRegistry(setOf())
    val first =
      GeneratedModel(
        "FirstPattern",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("items", array, required = true, validation = mapOf("minItems" to "1")),
          ),
      )
    val second =
      first.copy(
        name = "SecondPattern",
        properties =
          listOf(
            GeneratedModelProperty("items", array, required = true, validation = mapOf("maxItems" to "3")),
          ),
      )
    val overlap =
      GeneratedModel(
        "OverlappingPatterns",
        GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("foo", GeneratedTypeRef.named(first.name), required = true)),
        patternProperties = listOf(GeneratedPatternProperty("^foo$", GeneratedTypeRef.named(second.name))),
      )
    SwiftSundayIrGenerator(
      source.copy(
        models =
          source.models + inheritedAdditionalPropertiesModels() +
            listOf(
              soloBase,
              solo,
              holder,
              first,
              second,
              overlap,
            ),
      ),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API", preserveUnknownFields = true),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("InheritedDynamicTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest

      final class InheritedDynamicTests: XCTestCase {
        func testOverlappingModelSchemas() throws {
          let large = try FirstPattern(items: [1, 2, 3, 4])
          XCTAssertThrowsError(try OverlappingPatterns(foo: large)) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Expected model validation") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/foo/items"])
          }
          let valid = try OverlappingPatterns(foo: FirstPattern(items: [1, 2]))
          XCTAssertTrue(valid.isValid(.request))
          XCTAssertThrowsError(try JSONDecoder().decode(OverlappingPatterns.self, from: Data(#"{"foo":{"items":[1,2,3,4]}}"#.utf8)))
        }
        func testInheritedAdditionalProperties() throws {
          let decoder = JSONDecoder()
          for raw in [#"{"id":"one","extra":[1]}"#, #"{"id":"one","extra":[1,2,3,4]}"#] {
            XCTAssertThrowsError(try decoder.decode(DynamicChild.self, from: Data(raw.utf8)))
            XCTAssertThrowsError(try decoder.decode(DynamicLeaf.self, from: Data(raw.utf8)))
            XCTAssertThrowsError(try decoder.decode(DynamicHolder.self, from: Data("{\"payload\":\(raw)}".utf8)))
          }
          XCTAssertThrowsError(try DynamicChild(id: "one", additionalProperties: ["extra": [1]]))
          XCTAssertThrowsError(try DynamicLeaf(id: "one", additionalProperties: ["extra": [1,2,3,4]]))
          let value = try DynamicLeaf(id: "one", additionalProperties: ["extra": [1,2]])
          XCTAssertTrue(value.isValid(.request))
          XCTAssertTrue(try decoder.decode(DynamicLeaf.self, from: JSONEncoder().encode(value)).isValid(.response))
        }
        func testLeafDiscriminatorWithoutDecoderValidation() throws {
          let holder = try SoloHolder(additionalProperties: ["one": ["kind": "only", "items": [1]]])
          XCTAssertTrue(holder.isValid(.request))
          XCTAssertThrowsError(try SoloHolder(additionalProperties: ["one": ["kind": "wrong", "items": [1]]]))
          XCTAssertThrowsError(try SoloHolder(additionalProperties: ["one": ["kind": "only", "items": []]])) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Expected model validation") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/one/items"])
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val validation = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/SoloValidation.swift")
    val model = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/SoloHolder.swift")
    assertTrue(validation.contains("isValid(normalized:"))
    assertFalse(validation.contains(".decode("))
    assertFalse(model.contains("AdditionalPropertiesValidator"))
  }
}
