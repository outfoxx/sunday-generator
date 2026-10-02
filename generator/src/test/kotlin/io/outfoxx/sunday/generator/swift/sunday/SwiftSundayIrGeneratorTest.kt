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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedDocumentation
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateSunday
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Frontend Test")
class SwiftSundayIrGeneratorTest : SwiftSundayIrTestSupport() {

  @Test
  fun `compiles remote schema resources`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val api = fixture.export(directory, OpenApiReferenceDocuments.sdkCompatibilityDecimalDefaults)
      generateSwiftSundayFiles(compiler, api)
      Files.createDirectories(compiler.testsDir)
      Files.writeString(
        compiler.testsDir.resolve("NullUnionTests.swift"),
        """
        import Foundation
        import XCTest
        @testable import SundayGenTest

        final class NullUnionTests: XCTestCase {
          func testSdkContracts() throws {
            let decoder = JSONDecoder()
            let mappedPayload = Data(#"{"kind":"cat","name":"Mittens"}"#.utf8)
            let mapped = try decoder.decode(SdkMappedPetRef.self, from: mappedPayload)
            let mappedParent: any SdkMappedCat = try XCTUnwrap(mapped.value as? SdkWrappedCat)
            XCTAssertEqual(mappedParent.name, "Mittens")
            let mappedData = try JSONEncoder().encode(mapped)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: mappedData) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: mappedPayload) as! NSDictionary)
            _ = try decoder.decode(SdkMappedPetRef.self, from: mappedData)
            let aliasPayload = Data(#"{"label":"base","count":2,"extra":"child"}"#.utf8)
            let aliasChild = try decoder.decode(SdkAliasChild.self, from: aliasPayload)
            XCTAssertEqual(aliasChild.label, "base")
            XCTAssertEqual(aliasChild.extra, "child")
            XCTAssertEqual(try SdkAliasBase(label: aliasChild.label, count: aliasChild.count).count, 2)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(aliasChild)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: aliasPayload) as! NSDictionary)
            XCTAssertThrowsError(try decoder.decode(SdkAliasChild.self, from: Data(#"{"label":"base","count":0,"extra":"child"}"#.utf8)))
            let multiPayload = Data(#"{"a":"first","b":"second","count":2,"state":"b"}"#.utf8)
            let multi = try decoder.decode(SdkMultiChild.self, from: multiPayload)
            let reversed = try decoder.decode(SdkMultiReversed.self, from: multiPayload)
            XCTAssertEqual(multi.a, "first")
            XCTAssertEqual(multi.b, "second")
            XCTAssertEqual(reversed.a, "first")
            XCTAssertEqual(reversed.b, "second")
            for data in [try JSONEncoder().encode(multi), try JSONEncoder().encode(reversed)] {
              XCTAssertEqual(try JSONSerialization.jsonObject(with: data) as! NSDictionary,
                             try JSONSerialization.jsonObject(with: multiPayload) as! NSDictionary)
            }
            XCTAssertEqual(try decoder.decode(SdkMultiChild.self, from: Data(#"{"a":"first","b":"second"}"#.utf8)).count, 2)
            for invalid in [#"{"a":"first","b":"second","count":0}"#, #"{"a":"first","b":"second","state":"a"}"#] {
              XCTAssertThrowsError(try decoder.decode(SdkMultiChild.self, from: Data(invalid.utf8)))
              XCTAssertThrowsError(try decoder.decode(SdkMultiReversed.self, from: Data(invalid.utf8)))
            }
            let intersected = try decoder.decode(SdkConflictingChild.self, from: Data(#"{"status":"b"}"#.utf8))
            XCTAssertEqual(intersected.status, "b")
            XCTAssertThrowsError(try decoder.decode(SdkConflictingChild.self, from: Data(#"{"status":"a"}"#.utf8)))
            let envelopeDefaults = try decoder.decode(SdkEnvelope.self, from: Data("{}".utf8))
            XCTAssertEqual(envelopeDefaults.uuid, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            XCTAssertEqual(envelopeDefaults.timestamp?.timeIntervalSince1970, 1767225600.125)
            let midnight = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-09-14T00:00:00Z"))
            _ = try SdkTemporalChild(timestamp: midnight)
            XCTAssertThrowsError(try SdkTemporalChild(timestamp: midnight.addingTimeInterval(1)))
            _ = try SdkByteRestrictions(data: Data("Hi".utf8), encoded: Data("Hi".utf8))
            XCTAssertThrowsError(try SdkByteRestrictions(data: Data("Hi".utf8), encoded: Data("No".utf8)))
            XCTAssertNil(try SdkIntegerChild().count)
            for value in [try SdkIntegerChild(count: 1), try decoder.decode(SdkIntegerChild.self, from: Data("{}".utf8))] {
              XCTAssertEqual(value.count, 1)
              XCTAssertEqual(SdkIntegerBase(count: value.count).count, 1)
            }
            for (state, field) in [("rendered", "versionId"), ("refused", "refusalReason")] {
              let payload = ["currentAsset": ["state": state, field: "value"]]
              let data = try JSONSerialization.data(withJSONObject: payload)
              let entity = try decoder.decode(EntityDetails.self, from: data)
              let asset: (any CurrentAsset)? = entity.currentAsset?.value
              if state == "rendered" {
                XCTAssertEqual((asset as? RenderedAsset)?.versionId, "value")
              } else {
                XCTAssertEqual((asset as? RefusedAsset)?.refusalReason, "value")
              }
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(entity)) as! NSDictionary
              XCTAssertEqual(encoded, payload as NSDictionary)
            }
            let inline = try decoder.decode(SdkInlineChild.self, from: Data(#"{"detail":{"value":"value"},"selection":"text","tags":["b","a"]}"#.utf8))
            let inlineParent = SdkInlineBase(detail: inline.detail, selection: inline.selection, tags: inline.tags)
            XCTAssertEqual(inlineParent.detail?.value, "value")
            XCTAssertEqual(inline.tags, ["b", "a"])
            XCTAssertThrowsError(try decoder.decode(SdkInlineChild.self, from: Data(#"{"detail":{},"selection":"text","tags":["a","a"]}"#.utf8)))
            let event = try decoder.decode(CharacterChangeEvent.self, from: Data(#"{"type":"character","id":"one"}"#.utf8))
            let eventType: NarrativeChangeEventType = event.type
            XCTAssertEqual(eventType.rawValue, "character")
            XCTAssertEqual(event.count, 20)
            for json in [#"{"type":"prop","id":"one"}"#, #"{"type":"future","id":"one"}"#,
                         #"{"type":"character","id":"one","count":0}"#, #"{"type":"character","id":"one","count":21}"#] {
              XCTAssertThrowsError(try decoder.decode(CharacterChangeEvent.self, from: Data(json.utf8)))
            }
            let edit = try decoder.decode(AddFactOp.self, from: Data(#"{"op":"addFact","value":"fact"}"#.utf8))
            let parent: any FactEditOp = edit
            XCTAssertEqual(parent.op, "addFact")
            let problem = try decoder.decode(BadRequestProblem.self, from: Data(#"{"type":"about:blank","title":"Bad request","status":400}"#.utf8))
            XCTAssertEqual(problem.detail, "Invalid request")
            for status in ["401", "null", "false"] {
              XCTAssertThrowsError(try decoder.decode(BadRequestProblem.self, from: Data("{\"status\":\(status)}".utf8)))
            }
            let defaults = try decoder.decode(ScalarRestrictions.self, from: Data(#"{"value":"present"}"#.utf8))
            XCTAssertEqual(defaults.zero, 0)
            XCTAssertEqual(defaults.flag, false)
            XCTAssertEqual(defaults.mode?.rawValue, "character")
            for choice in ["null", "0", "false"] {
              _ = try decoder.decode(ScalarRestrictions.self, from: Data("{\"value\":\"present\",\"choice\":\(choice)}".utf8))
            }
            for json in [#"{}"#, #"{"value":null}"#, #"{"value":""}"#, #"{"value":"present","zero":1}"#,
                         #"{"value":"present","flag":true}"#, #"{"value":"present","choice":"0"}"#,
                         #"{"value":"present","choice":true}"#, #"{"value":"present","mode":"future"}"#] {
              XCTAssertThrowsError(try decoder.decode(ScalarRestrictions.self, from: Data(json.utf8)), json)
            }
          }

          func testDocumentaryInheritance() throws {
            let child = DocumentedRecord(id: "one", detail: "detail")
            XCTAssertEqual(child.id, BaseRecord(id: "one").id)
            let bytes = try JSONEncoder().encode(child)
            XCTAssertEqual(try JSONDecoder().decode(DocumentedRecord.self, from: bytes).id, "one")
            for payload in [nil, "value"] as [String?] {
              let documented = DocumentedRecord(id: "one", payload: payload)
              let parent = BaseRecord(id: "one", payload: payload)
              XCTAssertEqual(documented.payload, parent.payload)
              let encoded = try JSONEncoder().encode(documented)
              XCTAssertEqual(try JSONDecoder().decode(DocumentedRecord.self, from: encoded).payload, payload)
            }
            for next in [nil, RecordNode(id: "two", next: RecordNode(id: "three"))] as [RecordNode?] {
              let documented = DocumentedRecord(id: "one", next: next)
              let encoded = try JSONEncoder().encode(documented)
              let decoded = try JSONDecoder().decode(DocumentedRecord.self, from: encoded)
              XCTAssertEqual(decoded.next?.id, next?.id)
              XCTAssertEqual(decoded.next?.next?.id, next?.next?.id)
            }
            let recursive = DocumentedRecord(
              id: "one",
              direct: RecordNode(id: "two", direct: RecordNode(id: "three")),
              wrapped: RecordNode(id: "four", wrapped: RecordNode(id: "five"))
            )
            let recursiveBytes = try JSONEncoder().encode(recursive)
            let restored = try JSONDecoder().decode(DocumentedRecord.self, from: recursiveBytes)
            XCTAssertEqual(restored.direct?.direct?.id, "three")
            XCTAssertEqual(restored.wrapped?.wrapped?.id, "five")
            let invalid = Data(#"{"id":"one","next":42}"#.utf8)
            XCTAssertThrowsError(try JSONDecoder().decode(DocumentedRecord.self, from: invalid))
            let cat = try JSONDecoder().decode(Cat2.self, from: Data(#"{"kind":"Cat"}"#.utf8))
            let pet: any Pet = cat
            XCTAssertEqual(pet.kind, "Cat")
          }

          func testBooleanSchemas() throws {
            for value in [0, false, "value", ["nested": true]] as [Any] {
              let bytes = try JSONSerialization.data(withJSONObject: ["truth": value, "empty": value])
              let decoded = try JSONDecoder().decode(BooleanValues.self, from: bytes)
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary
              XCTAssertEqual(encoded["truth"] as? NSObject, encoded["empty"] as? NSObject)
            }
          }

          func testImplicitDiscriminatorValues() throws {
            for kind in ["Cat", "Dog"] {
              let bytes = try JSONSerialization.data(withJSONObject: ["animal": ["kind": kind]])
              let decoded = try JSONDecoder().decode(Pets.self, from: bytes)
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? [String: Any]
              XCTAssertEqual((encoded?["animal"] as? [String: Any])?["kind"] as? String, kind)
            }
            let invalid = try JSONSerialization.data(withJSONObject: ["animal": ["kind": "Cat2"]])
            XCTAssertThrowsError(try JSONDecoder().decode(Pets.self, from: invalid))
          }

          func testRelativeDiscriminatorMappings() throws {
            for animal in [["kind": "kitty", "lives": 9], ["kind": "hound", "barks": true]] as [[String: Any]] {
              let bytes = try JSONSerialization.data(withJSONObject: ["animal": animal])
              let decoded = try JSONDecoder().decode(MappedPets.self, from: bytes)
              if animal["kind"] as? String == "kitty" {
                XCTAssertEqual((decoded.animal.value as? MappedCat)?.lives, 9)
              } else {
                XCTAssertEqual((decoded.animal.value as? MappedDog)?.barks, true)
              }
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary
              XCTAssertEqual(encoded, ["animal": animal] as NSDictionary)
            }
            let invalid = Data(#"{"animal":{"kind":"MappedCat","lives":9}}"#.utf8)
            XCTAssertThrowsError(try JSONDecoder().decode(MappedPets.self, from: invalid))
          }

          func testNullabilityComposition() throws {
            let decoder = JSONDecoder()
            for values in [NSNull(), ["valid"]] as [Any] {
              let valid: [String: Any] = ["strictText": "valid", "values": values]
              let decoded = try decoder.decode(Nullability.self, from: JSONSerialization.data(withJSONObject: valid))
              XCTAssertEqual(decoded.values, values as? [String])
            }
            let invalid: [String: Any] = ["strictText": NSNull(), "values": NSNull()]
            let bytes = try JSONSerialization.data(withJSONObject: invalid)
            XCTAssertThrowsError(try decoder.decode(Nullability.self, from: bytes))
          }

          func testConstrainedNullUnions() throws {
            let valid: [String: Any] = ["address": ["street": "Main"], "text": "hello", "state": "active"]
            let decoder = JSONDecoder()
            let decoded = try decoder.decode(Restrictions.self, from: JSONSerialization.data(withJSONObject: valid))
            XCTAssertEqual(decoded.text, "hello")
            for field in valid.keys {
              for excluded in [42, NSNull()] as [Any] {
                var invalid = valid
                invalid[field] = excluded
                let bytes = try JSONSerialization.data(withJSONObject: invalid)
                XCTAssertThrowsError(try decoder.decode(Restrictions.self, from: bytes))
              }
            }
          }
        }
        """.trimIndent(),
      )
      assertTrue(compileAndTestGeneratedFiles(compiler))
      assertEquals(
        listOf(GeneratedTypeRef.named("BaseRecord")),
        api.models.single { it.name == "DocumentedRecord" }.inherits,
      )
      val record = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DocumentedRecord.swift")
      assertEquals(1, "let id:".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertEquals(1, "let payload: String?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertEquals(1, "let next: RecordNode?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      for (field in listOf("direct", "wrapped")) {
        assertEquals(1, "let $field: RecordNode?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      }
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Cat.swift").contains("unrelated"))
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Cat2.swift").contains("lives"))
      val user = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/User.swift")
      assertTrue(user.contains("Address"), user)
      assertTrue(user.contains("node: Node?"), user)
      assertTrue(user.contains("composedNode: Node?"), user)
      assertTrue(user.contains("copiedNode: Node?"), user)
      assertTrue(user.contains("maybeAddress: Address?"), user)
      assertTrue(user.contains("copiedAddress: Address?"), user)
      assertTrue(user.contains("composedAddress: Address?"), user)
      assertTrue(user.contains("UserProfile2"), user)
      assertFalse(user.contains("UserArbitrary"), user)
      assertFalse(user.contains("UserNullableArbitrary"), user)
      val profile = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserProfile.swift")
      assertTrue(profile.contains("remoteValue"), profile)
      val inlineProfile = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserProfile2.swift")
      assertTrue(inlineProfile.contains("localValue"), inlineProfile)
      val extended = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserExtendedAddress.swift")
      assertTrue(extended.contains("street"), extended)
      assertTrue(extended.contains("postalCode"), extended)
      val node = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Node.swift")
      assertTrue(node.contains("child: Node?"), node)
      val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "API.swift")
      assertTrue(service.contains("= 20"), service)
      val nullability = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Nullability.swift")
      assertTrue(nullability.contains("strictText: String"), nullability)
      assertFalse(nullability.contains("strictText: String?"), nullability)
      assertTrue(nullability.contains("values: [String]?"), nullability)
    }
  }

  @Test
  fun `Swift Sunday CLI uses IR exporter directly`() {
    val source =
      Files.readString(
        Path.of(
          "..",
          "cli",
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "swift",
          "SwiftSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("SwiftSundayIrGenerator"))
    assertFalse(source.contains("SwiftSundayGenerator("), source)
  }

  @Test
  fun `public generator generates service types from RAML through IR path`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val builtTypes = generateSunday(testUri, typeRegistry, compiler)
    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestMethodsTest/test-request-method-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `Swift Sunday generated files track Foundation imports for file payloads`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "TurnPost API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:turnpost.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "TeamsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "putTeamAvatar",
                    method = "PUT",
                    path = "/teams/{teamId}/avatar",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("application/octet-stream"),
                      ),
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()
    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Service), compiler.srcDir)

