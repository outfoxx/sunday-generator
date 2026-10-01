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
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftDirectionalToleranceTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `tolerant unions validate dynamic payloads without constructing models`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = directionalToleranceApi(frontend, directory)
    val eventType = if (frontend == "raml") "EventRef" else "Event"
    val holder =
      GeneratedModel(
        "DynamicEvents",
        GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("label", GeneratedTypeRef.scalar("string"), required = false)),
        additionalProperties = GeneratedAdditionalProperties(type = GeneratedTypeRef.named("Event")),
      )
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      source.copy(models = source.models + holder),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API", preserveUnknownFields = true),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("DynamicUnionTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class DynamicUnionTests: XCTestCase {
        func testCanonicalFallback() throws {
          let known = try DynamicEvents(additionalProperties: ["event": ["kind": "created", "count": 2]])
          XCTAssertTrue(known.isValid(.request))
          let unknown = try DynamicEvents(additionalProperties: ["event": ["kind": "future", "note": "valid", "extra": true]])
          XCTAssertTrue(unknown.isValid(.response))
          XCTAssertFalse(unknown.isValid(.request))
          XCTAssertThrowsError(try unknown.validate(.request)) { error in
            guard let failure = error as? ModelValidationError else { return XCTFail("Unexpected failure") }
            XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/event"])
            XCTAssertEqual(failure.diagnostics.map(\.reason), [.unknownUnion])
          }
          XCTAssertThrowsError(try DynamicEvents(additionalProperties: ["event": ["kind": "created"]]))
          XCTAssertThrowsError(try DynamicEvents(additionalProperties: ["event": ["kind": "created", "count": 0]]))
          XCTAssertThrowsError(try DynamicEvents(additionalProperties: ["event": ["kind": "future", "note": "x"]]))
          let manual = $eventType.unknown(try EventUnknown(kind: "created", note: nil, state: nil, rawBody: ["kind": "created"]))
          XCTAssertTrue(manual.isValid(.response))
          XCTAssertFalse(manual.isValid(.request))
          let wire = try JSONEncoder().encode(unknown)
          let restored = try JSONDecoder().decode(DynamicEvents.self, from: wire)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(restored)) as? NSDictionary,
                         try JSONSerialization.jsonObject(with: wire) as? NSDictionary)
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val output = if (frontend == "asyncapi") "Events" else "Models"
    val validation =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "$output/EventUnknownValidation.swift",
      )
    val dynamic = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DynamicEvents.swift")
    assertTrue(validation.contains("isValid(normalized:"))
    assertFalse(validation.contains(".decode("))
    assertFalse(dynamic.contains("AdditionalPropertiesValidator"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `string discriminator fallbacks retain unknown payloads and reject malformed known branches`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    val api = directionalToleranceApi(frontend, directory)
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    val eventType = if (frontend == "raml") "EventRef" else "Event"
    val requestTest =
      if (frontend == "asyncapi") {
        ""
      } else {
        val operation =
          api.services
            .first()
            .operations
            .first()
            .id
        """
        func testRequestBoundary() async throws {
          let transport = URLSessionTransport(baseURL: URI.Template(format: "https://example.com"))
          defer { transport.close() }
          let api = API(transport: transport)
          let known = try JSONDecoder().decode(Item.self, from: Data(#"{"state":"active"}"#.utf8))
          let operation = try api.$operation(body: known)
          let request = try await operation.transportRequest()
          XCTAssertNotNil(request.httpBody)
          let unknown = try JSONDecoder().decode(Item.self, from: Data(#"{"state":"future"}"#.utf8))
          let invalid = try api.$operation(body: unknown)
          do {
            _ = try await invalid.transportRequest()
            XCTFail("Expected request validation before encoding")
          } catch let SundayError.requestEncodingFailed(reason) {
            guard case .serializationFailed(_, let error) = reason else { return XCTFail("Unexpected encoding failure") }
            XCTAssertTrue(error is ModelValidationError)
          }
        }
        """.trimIndent()
      }
    Files.writeString(
      compiler.testsDir.resolve("ToleranceTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ToleranceTests: XCTestCase {
        $requestTest
        func testFallback() throws {
          let raw = #"{"kind":"future","detail":{"attempt":2}}"#
          let unknown = try JSONDecoder().decode($eventType.self, from: Data(raw.utf8))
          guard case .unknown = unknown else { return XCTFail("expected fallback") }
          XCTAssertTrue(unknown.isValid(.response))
          XCTAssertFalse(unknown.isValid(.request))
          XCTAssertThrowsError(try unknown.validate(.request))
          XCTAssertFalse(State.unknown("active").isValid(.request))
          XCTAssertTrue(OpenState.unknown("future").isValid(.request))
          XCTAssertTrue(StateValidation.isValid(.active, .request))
          try StateValidation.validate(.active, .request)
          let encoded = try JSONEncoder().encode(unknown)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoded) as? NSDictionary,
                         try JSONSerialization.jsonObject(with: Data(raw.utf8)) as? NSDictionary)
          let retained = try EventUnknown(kind: "future", note: "valid", state: nil,
            rawBody: ["kind": "future", "note": "x", "extra": true])
          let retainedWire = try JSONSerialization.jsonObject(with: JSONEncoder().encode(retained)) as! NSDictionary
          XCTAssertEqual(retainedWire["note"] as? String, "valid")
          XCTAssertEqual(retainedWire["extra"] as? Bool, true)
          _ = try JSONDecoder().decode($eventType.self, from: Data(#"{"kind":"created","count":1}"#.utf8))
          for raw in [#"{"kind":"future","note":"x"}"#, #"{"kind":"future","note":null}"#, #"{"kind":"created"}"#, #"{"kind":null}"#, "{}"] {
            XCTAssertThrowsError(try JSONDecoder().decode($eventType.self, from: Data(raw.utf8)))
          }
        }
        func testNestedPaths() throws {
          let item = try JSONDecoder().decode(Item.self, from: Data(
            #"{"state":"active","states":["active","future"],"next":{"state":"future"}}"#.utf8))
          XCTAssertTrue(item.isValid(.response))
          XCTAssertFalse(item.isValid(.request))
          do {
            try item.validate(.request)
            XCTFail("expected directional failures")
          } catch let error as ModelValidationError {
            XCTAssertEqual(Set(error.diagnostics.map(\.jsonPointer)), Set(["/states/1", "/next/state"]))
            XCTAssertTrue(error.diagnostics.allSatisfy { ${'$'}0.reason == .unknownEnum })
          }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
