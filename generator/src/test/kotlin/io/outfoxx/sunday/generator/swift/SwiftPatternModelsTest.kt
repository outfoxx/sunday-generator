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
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftPatternModelsTest {
  @ParameterizedTest
  @CsvSource("false,true", "true,true", "false,false", "true,false")
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    preserve: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    val source = patternModelsApi(directory, composed)
    val state =
      GeneratedModel(
        "DynamicState",
        GeneratedModel.Kind.ENUM,
        values = listOf("active", "unknown"),
        unknownValue = "unknown",
      )
    val numbers =
      GeneratedModel(
        "DynamicNumbers",
        GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.scalar("number", nullable = true)),
        validation = mapOf("minItems" to "1", "minimum" to "2", "multipleOf" to "2"),
      )
    val strings =
      GeneratedModel(
        "DynamicStrings",
        GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
        validation = mapOf("minItems" to "1", "minLength" to "3", "pattern" to "^ok"),
      )
    val code =
      GeneratedModel(
        "DynamicCode",
        GeneratedModel.Kind.SCALAR_ALIAS,
        nominal = true,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
        validation = mapOf("pattern" to "^code-", "minLength" to "7"),
      )
    val identity = code.copy(name = "DynamicId", validation = mapOf("pattern" to "^id-", "minLength" to "5"))
    val identifier =
      GeneratedModel(
        "DynamicIdentifier",
        GeneratedModel.Kind.UNION,
        aliases = listOf(GeneratedTypeRef.named(code.name), GeneratedTypeRef.named(identity.name)),
        unionMode = GeneratedModel.UnionMode.ONE_OF,
      )
    val problem =
      GeneratedModel(
        "DynamicProblem",
        GeneratedModel.Kind.OBJECT,
        closed = true,
        properties =
          listOf(
            GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri")),
            GeneratedModelProperty(
              "title",
              GeneratedTypeRef.scalar("string"),
              validation = mapOf("minLength" to "3"),
            ),
            GeneratedModelProperty(
              "status",
              GeneratedTypeRef.scalar("integer"),
              validation = mapOf("minimum" to "400"),
            ),
            GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
            GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string", format = "uri"), required = false),
          ),
      )
    val api =
      source.copy(
        models =
          source.models.map { model ->
            if (model.name != "PatternRecord") {
              model
            } else {
              model.copy(
                properties =
                  model.properties +
                    listOf(
                      GeneratedModelProperty("state", GeneratedTypeRef.named(state.name), required = false),
                      GeneratedModelProperty("code", GeneratedTypeRef.named(code.name), required = false),
                    ),
                patternProperties =
                  model.patternProperties +
                    listOf(
                      GeneratedPatternProperty("^state-", GeneratedTypeRef.named(state.name)),
                      GeneratedPatternProperty("^problem-", GeneratedTypeRef.named(problem.name)),
                      GeneratedPatternProperty("^numbers-", GeneratedTypeRef.named(numbers.name)),
                      GeneratedPatternProperty("^strings-", GeneratedTypeRef.named(strings.name)),
                      GeneratedPatternProperty("^code-", GeneratedTypeRef.named(code.name)),
                      GeneratedPatternProperty("^identifier-", GeneratedTypeRef.named(identifier.name)),
                      GeneratedPatternProperty("^uuid-", GeneratedTypeRef.scalar("string", format = "uuid")),
                      GeneratedPatternProperty("^bytes-", GeneratedTypeRef.scalar("string", format = "byte")),
                      GeneratedPatternProperty("^url-", GeneratedTypeRef.scalar("string", format = "uri")),
                      GeneratedPatternProperty("^date-", GeneratedTypeRef.scalar("string", format = "date")),
                      GeneratedPatternProperty("^time-", GeneratedTypeRef.scalar("string", format = "time")),
                      GeneratedPatternProperty("^instant-", GeneratedTypeRef.scalar("string", format = "date-time")),
                      GeneratedPatternProperty("^local-", GeneratedTypeRef.scalar("string", format = "datetime-only")),
                    ),
              )
            }
          } + listOf(state, numbers, strings, code, identity, identifier, problem),
      )
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API", preserveUnknownFields = preserve),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("PatternTests.swift"),
      """
      import Foundation
      import XCTest
      import PotentCodables
      import Sunday
      @testable import SundayGenTest
      final class PatternTests: XCTestCase {
        func testPatterns() throws {
          let valid = [${patternModelValid.joinToString { "#\"$it\"#" }}]
          let invalid = [${patternModelInvalid.joinToString { "#\"$it\"#" }}]
          for wire in valid {
            let decoded = try JSONDecoder().decode(PatternRecord.self, from: Data(wire.utf8))
            let encoded = try JSONEncoder().encode(decoded)
            let input = try JSONSerialization.jsonObject(with: Data(wire.utf8)) as! NSDictionary
            let output = try JSONSerialization.jsonObject(with: encoded) as! NSDictionary
            if $preserve {
              for (key, value) in input { XCTAssertEqual(output[key] as? NSObject, value as? NSObject) }
            } else {
              XCTAssertNil(output["x-value"])
              XCTAssertNil(output["maybe-value"])
            }
          }
          for wire in invalid {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(wire.utf8)), wire)
          }
          ${patternModelRegressions.entries.joinToString("\n") { (name, values) ->
        """
        for wire in [${values.first.joinToString { "#\"$it\"#" }}] {
          _ = try JSONDecoder().decode($name.self, from: Data(wire.utf8))
        }
        for wire in [${values.second.joinToString { "#\"$it\"#" }}] {
          XCTAssertThrowsError(try JSONDecoder().decode($name.self, from: Data(wire.utf8)), wire)
        }
        """.trimIndent()
      }}
          _ = try JSONDecoder().decode(PatternRecord.self, from:
            Data(#"{"code-value":"code-valid","identifier-one":"id-ok","identifier-two":"code-valid"}"#.utf8))
          for raw in [#"{"code-value":"invalid"}"#, #"{"code-value":"code-x"}"#,
                      #"{"identifier-one":"invalid"}"#, #"{"identifier-one":1}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(raw.utf8)), raw)
          }
          _ = try JSONDecoder().decode(PatternRecord.self, from:
            Data(#"{"numbers-values":[2,null,4],"strings-values":["okay","ok!"]}"#.utf8))
          for raw in [#"{"numbers-values":[1]}"#, #"{"numbers-values":[3]}"#,
                      #"{"numbers-values":[]}"#, #"{"strings-values":["ok"]}"#,
                      #"{"strings-values":["bad"]}"#, #"{"strings-values":[null]}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(raw.utf8)), raw)
          }
          _ = try JSONDecoder().decode(PatternRecord.self, from: Data(#"{"uuid-value":"E621E1F8-C36C-495A-93FC-0C247A3E6E5F","bytes-value":"YWJj","url-value":"https://example.com/path","date-value":"2026-09-30","time-value":"12:34:56.123","instant-value":"2026-09-30T12:34:56.123+01:00","local-value":"2026-09-30T12:34:56"}"#.utf8))
          for raw in [#"{"uuid-value":"invalid"}"#, #"{"bytes-value":"invalid"}"#,
                      #"{"url-value":"http://[invalid"}"#, #"{"date-value":"yesterday"}"#,
                      #"{"time-value":"noon"}"#, #"{"instant-value":"2026-09-30T12:34:56"}"#,
                      #"{"local-value":"2026-09-30T12:34:56Z"}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(raw.utf8)), raw)
          }
          let problem = try JSONDecoder().decode(DynamicProblem.self, from:
            Data(#"{"type":"about:blank","title":"Bad request","status":400}"#.utf8))
          XCTAssertTrue(problem.isValid(.request))
          XCTAssertNoThrow(try problem.validate(.response))
          _ = try JSONDecoder().decode(PatternRecord.self, from:
            Data(#"{"problem-one":{"type":"about:blank","title":"Bad request","status":400}}"#.utf8))
          for raw in [#"{"problem-one":{"type":"about:blank","title":"no","status":400}}"#,
                      #"{"problem-one":{"type":"about:blank","title":"Bad request","status":200}}"#,
                      #"{"problem-one":{"type":"about:blank","title":"Bad request","status":400,"extra":1}}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(PatternRecord.self, from: Data(raw.utf8)), raw)
          }
          _ = try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"x-valid":"ok"}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"x-invalid":"a"}"#.utf8)))
          XCTAssertThrowsError(try JSONDecoder().decode(PatternInherited.self, from: Data(#"{"extra":1}"#.utf8)))
          _ = try JSONDecoder().decode(OpenPattern.self, from: Data(#"{"extra":1,"x-valid":"ok"}"#.utf8))
          XCTAssertThrowsError(try JSONDecoder().decode(OpenPattern.self, from: Data(#"{"extra":"wrong"}"#.utf8)))
          _ = try JSONDecoder().decode(PatternOnly.self, from: Data(#"{"x-valid":"ok"}"#.utf8))
          ${if (preserve) {
        """
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["numbers-values": [2, 3]])) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Unexpected failure") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/numbers-values/1"])
            XCTAssertEqual(failure.diagnostics.map(\.reason), [.multipleOf])
          }
          XCTAssertThrowsError(try PatternRecord(additionalProperties: [
            "problem-one": ["type": "about:blank", "title": "Bad request", "status": 200]
          ]))
          let branded = try PatternRecord(code: DynamicCode("code-valid"), additionalProperties: ["identifier-id": "id-ok"])
          XCTAssertTrue(branded.isValid(.request))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["code-value": "code-x"]))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["uuid-value": "invalid"]))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["bytes-value": "invalid"]))
          let stored = try PatternRecord(additionalProperties: ["alias-value": "valid", "r-object": ["label": "child"]])
          XCTAssertTrue(stored.isValid(.request))
          XCTAssertNoThrow(try stored.validate(.response))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["alias-value": "ab"]))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["r-object": ["extra": 1]]))
          XCTAssertThrowsError(try PatternRecord(additionalProperties: ["x-end": "bad"]))
          let future = try PatternRecord(additionalProperties: ["state-future": "future"])
          XCTAssertTrue(future.isValid(.response))
          XCTAssertFalse(future.isValid(.request))
          XCTAssertThrowsError(try future.validate(.request))
          let known = try PatternRecord(state: .active, additionalProperties: ["state-known": "active"])
          XCTAssertTrue(known.isValid(.request))
          let disguised = try PatternRecord(state: .unknown("active"))
          XCTAssertTrue(disguised.isValid(.response))
          XCTAssertFalse(disguised.isValid(.request))
          do {
            try future.validate(.request)
            XCTFail("Expected directional dynamic-property failure")
          } catch let error as ModelValidationError {
            XCTAssertEqual(error.diagnostics.map(\.jsonPointer), ["/state-future"])
            XCTAssertEqual(error.diagnostics.map(\.reason), [.unknownEnum])
          }

          """
      } else {
        ""
      }}

        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val model = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/PatternRecord.swift")
    val validator = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/PatternRecordValidation.swift")
    assertFalse(model.contains("AdditionalPropertiesValidator"))
    assertFalse(model.contains("allProperties.decode("))
    assertTrue(validator.contains("ModelObjectValidation("))
    assertTrue(validator.contains("PatternChildValidation.isValid(normalized:"))
    assertFalse(validator.contains(".decode("))
  }
}
