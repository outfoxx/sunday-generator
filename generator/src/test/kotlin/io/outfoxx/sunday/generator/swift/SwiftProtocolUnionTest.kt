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
import io.outfoxx.sunday.generator.ir.GeneratedModelScope
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.protocolUnionApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("responses")
@Tag("validation")
class SwiftProtocolUnionTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "raml", "composed"])
  fun `service scoped protocol union branches compile and decode`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val input = protocolUnionApi(frontend, directory)
    val scope = GeneratedModelScope("Local", "getChoice", usage = GeneratedModelScope.Usage.RESPONSE_BODY)
    val scopedNames = setOf("CollectionProblem", "CollectionChoice")

    fun scoped(type: GeneratedTypeRef): GeneratedTypeRef =
      type.copy(scope = if (type.name in scopedNames) scope else type.scope, arguments = type.arguments.map(::scoped))
    val api =
      input.copy(
        models =
          input.models.map { model ->
            model.copy(
              scope = if (model.name in scopedNames) scope else model.scope,
              inherits = model.inherits.map(::scoped),
              aliases = model.aliases.map(::scoped),
            )
          },
        services =
          listOf(
            GeneratedService(
              "Local",
              operations =
                listOf(
                  GeneratedOperation(
                    "getChoice",
                    "get",
                    "/choice",
                    responses = listOf(GeneratedResponse(200, scoped(GeneratedTypeRef.named("CollectionChoice")))),
                  ),
                ),
            ),
          ),
      )
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("https://example.org/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("ScopedProtocolUnionTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ScopedProtocolUnionTests: XCTestCase {
        func testParentOnlyValue() throws {
          let wire = Data(#"{"type":"https://example.org/problem","title":"Problem","status":400,"detail":"valid"}"#.utf8)
          let value = try JSONDecoder().decode(LocalAPI<URLSessionTransport>.CollectionChoice.self, from: wire)
          guard case .collectionProblem(let parent) = value else { return XCTFail("Expected parent branch") }
          let _: AnyCollectionProblem = parent
          let _: any CollectionProblem = parent
          XCTAssertTrue(parent.isValid(.response))
          XCTAssertTrue(value.isValid(.response))
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? NSDictionary,
            try JSONSerialization.jsonObject(with: wire) as? NSDictionary)
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @CsvSource(
    "openapi,FooRef,AnyFoo,AnyFooRef_,false",
    "asyncapi,FooRef,AnyFoo,AnyFooRef_,false",
    "raml,FooRef,AnyFoo,AnyFooRef_,false",
    "composed,FooRef,AnyFoo,AnyFooRef_,false",
    "openapi,FooRefValidation,AnyFoo,AnyFooRefValidation_,false",
    "openapi,Foo,Pet,AnyFoo_,true",
  )
  fun `protocol implementations avoid reference and validation helper names`(
    frontend: String,
    parentName: String,
    protocolName: String,
    implementationName: String,
    validationCollision: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    val api =
      protocolUnionApi(
        frontend,
        directory,
        collision = validationCollision,
        typeNames =
          mapOf(
            "CollectionProblem" to parentName,
            "Pet" to protocolName,
            "AnyBaseProblem" to "AnyFooValidation",
          ),
      )
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("https://example.org/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("HelperCollisionTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class HelperCollisionTests: XCTestCase {
        func testIndependentParentAndDiscriminatedValues() throws {
          let decoder = JSONDecoder()
          let parent = Data(#"{"type":"https://example.org/problem","title":"Problem","status":400,"detail":"valid"}"#.utf8)
          let choice = try decoder.decode(CollectionChoice.self, from: parent)
          guard case .${parentName.replaceFirstChar(
        Char::lowercaseChar,
      )}(let payload) = choice else { return XCTFail("Expected parent") }
          let _: $implementationName = payload
          let _: any $parentName = payload
          XCTAssertTrue(choice.isValid(.response))
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(choice)) as? NSDictionary,
            try JSONSerialization.jsonObject(with: parent) as? NSDictionary)
          let response = try decoder.decode(Response.self, from: Data(#"{"kind":"cat","lives":9}"#.utf8))
          guard case .${protocolName.replaceFirstChar(
        Char::lowercaseChar,
      )}(let pet) = response else { return XCTFail("Expected pet") }
          let _: ${protocolName}Ref = pet
          XCTAssertTrue(response.isValid(.response))
          XCTAssertNoThrow(try JSONEncoder().encode(response))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @CsvSource(
    "openapi,true,false",
    "asyncapi,true,false",
    "raml,true,false",
    "composed,true,false",
    "openapi,false,false",
    "openapi,true,true",
  )
  fun `protocol union branches decode parent values and preserve original matching rules`(
    frontend: String,
    preserveUnknownFields: Boolean,
    collision: Boolean,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      protocolUnionApi(frontend, directory, collision),
      registry,
      SwiftSundayOptions(
        "https://example.org/",
        listOf("application/json"),
        "API",
        preserveUnknownFields = preserveUnknownFields,
      ),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    val implementationName = if (collision) "AnyBaseProblem_" else "AnyBaseProblem"
    val extra = if (preserveUnknownFields) ",\"extra\":{\"flag\":true}" else ""
    val intermediateCheck =
      if (frontend == "raml") {
        ""
      } else {
        """
        let mammalResponse = try decoder.decode(MammalResponse.self, from: Data(wire.utf8))
        guard case .mammal(let mammal) = mammalResponse else { return XCTFail("Expected mammal branch") }
        let _: MammalRef = mammal
        XCTAssertTrue(mammalResponse.isValid(.response))
        try roundTrip(mammalResponse, wire)
        """.trimIndent()
      }
    compiler.testsDir.resolve("ProtocolUnionTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ProtocolUnionTests: XCTestCase {
        func testParentSchema() throws {
          let parent = #"{"type":"https://example.org/problem","title":"Problem","status":400,"detail":"valid"$extra}"#
          let child = parent.replacingOccurrences(of: #""detail":"valid""#, with: #""detail":"valid","reason":"bad""#)
          let malformedChild = child.replacingOccurrences(of: "bad", with: "x")
          let decoder = JSONDecoder()
          let concrete = try decoder.decode($implementationName.self, from: Data(parent.utf8))
          let _: any BaseProblem = concrete
          XCTAssertTrue(concrete.isValid(.request))
          try roundTrip(concrete, parent)
          let collection = try decoder.decode(CollectionChoice.self, from: Data(parent.utf8))
          guard case .collectionProblem(let collectionPayload) = collection else { return XCTFail("Expected collection") }
          let _: AnyCollectionProblem = collectionPayload
          XCTAssertTrue(collection.isValid(.request))
          try roundTrip(collection, parent)
          XCTAssertThrowsError(try decoder.decode(CollectionChoice.self, from: Data(parent.replacingOccurrences(of: "400", with: "401").utf8)))
          for wire in [parent, child, malformedChild] {
            let value = try decoder.decode(Inclusive.self, from: Data(wire.utf8))
            guard case .baseProblem(let payload) = value else { return XCTFail("Expected parent branch") }
            let _: $implementationName = payload
            XCTAssertTrue(value.isValid(.request))
            try roundTrip(value, ${if (preserveUnknownFields) "wire" else "parent"})
            let reversed = try decoder.decode(ReverseInclusive.self, from: Data(wire.utf8))
            if wire == child {
              guard case .childProblem = reversed else { return XCTFail("Expected child branch") }
            } else {
              guard case .baseProblem = reversed else { return XCTFail("Expected parent-only branch") }
            }
            XCTAssertTrue(reversed.isValid(.response))
            try roundTrip(reversed, ${if (preserveUnknownFields) "wire" else "wire == child ? child : parent"})
          }
          let exclusive = try decoder.decode(Exclusive.self, from: Data(parent.utf8))
          XCTAssertTrue(exclusive.isValid(.request))
          try roundTrip(exclusive, parent)
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try decoder.decode(Exclusive.self, from: Data(child.utf8)))
          let parentOnlyMatch = try decoder.decode(Exclusive.self, from: Data(malformedChild.utf8))
          XCTAssertTrue(parentOnlyMatch.isValid(.response))
          try roundTrip(parentOnlyMatch, ${if (preserveUnknownFields) "malformedChild" else "parent"})
          let childValue = try decoder.decode(ChildProblem.self, from: Data(child.utf8))
          let ambiguous = Exclusive.childProblem(childValue)
          XCTAssertEqual(ambiguous.isValid(.request), ${frontend == "raml"})
          ${if (frontend == "raml") "XCTAssertNoThrow" else "XCTAssertThrowsError"}(try JSONEncoder().encode(ambiguous))
          for invalid in [parent.replacingOccurrences(of: "valid", with: "x"),
                          parent.replacingOccurrences(of: "400", with: "200")] {
            XCTAssertThrowsError(try decoder.decode($implementationName.self, from: Data(invalid.utf8)))
            XCTAssertThrowsError(try decoder.decode(Inclusive.self, from: Data(invalid.utf8)))
            XCTAssertThrowsError(try decoder.decode(Exclusive.self, from: Data(invalid.utf8)))
          }
        }
        func testDiscriminatedProtocolBranch() throws {
          let decoder = JSONDecoder()
          for wire in [#"{"kind":"cat","lives":9}"#, #"{"kind":"dog","bark":true}"#] {
            let value = try decoder.decode(Response.self, from: Data(wire.utf8))
            guard case .pet(let pet) = value else { return XCTFail("Expected pet branch") }
            let _: PetRef = pet
            $intermediateCheck
            XCTAssertTrue(value.isValid(.request))
            try roundTrip(value, wire)
          }
          for invalid in [#"{"kind":"cat","lives":0}"#, #"{"kind":"dog"}"#, #"{"kind":"fish"}"#] {
            XCTAssertThrowsError(try decoder.decode(Response.self, from: Data(invalid.utf8)))
          }
        }
        private func roundTrip<T: Encodable>(_ value: T, _ wire: String) throws {
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? NSDictionary,
            try JSONSerialization.jsonObject(with: Data(wire.utf8)) as? NSDictionary)
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