    val source = Files.readString(compiler.srcDir.resolve("TeamsAPI.swift"))
    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("body: Data"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from RAML source`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/ir/any-shapes.raml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val source = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import PotentCodables"), source)
    assertTrue(source.contains("public let value: AnyValue?"), source)
    assertTrue(source.contains("try container.decodeIfPresent(AnyValue.self, forKey: .value)"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from OpenAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/operation-surface-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files treat OpenAPI empty schemas as AnyValue`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(
      compiler,
      GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = true)).export(testUri),
    )

    val holderSource = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))
    val entityStatePropertyValueSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyValue.swift"),
      )
    val entityStatePropertyRefSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyRef.swift"),
      )
    val serviceSource = Files.readString(compiler.srcDir.resolve("NarrativeAPI.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(holderSource.contains("public let value: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let documented: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let named: AnyValue?"), holderSource)
    assertTrue(
      entityStatePropertyValueSource.contains("public struct EntityStatePropertyValue : EntityStateProperty"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyValueSource.contains("public let value: AnyValue?"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyRefSource.contains("case value(EntityStatePropertyValue)"),
      entityStatePropertyRefSource,
    )
    assertTrue(serviceSource.contains("body: AnyValue"), serviceSource)
    assertTrue(serviceSource.contains("Operation<AnyValue, AnyValue, TransportType>"), serviceSource)
  }

  @Test
  fun `Swift Sunday generated files compile from AsyncAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files compile from composed OpenAPI and AsyncAPI sources`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri)))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `generates documentation comments from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Documentation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:docs.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/repos/{projectId}",
                    documentation =
                      GeneratedDocumentation(
                        summary = "Fetch a project.",
                        description = "Returns a project visible to the `/repos/**` caller.",
                      ),
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "projectId",
                          location = GeneratedParameter.Location.PATH,
                          type = GeneratedTypeRef.scalar("string"),
                        ),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("Project"),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Project",
              kind = GeneratedModel.Kind.OBJECT,
              documentation =
                GeneratedDocumentation(
                  summary = "Project model.",
                  description = "A project in the workspace.",
                ),
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "projectId",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    documentation = GeneratedDocumentation(description = "Stable project identifier."),
                  ),
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
          .get("", findType("ProjectsAPI", builtTypes))
          .writeTo(this)
      }
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("Project", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("Fetch a project."), serviceSource)
    assertTrue(serviceSource.contains("Returns a project visible to the `/repos/ **` caller."), serviceSource)
    assertTrue(modelSource.contains("Project model."), modelSource)
    assertTrue(modelSource.contains("A project in the workspace."), modelSource)
    assertTrue(modelSource.contains("Stable project identifier."), modelSource)
  }

  @Test
  fun `generates nullify methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation-with-nullify.output.swift",
    )
  }
}
