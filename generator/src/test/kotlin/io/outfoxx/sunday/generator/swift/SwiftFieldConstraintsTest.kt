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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.fieldConstraintsApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("validation")
@Tag("models")
class SwiftFieldConstraintsTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml", "asyncapi", "composed"])
  fun `ordinary constraints validate constructors and decoding`(
    sourceKind: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    val source = fieldConstraintsApi(directory, sourceKind)
    val choices =
      GeneratedModel(
        name = "NullableChoices",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "choice",
              GeneratedTypeRef.scalar("string").copy(nullable = true),
              required = true,
              allowedValues = listOf("a"),
            ),
            GeneratedModelProperty(
              "optional",
              GeneratedTypeRef.scalar("string").copy(nullable = true),
              allowedValues = listOf("a"),
            ),
          ),
      )
    val numericChoice =
      GeneratedModel(
        name = "NumericChoice",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "choice",
              GeneratedTypeRef.scalar("number"),
              required = true,
              allowedValues = listOf(1.0),
            ),
          ),
      )
    val values =
      GeneratedModel(
        name = "Values",
        kind = GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
        validation = mapOf("minItems" to "1"),
      )
    val collection =
      GeneratedModel(
        name = "CollectionOwner",
        kind = GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("values", GeneratedTypeRef.named("Values"), required = true)),
      )
    val shortName =
      GeneratedModel(
        name = "ShortName",
        kind = GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
        validation = mapOf("minLength" to "3"),
      )
    val closed =
      GeneratedModel(
        name = "ClosedRecord",
        kind = GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.named("ShortName"), required = true)),
        additionalProperties = GeneratedAdditionalProperties(allowed = false),
      )
    val patch = choices.copy(name = "NullableChoicePatch", patchable = true)
    SwiftSundayIrGenerator(
      source.copy(
        models = source.models + listOf(choices, patch, numericChoice, values, collection, shortName, closed),
      ),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("FieldConstraintsTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class FieldConstraintsTests: XCTestCase {
        func testCanonicalAliasAndClosedProperties() throws {
          XCTAssertFalse(ShortNameValidation.isValid("x", .response))
          XCTAssertThrowsError(try ClosedRecord(name: "x"))
          XCTAssertTrue(try ClosedRecord(name: "valid").isValid(.request))
          for raw in [#"{"name":"x"}"#, #"{"name":"valid","extra":null}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(ClosedRecord.self, from: Data(raw.utf8))) { error in
              guard case DecodingError.dataCorrupted(let context) = error,
                    let validation = context.underlyingError as? ModelValidationError else {
                return XCTFail("Expected canonical validation diagnostics: \(error)")
              }
              XCTAssertEqual(validation.diagnostics.count, 1)
              XCTAssertTrue(["/name", "/extra"].contains(validation.diagnostics[0].jsonPointer))
            }
          }
        }
        func testNumericAllowedValuesRejectNonFiniteValues() throws {
          XCTAssertTrue(try NumericChoice(choice: 1.0).isValid(.response))
          for value in [Double.nan, Double.infinity, -Double.infinity, 2.0] {
            XCTAssertThrowsError(try NumericChoice(choice: value))
          }
        }
        func testNestedCollectionAlias() throws {
          XCTAssertTrue(ValuesValidation.isValid(["a"], .request))
          XCTAssertFalse(ValuesValidation.isValid([], .response))
          let value = try CollectionOwner(values: ["a"])
          XCTAssertTrue(value.isValid(.request))
          XCTAssertThrowsError(try CollectionOwner(values: []))
          XCTAssertThrowsError(try JSONDecoder().decode(CollectionOwner.self, from: Data(#"{"values":[]}"#.utf8)))
        }
        func testAllowedNullsAndPatchPresence() throws {
          _ = try NullableChoices(choice: "a")
          XCTAssertThrowsError(try NullableChoices(choice: nil))
          for json in [#"{"choice":null}"#, #"{"choice":"a","optional":null}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(NullableChoices.self, from: Data(json.utf8)))
          }
          let patch = try NullableChoicePatch()
          XCTAssertTrue(patch.isValid(.request))
          XCTAssertThrowsError(try patch.withChoice(choice: .delete))
          XCTAssertThrowsError(try JSONDecoder().decode(NullableChoicePatch.self, from: Data(#"{"choice":null}"#.utf8)))
          XCTAssertTrue(try patch.withChoice(choice: .set("a")).isValid(.request))
        }
        func testConstraints() throws {
          let value = try Probe(name: "valid", tags: ["a"], count: 5, id: "ID-ABC")
          let data = try JSONEncoder().encode(value)
          let decoded = try JSONDecoder().decode(Probe.self, from: data)
          XCTAssertEqual(decoded.name, "valid")
          XCTAssertEqual(decoded.tags, ["a"])
          XCTAssertEqual(decoded.count, 5)
          XCTAssertEqual(decoded.id, "ID-ABC")
          XCTAssertThrowsError(try value.withName(name: "BAD"))
          XCTAssertThrowsError(try value.withId(id: "invalid"))
          _ = try Probe(name: "valid")
          _ = try JSONDecoder().decode(Probe.self, from: Data(#"{"name":"valid"}"#.utf8))
          for invalid in ["a", "abcdef", "BAD"] {
            XCTAssertThrowsError(try Probe(name: invalid))
          }
          for invalid in [[], ["a", "b", "c"]] {
            XCTAssertThrowsError(try Probe(name: "valid", tags: invalid))
          }
          for invalid in [-1, 11] { XCTAssertThrowsError(try Probe(name: "valid", count: invalid)) }
          XCTAssertThrowsError(try Probe(name: "valid", id: "invalid"))
          let invalid: [(String, Any)] = [
            ("name", "a"), ("name", "abcdef"), ("name", "BAD"),
            ("tags", [String]()), ("tags", ["a", "b", "c"]),
            ("count", -1), ("count", 11), ("id", "invalid"),
            ${if (sourceKind == "asyncapi") "(\"id\", \"ID-XYZ\"), (\"id\", \"bad-ABC\")," else ""}
            ("tags", NSNull()), ("count", NSNull()), ("id", NSNull())
          ]
          for (field, bad) in invalid {
            if field == "id", let text = bad as? String {
              XCTAssertThrowsError(try Probe(name: "valid", id: text))
            }
            let payload: [String: Any] = ["name": "valid"].merging([field: bad]) { _, new in new }
            let json = try JSONSerialization.data(withJSONObject: payload)
            XCTAssertThrowsError(try JSONDecoder().decode(Probe.self, from: json)) { error in
              guard case DecodingError.dataCorrupted(let context) = error else {
                return XCTFail("Unexpected error: \(error)")
              }
              XCTAssertTrue(context.debugDescription.contains(field))
            }
          }
        }
        ${if (sourceKind == "raml") numericArrayTests else ""}
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val outputDirectory = if (sourceKind == "asyncapi") "Events" else "Models"
    val model = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$outputDirectory/Probe.swift")
    val validator =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "$outputDirectory/ProbeValidation.swift",
      )
    assertTrue(model.contains("ProbeValidation.validate(self, .response)"))
    assertTrue(model.contains("ProbeValidation.isValid(self, .response, context: &validationContext)"))
    assertFalse(model.contains("unicodeScalars.count"))
    assertFalse(model.contains(".regularExpression"))
    assertTrue(validator.contains("ModelValueConstraints("))
    assertTrue(validator.contains(".minLength("))
    assertTrue(validator.contains(".pattern("))
    assertFalse(validator.contains("unicodeScalars.count"))
    assertFalse(validator.contains(".regularExpression"))
  }

  private val numericArrayTests =
    """
    func testNumericArrays() throws {
      let value = try NumericProbe(samples: [0, 5, 10], optionalSamples: [1, 9])
      let decoded = try JSONDecoder().decode(NumericProbe.self, from: JSONEncoder().encode(value))
      XCTAssertEqual(decoded.samples, [0, 5, 10])
      XCTAssertEqual(decoded.optionalSamples, [1, 9])
      _ = try NumericProbe(samples: [0, 10])
      _ = try JSONDecoder().decode(NumericProbe.self, from: Data(#"{"samples":[0,10]}"#.utf8))
      for invalid in [[-1], [11], [0, 11]] {
        XCTAssertThrowsError(try NumericProbe(samples: invalid))
        XCTAssertThrowsError(try NumericProbe(samples: [0, 10], optionalSamples: invalid))
        for field in ["samples", "optionalSamples"] {
          let payload = ["samples": [0, 10]].merging([field: invalid]) { _, new in new }
          let data = try JSONSerialization.data(withJSONObject: payload)
          XCTAssertThrowsError(try JSONDecoder().decode(NumericProbe.self, from: data))
        }
      }
    }
    """.trimIndent()
}
