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
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedService
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
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] ModelInheritance Test")
@Tag("models")
class SwiftSundayIrModelInheritanceTest : SwiftSundayIrTestSupport() {

  @Test
  @Tag("requests")
  fun `generates sendable request body types for inherited and discriminated models`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "NarrativeService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "updateScript",
                    method = "PATCH",
                    path = "/scripts/{scriptId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("ScriptUpdate")),
                  ),
                  GeneratedOperation(
                    id = "updateEntity",
                    method = "PATCH",
                    path = "/entities/{entityId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("EntityUpdate")),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Update",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ScriptUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Update")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "EntityKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("scene"),
            ),
            GeneratedModel(
              name = "EntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("EntityKind"), required = true),
                ),
            ),
            GeneratedModel(
              name = "SceneEntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityUpdate")),
              discriminatorValue = "scene",
              properties =
                listOf(
                  GeneratedModelProperty("sceneId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }
    val scriptUpdateSource =
      buildString {
        FileSpec
          .get("", findType("ScriptUpdate", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntityUpdateRef", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      serviceSource.contains(
        "public func updateScript(body: ScriptUpdate) throws -> Sunday.Operation<ScriptUpdate, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      serviceSource.contains(
        "public func updateEntity(body: EntityUpdateRef) throws -> Sunday.Operation<EntityUpdateRef, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      scriptUpdateSource.contains("public struct ScriptUpdate : Codable, CustomDebugStringConvertible, Sendable"),
      scriptUpdateSource,
    )
    assertTrue(scriptUpdateSource.contains("public let version: Int"), scriptUpdateSource)
    assertTrue(scriptUpdateSource.contains("public let title: String?"), scriptUpdateSource)
    assertTrue(
      entityRefSource.contains("public enum EntityUpdateRef : Codable, CustomDebugStringConvertible, Sendable"),
    )
  }

  @Test
  fun `uses reference models for recursive Swift object graphs`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Recursive API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EntityType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("location", "character"),
            ),
            GeneratedModel(
              name = "EntitySummary",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityType"), required = true),
                ),
            ),
            GeneratedModel(
              name = "LocationSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "location",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "parent",
                    GeneratedTypeRef.named("LocationSummary").copy(nullable = true),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "CharacterSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "character",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EntityList",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "items",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.named("EntitySummary")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "EntityMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.named("EntitySummary")),
            ),
            GeneratedModel(
              name = "EntityMapHolder",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("entries", GeneratedTypeRef.named("EntityMap"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val locationSource =
      buildString {
        FileSpec
          .get("", findType("LocationSummary", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntitySummaryRef", builtTypes))
          .writeTo(this)
      }
    val listSource =
      buildString {
        FileSpec
          .get("", findType("EntityList", builtTypes))
          .writeTo(this)
      }
    val mapHolderSource =
      buildString {
        FileSpec
          .get("", findType("EntityMapHolder", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(locationSource.contains("public final class LocationSummary : EntitySummary"), locationSource)
    assertTrue(locationSource.contains("public let parent: LocationSummary?"), locationSource)
    assertTrue(locationSource.contains("parent: LocationSummary? = nil"), locationSource)
    assertTrue(
      entityRefSource.contains(
        "EntitySummaryValidation.isValid(normalized: context.originalValue!, .response, context: &context)",
      ),
      entityRefSource,
    )
    assertTrue(entityRefSource.contains("self = .location(try LocationSummary(from: decoder))"), entityRefSource)
    assertTrue(listSource.contains("public let items: [EntitySummaryRef]"), listSource)
    assertTrue(mapHolderSource.contains("public let entries: [String : EntitySummaryRef]"), mapHolderSource)
  }

  @Test
  fun `distinguishes value and reference models for OpenAPI allOf inheritance`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/value-inheritance-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val summarySource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphSummary.swift")
    val detailsSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphDetails.swift")
    val listSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphList.swift")
    val recursiveParentSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RecursiveParent.swift")
    val recursiveChildSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RecursiveChild.swift")
    assertTrue(
      summarySource.contains(
        "public struct RenderGraphSummary : Codable, CustomDebugStringConvertible, Sendable",
      ),
      summarySource,
    )
    assertTrue(
      detailsSource.contains(
        "public struct RenderGraphDetails : Codable, CustomDebugStringConvertible, Sendable",
      ),
      detailsSource,
    )
    assertTrue(detailsSource.contains("public let graphJobId: String"), detailsSource)
    assertTrue(detailsSource.contains("public let tasks: [String]"), detailsSource)
    assertTrue(listSource.contains("public let items: [RenderGraphSummary]"), listSource)
    assertTrue(recursiveParentSource.contains("public class RecursiveParent"), recursiveParentSource)
    assertTrue(recursiveParentSource.contains("public let child: RecursiveChild?"), recursiveParentSource)
    assertTrue(
      recursiveChildSource.contains("public final class RecursiveChild : RecursiveParent"),
      recursiveChildSource,
    )
  }

  @Test
  fun `generates externally discriminated shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "External Discriminator API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Parent",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              externallyDiscriminated = true,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Cat",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "cat",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Dog",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "dog",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Envelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "payload",
                    GeneratedTypeRef.named("Parent"),
                    required = true,
                    externalDiscriminator = "kind",
                  ),
                ),
            ),
          ),
      )

    val envelope = api.models.last()
    val optionalEnvelope =
      envelope.copy(
        name = "OptionalEnvelope",
        properties =
          envelope.properties.map {
            if (it.externalDiscriminator !=
              null
            ) {
              it.copy(required = false)
            } else {
              it
            }
          },
      )
    val nullableEnvelope =
      envelope.copy(
        name = "NullableEnvelope",
        properties =
          envelope.properties.map {
            if (it.externalDiscriminator !=
              null
            ) {
              it.copy(type = it.type.copy(nullable = true))
            } else {
              it
            }
          },
      )
    SwiftSundayIrGenerator(
      api.copy(models = api.models + listOf(optionalEnvelope, nullableEnvelope)),
      typeRegistry,
      swiftSundayTestOptions,
    ).generateServiceTypes()

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ExternalDiscriminatorValidationTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ExternalDiscriminatorValidationTests: XCTestCase {
        func testMatchingPayloadUsesConcreteEncoder() throws {
          let envelope = try Envelope(kind: "cat", payload: Cat(name: "Kit"))
          XCTAssertTrue(envelope.isValid(.request))
          XCTAssertTrue(envelope.isValid(.response))
          try envelope.validate(.request)
          let wire = try JSONEncoder().encode(envelope)
          let decoded = try JSONDecoder().decode(Envelope.self, from: wire)
          XCTAssertEqual((decoded.payload as? Cat)?.name, "Kit")
          XCTAssertEqual(decoded.kind, "cat")
        }
        func testOptionalAndNullablePayloadsParticipateOnlyWhenPresent() throws {
          let optional = try OptionalEnvelope(kind: "cat", payload: Cat(name: "Kit"))
          XCTAssertNoThrow(try JSONDecoder().decode(OptionalEnvelope.self, from: JSONEncoder().encode(optional)))
          for kind in ["cat", "future"] {
            let omitted = try OptionalEnvelope(kind: kind, payload: nil)
            XCTAssertTrue(omitted.isValid(.request))
            XCTAssertNoThrow(try JSONDecoder().decode(OptionalEnvelope.self, from: JSONEncoder().encode(omitted)))
            let nullable = try NullableEnvelope(kind: kind, payload: nil)
            XCTAssertTrue(nullable.isValid(.request))
            XCTAssertNoThrow(try JSONDecoder().decode(NullableEnvelope.self, from: JSONEncoder().encode(nullable)))
          }
          XCTAssertThrowsError(try OptionalEnvelope(kind: "cat", payload: Dog(name: "Rex")))
          XCTAssertThrowsError(try NullableEnvelope(kind: "dog", payload: Cat(name: "Kit")))
          XCTAssertThrowsError(try JSONDecoder().decode(OptionalEnvelope.self, from:
            Data(#"{"kind":"cat","payload":null}"#.utf8)))
          XCTAssertThrowsError(try JSONDecoder().decode(NullableEnvelope.self, from:
            Data(#"{"kind":"cat"}"#.utf8)))
        }
        func testMismatchedAndUnsupportedDiscriminatorsRejectBeforeEncoding() throws {
          for discriminator in ["cat", "future"] {
            XCTAssertThrowsError(try Envelope(kind: discriminator, payload: Dog(name: "Rex"))) { error in
              guard let failure = error as? ModelValidationError else { return XCTFail("Unexpected error: \(error)") }
              XCTAssertEqual(failure.diagnostics.map(\.reason), [.discriminator])
              XCTAssertEqual(failure.diagnostics.map(\.jsonPointer), ["/payload"])
            }
          }
          XCTAssertThrowsError(try JSONDecoder().decode(Envelope.self, from:
            Data(#"{"kind":"cat","payload":{}}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val parentSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Parent.swift")
    val envelopeSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Envelope.swift")
    val validatorSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/EnvelopeValidation.swift",
      )
    assertTrue(validatorSource.contains("ModelObjectValidation(fields:"), validatorSource)
    assertFalse(envelopeSource.contains("AdditionalPropertiesValidator"), envelopeSource)
    assertTrue(parentSource.contains("public protocol Parent"), parentSource)
    assertFalse(parentSource.contains("enum AnyRef"), parentSource)
    assertTrue(envelopeSource.contains("public struct Envelope"), envelopeSource)
    assertTrue(envelopeSource.contains("public let payload: Parent"), envelopeSource)
    assertTrue(envelopeSource.contains("switch self.kind"), envelopeSource)
    assertTrue(envelopeSource.contains("case \"cat\":"), envelopeSource)
    assertTrue(
      envelopeSource.contains("self.payload = try container.decode(Cat.self, forKey: .payload)"),
      envelopeSource,
    )
    assertTrue(envelopeSource.contains("case \"dog\":"), envelopeSource)
    assertTrue(envelopeSource.contains("try container.encode(self.payload, forKey: .payload)"), envelopeSource)
    assertFalse(envelopeSource.contains("as!"), envelopeSource)
  }

  @Test
  fun `generates inherited discriminated model snapshots from IR with existing Swift output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertTrue(compileTypes(compiler, builtTypes))
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output.swift",
      buildString {
        FileSpec
          .get("", findType("Parent", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output2.swift",
      buildString {
        FileSpec
          .get("", findType("Child1", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output3.swift",
      buildString {
        FileSpec
          .get("", findType("Child2", builtTypes))
          .writeTo(this)
      },
    )
  }

  @Test
  @Tag("requests")
  fun `generates inherited path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-inherited-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates Sendable references for AsyncAPI discriminated base models`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/discriminated-base-sendable-regression.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val narrativeChangeEventSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeChangeEvent", builtTypes))
          .writeTo(this)
      }
    val narrativeChangeEventRefSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeChangeEventRef", builtTypes))
          .writeTo(this)
      }
    val scriptChangeEventSource =
      buildString {
        FileSpec
          .get("", findType("ScriptChangeEvent", builtTypes))
          .writeTo(this)
      }
    val sceneUpdatedSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeIqSceneUpdatedData", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      narrativeChangeEventSource.contains(
        "public protocol NarrativeChangeEvent : Codable, CustomDebugStringConvertible, Sendable",
      ),
      narrativeChangeEventSource,
    )
    assertFalse(narrativeChangeEventSource.contains("public class NarrativeChangeEvent"), narrativeChangeEventSource)
    assertTrue(
      narrativeChangeEventRefSource.contains(
        "public enum NarrativeChangeEventRef : Codable, CustomDebugStringConvertible, Sendable",
      ),
      narrativeChangeEventRefSource,
    )
    assertTrue(narrativeChangeEventRefSource.contains("case script(ScriptChangeEvent)"))
    assertTrue(narrativeChangeEventRefSource.contains("case scene(SceneChangeEvent)"))
    assertTrue(
      scriptChangeEventSource.contains("public struct ScriptChangeEvent : NarrativeChangeEvent"),
      scriptChangeEventSource,
    )
    assertTrue(sceneUpdatedSource.contains("public let change: NarrativeChangeEventRef"), sceneUpdatedSource)
  }

  @Test
  fun `filters inherited properties from Swift model subclasses`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = false),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                ),
            ),
            GeneratedModel(
              name = "BadRequest",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Conflict",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                ),
            ),
            GeneratedModel(
              name = "GraphsProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "RepoNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("GraphsProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("", findType("HttpProblem", builtTypes))
          .writeTo(this)
      }
    val source =
      buildString {
        FileSpec
          .get("", findType("BadRequest", builtTypes))
          .writeTo(this)
      }
    val graphsSource =
      buildString {
        FileSpec
          .get("", findType("GraphsProblem", builtTypes))
          .writeTo(this)
      }
    val repoNotFoundSource =
      buildString {
        FileSpec
          .get("", findType("RepoNotFoundProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(rootSource.contains("public protocol HttpProblem : Problem"), rootSource)
    assertFalse(rootSource.contains("public class HttpProblem"), rootSource)
    assertTrue(source.contains("public struct BadRequest : HttpProblem"), source)
    assertTrue(source.contains("public let type: URL"), source)
    assertTrue(source.contains("public let title: String"), source)
    assertTrue(source.contains("public let status: Int"), source)
    assertTrue(source.contains("public let code: String"), source)
    assertTrue(graphsSource.contains("public protocol GraphsProblem : Problem"), graphsSource)
    assertTrue(graphsSource.contains("var code: String { get }"), graphsSource)
    assertTrue(repoNotFoundSource.contains("public struct RepoNotFoundProblem : GraphsProblem"), repoNotFoundSource)
    assertFalse(repoNotFoundSource.contains("override"), repoNotFoundSource)
    assertTrue(repoNotFoundSource.contains("public let parameters: [String : AnyValue]?"), repoNotFoundSource)
  }
}
