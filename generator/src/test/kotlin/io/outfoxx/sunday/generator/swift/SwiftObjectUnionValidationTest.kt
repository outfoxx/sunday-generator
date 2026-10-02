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
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
@Tag("validation")
class SwiftObjectUnionValidationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("responses")
  fun `problem hierarchies validate dynamic fields through their canonical schema`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = objectUnionValidationApi(frontend, directory)
    val root =
      GeneratedModel(
        "WireProblem",
        GeneratedModel.Kind.OBJECT,
        discriminator = "code",
        properties =
          listOf(
            GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
            GeneratedModelProperty(
              "title",
              GeneratedTypeRef.scalar("string"),
              required = true,
              validation =
                mapOf(
                  "minLength" to "2",
                ),
            ),
            GeneratedModelProperty(
              "status",
              GeneratedTypeRef.scalar("integer"),
              required = true,
              validation =
                mapOf(
                  "minimum" to "400",
                ),
            ),
            GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
          ),
      )
    val branch =
      GeneratedModel(
        "InvalidProblem",
        GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named(root.name)),
        discriminatorValue = "invalid",
        properties =
          listOf(
            GeneratedModelProperty(
              "detail",
              GeneratedTypeRef.scalar("string"),
              required = true,
              validation = mapOf("minLength" to "2"),
            ),
          ),
      )
    val holder =
      GeneratedModel(
        "ProblemHolder",
        GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("problem", GeneratedTypeRef.named(root.name), required = false)),
        additionalProperties = GeneratedAdditionalProperties(type = GeneratedTypeRef.named(root.name)),
      )
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      source.copy(models = source.models + listOf(root, branch, holder)),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API", preserveUnknownFields = true),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ProblemHierarchyTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ProblemHierarchyTests: XCTestCase {
        func testCanonicalProblem() throws {
          let raw = #"{"type":"https://example.com/invalid","title":"Invalid","status":400,"code":"invalid","detail":"valid"}"#
          let decoder = JSONDecoder()
          let problem = try decoder.decode(WireProblemRef.self, from: Data(raw.utf8))
          XCTAssertTrue(problem.isValid(.request))
          let holder = try decoder.decode(ProblemHolder.self, from: Data("{\"problem\":\(raw),\"other\":\(raw)}".utf8))
          XCTAssertTrue(holder.isValid(.request))
          XCTAssertNoThrow(try JSONEncoder().encode(holder))
          let dynamic = try ProblemHolder(problem: problem, additionalProperties: ["other": [
            "type": "https://example.com/invalid", "title": "Invalid", "status": 400, "code": "invalid", "detail": "valid"
          ]])
          XCTAssertTrue(dynamic.isValid(.response))
          for invalid in [raw.replacingOccurrences(of: "valid\"", with: "x\""),
                          raw.replacingOccurrences(of: "400", with: "200")] {
            XCTAssertThrowsError(try decoder.decode(WireProblemRef.self, from: Data(invalid.utf8)))
            XCTAssertThrowsError(try decoder.decode(ProblemHolder.self, from: Data("{\"other\":\(invalid)}".utf8)))
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val validation = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/WireProblemValidation.swift")
    assertTrue(validation.contains("isValid(normalized:"))
    assertFalse(validation.contains(".decode("))
  }

  @ParameterizedTest
  @CsvSource("openapi,false", "asyncapi,false", "composed,false", "openapi,true", "asyncapi,true", "composed,true")
  fun `discriminated object unions share canonical validation with dynamic fields`(
    frontend: String,
    mappingOnly: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source =
      objectUnionValidationApi(frontend, directory, discriminated = true, commonMaximum = 5, mappingOnly = mappingOnly)
    val choiceType = if (mappingOnly) "ChoiceRef" else "Choice"
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      source,
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API", preserveUnknownFields = true),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("DiscriminatedUnionTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class DiscriminatedUnionTests: XCTestCase {
        func testSelectedBranchValidation() throws {
          let decoder = JSONDecoder()
          for kind in ["small", "large"] {
            let wire = "{\"kind\":\"\(kind)\",\"value\":2}"
            let choice = try decoder.decode($choiceType.self, from: Data(wire.utf8))
            XCTAssertTrue(choice.isValid(.request))
            XCTAssertNoThrow(try choice.validate(.response))
            XCTAssertNoThrow(try JSONEncoder().encode(choice))
            let holder = try decoder.decode(Holder.self, from: Data("{\"choice\":\(wire),\"extra-choice\":\(wire)}".utf8))
            XCTAssertTrue(holder.isValid(.request))
            XCTAssertNoThrow(try Holder(additionalProperties: ["extra-choice": ["kind": .string(kind), "value": 2]]))
          }
          let reused = try Large(kind: "large", value: 9)
          XCTAssertTrue(reused.isValid(.request))
          XCTAssertFalse($choiceType.large(reused).isValid(.request))
          XCTAssertThrowsError(try $choiceType.large(reused).validate(.response))
          for wire in [#"{"kind":"small","value":9}"#, #"{"kind":"large","value":1}"#, #"{"kind":"large","value":9}"#,
                       #"{"kind":"future","value":2}"#, #"{"kind":null,"value":2}"#, #"{"value":2}"#] {
            XCTAssertThrowsError(try decoder.decode($choiceType.self, from: Data(wire.utf8)), wire)
            XCTAssertThrowsError(try decoder.decode(Holder.self, from: Data("{\"extra-choice\":\(wire)}".utf8)), wire)
          }
          XCTAssertThrowsError(try Holder(additionalProperties: ["extra-choice": ["kind": "large", "value": 9]])) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Unexpected failure") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/extra-choice/value"])
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val output = if (frontend == "asyncapi") "Events" else "Models"
    val union = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/$choiceType.swift")
    val validation = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/ChoiceValidation.swift")
    val holder = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/Holder.swift")
    assertTrue(union.contains("context.selectedAlternative"))
    assertTrue(validation.contains("SmallValidation.isValid(normalized:"))
    assertFalse(validation.contains(".decode("))
    assertFalse(holder.contains("AdditionalPropertiesValidator"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `object unions share canonical validation in stored and dynamic values`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      objectUnionValidationApi(frontend, directory),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API", preserveUnknownFields = true),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ObjectUnionTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ObjectUnionTests: XCTestCase {
        func testUnionValidation() throws {
          let decoder = JSONDecoder()
          for value in [1, 9] {
            let choice = try decoder.decode(Choice.self, from: Data("{\"value\":\(value)}".utf8))
            XCTAssertTrue(choice.isValid(.request))
            XCTAssertNoThrow(try choice.validate(.response))
            XCTAssertNoThrow(try JSONEncoder().encode(choice))
            let holder = try decoder.decode(Holder.self, from:
              Data("{\"choice\":{\"value\":\(value)},\"extra-choice\":{\"value\":\(value)}}".utf8))
            XCTAssertTrue(holder.isValid(.request))
            XCTAssertNoThrow(try Holder(additionalProperties: ["extra-choice": ["value": .int64(Int64(value))]]))
          }
          for wire in [#"{"value":0}"#, #"{"value":10}"#, #"{"value":"1"}"#, #"{"value":1,"extra":true}"#] {
            XCTAssertThrowsError(try decoder.decode(Choice.self, from: Data(wire.utf8)), wire)
          }
          XCTAssertThrowsError(try Holder(additionalProperties: ["extra-choice": ["value": 10]])) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Unexpected failure") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/extra-choice"])
          }
          let ambiguous = Choice.small(try Small(value: 2))
          XCTAssertEqual(ambiguous.isValid(.request), ${frontend == "raml"})
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try ambiguous.validate(.response))
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try JSONEncoder().encode(ambiguous))
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try decoder.decode(Choice.self, from: Data(#"{"value":2}"#.utf8)))
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try Holder(additionalProperties: ["extra-choice": ["value": 2]]))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val output = if (frontend == "asyncapi") "Events" else "Models"
    val union = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/Choice.swift")
    val validation = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/ChoiceValidation.swift")
    val holder = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "$output/Holder.swift")
    assertTrue(union.contains("context.selectedAlternative"))
    assertTrue(validation.contains("SmallValidation.isValid(normalized:"))
    assertFalse(validation.contains(".decode("))
    assertFalse(holder.contains("AdditionalPropertiesValidator"))
  }
}
