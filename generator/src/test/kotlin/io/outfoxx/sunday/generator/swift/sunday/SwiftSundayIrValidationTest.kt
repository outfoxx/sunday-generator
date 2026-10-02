/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Validation Test")
@Tag("validation")
class SwiftSundayIrValidationTest : SwiftSundayIrTestSupport() {

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @Tag("models")
  fun `AsyncAPI enum refinements retain inherited storage and validate allowed values`(
    composed: Boolean,
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/inherited-enum.yaml") asyncApiUri: URI,
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") openApiUri: URI,
  ) {
    val sources = if (composed) listOf(openApiUri, asyncApiUri) else listOf(asyncApiUri)
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(sources))
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("InheritedEnumTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest

      final class InheritedEnumTests: XCTestCase {
        func testInheritedStorageAndRequiredness() throws {
          let types: [EventType] = try [AlphaEvent(type: .alpha).type, BetaEvent(type: .beta).type,
                                   AlphaLeafEvent(type: .alpha).type, ReferencedAlphaEvent(type: .alpha).type]
          XCTAssertEqual(types, [.alpha, .beta, .alpha, .alpha])
          func check<T: Codable>(_ type: T.Type, value: String, invalidValue: String) throws -> T {
            let data = Data("{\"type\":\"\(value)\"}".utf8)
            let decoded = try JSONDecoder().decode(type, from: data)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? NSDictionary,
                           try JSONSerialization.jsonObject(with: data) as? NSDictionary)
            for json in ["{}", #"{"type":null}"#, "{\"type\":\"\(invalidValue)\"}"] {
              XCTAssertThrowsError(try JSONDecoder().decode(type, from: Data(json.utf8)))
            }
            return decoded
          }
          XCTAssertEqual(try check(AlphaEvent.self, value: "alpha", invalidValue: "beta").type, .alpha)
          XCTAssertEqual(try check(AlphaLeafEvent.self, value: "alpha", invalidValue: "beta").type, .alpha)
          XCTAssertEqual(try check(ReferencedAlphaEvent.self, value: "alpha", invalidValue: "beta").type, .alpha)
          XCTAssertEqual(try check(BetaEvent.self, value: "beta", invalidValue: "alpha").type, .beta)
          for value in ["alpha", "beta"] {
            let data = Data("{\"type\":\"\(value)\"}".utf8)
            XCTAssertEqual(try JSONDecoder().decode(BaseEvent.self, from: data).type.rawValue, value)
          }
        }

        func testOptionalWireProperty() throws {
          let decoder = JSONDecoder()
          XCTAssertNil(try decoder.decode(OptionalAlphaEvent.self, from: Data("{}".utf8)).eventType)
          let data = Data(#"{"event-type":"alpha"}"#.utf8)
          let decoded = try decoder.decode(OptionalAlphaEvent.self, from: data)
          let canonical: EventType? = decoded.eventType
          XCTAssertEqual(canonical, .alpha)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? NSDictionary,
                         try JSONSerialization.jsonObject(with: data) as? NSDictionary)
          XCTAssertThrowsError(try decoder.decode(OptionalAlphaEvent.self, from: Data(#"{"event-type":"beta"}"#.utf8)))
        }

        func testDiscriminatorMapping() throws {
          let decoder = JSONDecoder()
          for value in ["alpha", "beta"] {
            let data = Data("{\"type\":\"\(value)\"}".utf8)
            let decoded = try decoder.decode(MappedEvent.self, from: data)
            switch decoded {
            case .mappedAlphaEvent(let event):
              XCTAssertEqual(value, "alpha")
              XCTAssertEqual(event.type, .alpha)
            case .mappedBetaEvent(let event):
              XCTAssertEqual(value, "beta")
              XCTAssertEqual(event.type, .beta)
            }
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? NSDictionary,
                           try JSONSerialization.jsonObject(with: data) as? NSDictionary)
          }
          for json in ["{}", #"{"type":null}"#, #"{"type":"unknown"}"#] {
            XCTAssertThrowsError(try decoder.decode(MappedEvent.self, from: Data(json.utf8)))
          }
        }

        func testRecursiveRefinement() throws {
          func requireSendable<T: Sendable>(_ value: T) {}
          let decoder = JSONDecoder()
          let data = Data(#"{"type":"alpha","next":{"type":"alpha"}}"#.utf8)
          let decoded = try decoder.decode(RecursiveAlphaEvent.self, from: data)
          let base: RecursiveBaseEvent = decoded
          let canonical: EventType = decoded.type
          XCTAssertEqual(canonical, .alpha)
          XCTAssertEqual(base.type, .alpha)
          XCTAssertEqual(decoded.next?.type, .alpha)
          requireSendable(decoded)
          requireSendable(try decoder.decode(RecursiveBaseEvent.self, from: data))
          XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? NSDictionary,
                         try JSONSerialization.jsonObject(with: data) as? NSDictionary)
          XCTAssertThrowsError(try decoder.decode(RecursiveAlphaEvent.self,
            from: Data(#"{"type":"alpha","next":{"type":"beta"}}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  @Tag("models")
  fun `generates tolerant enums with raw-value associated fallback cases`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/ir/tolerant-enum.raml") ramlUri: URI,
    @ResourceUri("openapi/ir/tolerant-enum-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/tolerant-enum.yaml") asyncApiUri: URI,
  ) {
    val composedApi =
      GeneratedApi(
        name = "Tolerant Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "TaskState",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("pending", "running", "unknown"),
              unknownValue = "unknown",
            ),
          ),
      )
    val apis =
      listOf(
        RamlToGeneratedApi().convert(TestAPIProcessing.process(ramlUri)),
        OpenApiToGeneratedApi().convert(openApiUri),
        AsyncApiToGeneratedApi().convertFragment(asyncApiUri).api,
        GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composedApi)),
      )

    apis.forEach { api ->
      val typeRegistry = SwiftTypeRegistry(setOf())
      SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
        .generateServiceTypes()
      assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))
    }

    val typeRegistry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(composedApi, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()
    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("TolerantEnumTests.swift"),
      """
      import XCTest
      @testable import SundayGenTest

      final class TolerantEnumTests: XCTestCase {

        func testEqualityAndHashing() {
          XCTAssertEqual(TaskState.pending, .pending)
          XCTAssertNotEqual(TaskState.unknown("future"), .unknown("other"))

          let states: Set<TaskState> = [.pending, .unknown("future"), .unknown("future")]
          XCTAssertEqual(states.count, 2)

          let grouped = Dictionary(grouping: ["first", "second"]) { _ in TaskState.unknown("future") }
          XCTAssertEqual(grouped[.unknown("future")], ["first", "second"])
        }

        func testAllCasesContainsOnlyKnownValues() {
          XCTAssertEqual(TaskState.allCases, [.pending, .running])
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/TaskState.swift")
    assertTrue(
      source.contains("public enum TaskState : CaseIterable, Codable, CustomStringConvertible, Equatable, Hashable,"),
      source,
    )
    assertTrue(source.contains("Sendable, ModelValidatable {"), source)
    assertTrue(source.contains("return [.pending, .running]"), source)
    assertTrue(source.contains("case unknown(String)"), source)
    assertTrue(source.contains("default: self = .unknown(rawValue)"), source)
    assertTrue(source.contains("case .unknown(let rawValue): return rawValue"), source)
    assertTrue(
      source.contains("public var description: String {\n    return rawValue\n  }"),
      source,
    )
    assertTrue(source.contains("try container.encode(rawValue)"), source)
  }

  @Test
  @Tag("models")
  fun `generates tolerant discriminator hierarchy fallbacks`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Jobs API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "JobPhase",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("started", "paused", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "JobProgress",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("jobId", GeneratedTypeRef.scalar("string"), required = true),
                ),
              discriminator = "phase",
              discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
            ),
            GeneratedModel(
              name = "JobStarted",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("JobProgress")),
              discriminatorValue = "started",
              properties =
                listOf(
                  GeneratedModelProperty("taskCount", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "JobPaused",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorValue = "paused",
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("reason", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "JobEvent",
              kind = GeneratedModel.Kind.UNION,
              aliases = listOf(GeneratedTypeRef.named("JobStarted"), GeneratedTypeRef.named("JobPaused")),
              discriminator = "phase",
              discriminatorMappings =
                mapOf(
                  "started" to GeneratedTypeRef.named("JobStarted"),
                  "paused" to GeneratedTypeRef.named("JobPaused"),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobProgressUnknown.swift")
    val referenceSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobProgressRef.swift")
    val unionSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobEvent.swift")
    assertTrue(fallbackSource.contains("public let rawBody: [String : AnyValue]"), fallbackSource)
    assertTrue(fallbackSource.contains("AdditionalPropertyValue(value: value)"), fallbackSource)
    assertTrue(referenceSource.contains("case unknown(JobProgressUnknown)"), referenceSource)
    assertTrue(
      referenceSource.contains("self = .unknown(try JobProgressUnknown(from: decoder))"),
      referenceSource,
    )
    assertTrue(unionSource.contains("case unknown(JobEventUnknown)"), unionSource)
  }

  @Test
  @Tag("events")
  fun `generates tolerant typed event envelope fallbacks`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Events API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EventType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("created", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              properties =
                listOf(
                  GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
                ),
              discriminatorMappings = mapOf("created" to GeneratedTypeRef.named("CreatedData")),
            ),
            GeneratedModel(
              name = "CreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "created",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EventType"), required = true),
                  GeneratedModelProperty(
                    "requiredNullable",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = true,
                  ),
                  GeneratedModelProperty("optionalNullable", GeneratedTypeRef.scalar("string", nullable = true)),
                  GeneratedModelProperty(
                    "optionalText",
                    GeneratedTypeRef.scalar("string"),
                    validation =
                      mapOf(
                        "minLength" to "2",
                      ),
                  ),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("EventNullabilityTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class EventNullabilityTests: XCTestCase {
        func testKnownAndFallbackEvents() throws {
          for kind in ["created", "future"] {
            let base: [String: Any] = [
              "type": kind, "data": ["version": 1, "name": "test", "future": ["nested": [NSNull(), true]]],
              "requiredNullable": NSNull(), "optionalNullable": NSNull(),
              "future": ["nested": ["value": NSNull()]]
            ]
            var populated = base
            populated["optionalText"] = "ok"
            for wire in [base, populated] {
              let data = try JSONSerialization.data(withJSONObject: wire)
              let event = try JSONDecoder().decode(EventEnvelope.self, from: data)
              XCTAssertTrue(event.isValid(.response))
              XCTAssertEqual(event.isValid(.request), kind == "created")
              XCTAssertTrue(EventEnvelopeValidation.isValid(event, .response))
              switch event {
              case .created(let value):
                XCTAssertTrue(value.isValid(.request))
                try EventEnvelope.CreatedEvent.Validation.validate(value, .request)
              case .unknown(let value):
                XCTAssertFalse(value.isValid(.request))
                XCTAssertTrue(EventEnvelope.UnknownEvent.Validation.isValid(value, .response))
              }
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(event)) as! NSDictionary
              XCTAssertEqual(encoded, wire as NSDictionary)
            }
            for invalid in [NSNull(), "x"] as [Any] {
              var malformed = base
              malformed["optionalText"] = invalid
              XCTAssertThrowsError(try JSONDecoder().decode(EventEnvelope.self,
                from: JSONSerialization.data(withJSONObject: malformed)))
            }
          }
          let payload = CreatedData(version: 1, name: "test")
          XCTAssertThrowsError(try EventEnvelope.CreatedEvent(requiredNullable: nil,
            optionalNullable: nil, optionalText: "x", data: payload))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))

    val envelopeSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Events/EventEnvelope.swift")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Events/EventDataUnknown.swift")
    val validatorSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Events/EventEnvelopeValidation.swift",
      )
    assertTrue(validatorSource.contains("ModelObjectValidation(fields:"), validatorSource)
    assertFalse(envelopeSource.contains("AdditionalPropertiesValidator"), envelopeSource)
    assertTrue(envelopeSource.contains("case unknown(UnknownEvent)"), envelopeSource)
    assertTrue(envelopeSource.contains("public let data: EventDataUnknown"), envelopeSource)
    assertTrue(envelopeSource.contains("self = .unknown(try UnknownEvent(from: decoder))"), envelopeSource)
    assertTrue(fallbackSource.contains("public let version: Int"), fallbackSource)
    assertTrue(fallbackSource.contains("public let rawBody: [String : AnyValue]"), fallbackSource)
  }

  @Test
  @Tag("models")
  fun `rejects invalid explicit Swift enum case names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("wire"),
              enumValueNames = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '123'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid case name '123'"), error.message)
  }
}
