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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] ScalarModels Test")
@Tag("models")
class SwiftSundayIrScalarModelsTest : SwiftSundayIrTestSupport() {

  @Test
  fun `integer defaults preserve exact values through aliases and inheritance`(compiler: SwiftCompiler) {
    val count = GeneratedModelProperty("count", GeneratedTypeRef.named("IntegerAlias"), defaultValue = "1.0")
    val defaults =
      GeneratedModel(
        "IntegerDefaults",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(count) +
            listOf(
              "exponent" to "1e3",
              "negativeZero" to "-0.0",
              "maximum" to "9223372036854775807.0",
              "minimum" to "-9223372036854775808.0",
            ).map { (name, value) ->
              GeneratedModelProperty(name, GeneratedTypeRef.scalar("integer"), defaultValue = value)
            } +
            listOf(
              GeneratedModelProperty("fraction", GeneratedTypeRef.scalar("number"), defaultValue = "1.25"),
              GeneratedModelProperty(
                "nullable",
                GeneratedTypeRef.scalar("integer", nullable = true),
                defaultValue = "2.0",
              ),
            ),
      )
    val child =
      GeneratedModel(
        "RefinedIntegerDefaults",
        GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named("IntegerDefaults")),
        properties =
          listOf(
            count.copy(defaultValue = "2.0", allowedValues = listOf(2), validation = mapOf("minimum" to "2")),
          ),
      )
    val api =
      GeneratedApi(
        name = "Integer defaults",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            child,
            child.copy(name = "RequiredIntegerDefaults", properties = listOf(count.copy(required = true))),
            GeneratedModel(
              "IntegerPatch",
              GeneratedModel.Kind.OBJECT,
              properties = listOf(count, defaults.properties.last()),
              patchable = true,
            ),
            defaults,
            GeneratedModel(
              "IntegerAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("IntegerValue")),
            ),
            GeneratedModel(
              "IntegerValue",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("integer")),
            ),
          ),
      )
    generateSwiftSundayFiles(compiler, api)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("IntegerDefaultsTests.swift"),
      """
      import Foundation
      import Sunday
      import XCTest
      @testable import SundayGenTest

      final class IntegerDefaultsTests: XCTestCase {
        func testExactDefaults() throws {
          let decoder = JSONDecoder()
          for value in [try decoder.decode(IntegerDefaults.self, from: Data("{}".utf8))] {
            XCTAssertEqual(value.count, 1)
            XCTAssertEqual(value.exponent, 1000)
            XCTAssertEqual(value.negativeZero, 0)
            XCTAssertEqual(value.maximum, Int.max)
            XCTAssertEqual(value.minimum, Int.min)
            XCTAssertEqual(value.fraction, 1.25)
            XCTAssertEqual(value.nullable, 2)
            let roundTrip = try decoder.decode(IntegerDefaults.self, from: JSONEncoder().encode(value))
            XCTAssertEqual(roundTrip.maximum, Int.max)
            XCTAssertEqual(roundTrip.minimum, Int.min)
          }
          for value in [try decoder.decode(RefinedIntegerDefaults.self, from: Data("{}".utf8))] {
            let parent = IntegerDefaults(count: value.count)
            XCTAssertEqual(parent.count, 2)
          }
          XCTAssertNil(IntegerDefaults().count)
          XCTAssertNil(try RefinedIntegerDefaults().count)
          XCTAssertThrowsError(try decoder.decode(RefinedIntegerDefaults.self, from: Data(#"{"count":1}"#.utf8)))
          XCTAssertThrowsError(try decoder.decode(IntegerDefaults.self, from: Data(#"{"count":null}"#.utf8)))
          let null = try decoder.decode(IntegerDefaults.self, from: Data(#"{"nullable":null}"#.utf8))
          XCTAssertEqual(null.count, 1)
          XCTAssertNil(null.nullable)
          XCTAssertNil(RequiredIntegerDefaults().count)
          XCTAssertThrowsError(try decoder.decode(RequiredIntegerDefaults.self, from: Data("{}".utf8)))
          for value in [IntegerPatch(), try decoder.decode(IntegerPatch.self, from: Data("{}".utf8))] {
            XCTAssertNil(value.count)
            XCTAssertNil(value.nullable)
            XCTAssertEqual(String(data: try JSONEncoder().encode(value), encoding: .utf8), "{}")
          }
          let deleted = try decoder.decode(IntegerPatch.self, from: Data(#"{"nullable":null}"#.utf8))
          guard case .delete? = deleted.nullable else { return XCTFail("null must remain a delete") }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `superclass decoders use the most derived typed defaults`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val schemas =
        """
        DefaultCount: {type: integer}
        DefaultIdentifier: {type: string, format: uuid}
        DefaultParent:
          type: object
          properties:
            count: {${'$'}ref: '#/components/schemas/DefaultCount', default: 1}
            added: {type: integer}
            identifier: {${'$'}ref: '#/components/schemas/DefaultIdentifier', default: '00000000-0000-0000-0000-000000000000'}
            next: {${'$'}ref: '#/components/schemas/DefaultChild'}
            other: {${'$'}ref: '#/components/schemas/DefaultParent'}
            grandchild: {${'$'}ref: '#/components/schemas/DefaultGrandchild'}
            requiredChild: {${'$'}ref: '#/components/schemas/RequiredDefaultChild'}
        DefaultChild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultParent'}]
          properties:
            count: {minimum: 2, default: 2}
            added: {minimum: 4, default: 4.0}
            identifier: {default: '00000000-0000-0000-0000-000000000001'}
        DefaultGrandchild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultChild'}]
          properties: {count: {minimum: 3, default: 3}}
        RequiredDefaultChild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultChild'}]
          required: [count]
        """.trimIndent()
      fixture.respond("/defaults.yaml", OpenApiReferenceDocuments.document("Defaults", schemas))
      val source = directory.resolve("defaults.yaml")
      Files.writeString(
        source,
        OpenApiReferenceDocuments.document(
          "Inherited defaults",
          listOf("DefaultParent", "DefaultChild", "DefaultGrandchild", "RequiredDefaultChild").joinToString("\n") {
            "$it: {${'$'}ref: '${fixture.baseUri}defaults.yaml#/components/schemas/$it'}"
          },
        ),
      )
      val options =
        GeneratedApiIrOptions(
          openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
        )
      val api = OpenApiToGeneratedApi(options).convert(source.toUri())
      generateSwiftSundayFiles(compiler, api)
      Files.createDirectories(compiler.testsDir)
      Files.writeString(
        compiler.testsDir.resolve("InheritedDefaultsTests.swift"),
        """
        import Foundation
        import XCTest
        @testable import SundayGenTest

        final class InheritedDefaultsTests: XCTestCase {
          func testDynamicDefaults() throws {
            let decoder = JSONDecoder()
            let parent = try decoder.decode(DefaultParent.self, from: Data("{}".utf8))
            XCTAssertEqual(parent.count, 1)
            XCTAssertNil(parent.added)
            XCTAssertEqual(parent.identifier, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            for child in [try decoder.decode(DefaultChild.self, from: Data("{}".utf8))] {
              let assigned: DefaultParent = child
              XCTAssertEqual(assigned.count, 2)
              XCTAssertEqual(assigned.added, 4)
              XCTAssertEqual(assigned.identifier, UUID(uuidString: "00000000-0000-0000-0000-000000000001"))
            }
            for grandchild in [try decoder.decode(DefaultGrandchild.self, from: Data("{}".utf8))] {
              XCTAssertEqual(grandchild.count, 3)
              XCTAssertEqual(grandchild.added, 4)
            }
            XCTAssertNil(try DefaultChild().count)
            XCTAssertNil(try DefaultGrandchild().count)
            let nested = try decoder.decode(DefaultGrandchild.self, from: Data(#"{"next":{},"other":{}}"#.utf8))
            XCTAssertEqual(nested.count, 3)
            XCTAssertEqual(nested.next?.count, 2)
            XCTAssertEqual(nested.other?.count, 1)
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"count":1}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"count":null}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"added":3}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(RequiredDefaultChild.self, from: Data("{}".utf8)))
            XCTAssertEqual(try decoder.decode(RequiredDefaultChild.self, from: Data(#"{"count":5}"#.utf8)).count, 5)
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"count":6,"identifier":null}"#.utf8)))
            let supplied = try decoder.decode(DefaultChild.self, from: Data(#"{"count":6}"#.utf8))
            XCTAssertEqual(supplied.count, 6)
          }
        }
        """.trimIndent(),
      )
      assertTrue(compileAndTestGeneratedFiles(compiler))
      val parent = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DefaultParent.swift")
      val child = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DefaultChild.swift")
      assertTrue(parent.contains("class var _sundayDefaultCount"), parent)
      assertTrue(child.contains("class override var _sundayDefaultCount"), child)
      assertTrue(child.contains("try super.init(from: decoder)"), child)
      assertFalse(child.contains("public let count"), child)
    }
  }

  @Test
  fun `lowers IR date scalar properties to Swift Date`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Dates API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "AuditEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("dateOnly", GeneratedTypeRef.scalar("date"), required = true),
                  GeneratedModelProperty("timeOnly", GeneratedTypeRef.scalar("time"), required = true),
                  GeneratedModelProperty("localDateTime", GeneratedTypeRef.scalar("datetime-only"), required = true),
                  GeneratedModelProperty("timestamp", GeneratedTypeRef.scalar("datetime"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("AuditEvent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("public let dateOnly: Date"), source)
    assertTrue(source.contains("public let timeOnly: Date"), source)
    assertTrue(source.contains("public let localDateTime: Date"), source)
    assertTrue(source.contains("public let timestamp: Date"), source)
  }

  @Test
  fun `lowers supported IR scalar formats to Swift Foundation types`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Formatted API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "FormattedService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getFormatted",
                    method = "GET",
                    path = "/formatted/{resourceId}",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "resourceId",
                          GeneratedParameter.Location.PATH,
                          GeneratedTypeRef.scalar("string", format = "uuid"),
                          required = true,
                        ),
                        GeneratedParameter(
                          "callbackUrl",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                        ),
                        GeneratedParameter(
                          "location",
                          GeneratedParameter.Location.HEADER,
                          GeneratedTypeRef.scalar("string", format = "uri-reference"),
                          required = true,
                          serializationName = "Location",
                        ),
                      ),
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "FormattedModel",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "absoluteUrl",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "relativeUrl",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri-reference"),
                  ),
                  GeneratedModelProperty(
                    "id",
                    GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "encoded",
                    GeneratedTypeRef.scalar("string", format = "byte"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "binary",
                    GeneratedTypeRef.scalar("string", format = "binary"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "createdAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "calendarDay",
                    GeneratedTypeRef.scalar("string", format = "date"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localTime",
                    GeneratedTypeRef.scalar("string", format = "time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localDateTime",
                    GeneratedTypeRef.scalar("string", format = "datetime-only"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("FormattedModel", builtTypes))
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(modelSource.contains("import Foundation"), modelSource)
    assertTrue(modelSource.contains("public let absoluteUrl: URL"), modelSource)
    assertTrue(modelSource.contains("public let relativeUrl: URL?"), modelSource)
    assertTrue(modelSource.contains("public let id: UUID"), modelSource)
    assertTrue(modelSource.contains("public let encoded: Data"), modelSource)
    assertTrue(modelSource.contains("public let binary: Data"), modelSource)
    assertTrue(modelSource.contains("public let createdAt: Date"), modelSource)
    assertTrue(modelSource.contains("public let calendarDay: Date"), modelSource)
    assertTrue(modelSource.contains("public let localTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let localDateTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let email: String"), modelSource)
    assertTrue(serviceSource.contains("resourceId: UUID"), serviceSource)
    assertTrue(serviceSource.contains("callbackUrl: URL? = nil"), serviceSource)
    assertTrue(serviceSource.contains("location: URL"), serviceSource)
  }

  @Test
  fun `adds Identifiable to IR models with id when default identifiable option is enabled`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      source.contains("public struct User : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      source,
    )
  }
}
