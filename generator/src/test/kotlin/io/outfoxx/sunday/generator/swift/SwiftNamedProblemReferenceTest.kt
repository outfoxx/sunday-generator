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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("responses")
class SwiftNamedProblemReferenceTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `named Problem references use strings and retain authored constraints`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve(if (frontend == "raml") "problems.raml" else "problems.yaml")
    source.writeText(
      if (frontend == "raml") {
        """
        #%RAML 1.0
        title: Named Problem references
        types:
          Uri: string
          NestedUri: Uri
          InstanceRoot: {type: Uri, minLength: 3}
          InstanceUri: InstanceRoot
          AllowedType: {type: string, enum: ['about:blank', 'https://example.com']}
          HttpProblem:
            type: object
            properties:
              type?: NestedUri
              title?: string
              status?: integer
              instance?: InstanceUri
          EnumProblem:
            type: object
            properties:
              type?: AllowedType
              title?: string
              status?: integer
          OrdinaryRecord:
            type: object
            properties:
              uri: Uri
        """.trimIndent()
      } else {
        val header =
          if (frontend == "asyncapi") "asyncapi: 3.0.0\nchannels: {}\noperations: {}" else "openapi: 3.1.0\npaths: {}"
        header + "\n" +
          """
          info: {title: Named Problem references, version: '1'}
          components:
            schemas:
              Uri: {type: string, format: uri}
              NestedUri: {${'$'}ref: '#/components/schemas/Uri'}
              InstanceRoot: {${'$'}ref: '#/components/schemas/Uri', minLength: 3}
              InstanceUri: {${'$'}ref: '#/components/schemas/InstanceRoot'}
              AllowedType: {type: string, enum: ['about:blank', 'https://example.com']}
              HttpProblem:
                type: object
                properties:
                  type: {${'$'}ref: '#/components/schemas/NestedUri'}
                  title: {type: string}
                  status: {type: integer}
                  instance: {${'$'}ref: '#/components/schemas/InstanceUri'}
              EnumProblem:
                type: object
                properties:
                  type: {${'$'}ref: '#/components/schemas/AllowedType'}
                  title: {type: string}
                  status: {type: integer}
              OrdinaryRecord:
                type: object
                required: [uri]
                properties:
                  uri: {${'$'}ref: '#/components/schemas/Uri'}
          """.trimIndent()
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      events.writeText(
        "asyncapi: 3.0.0\ninfo: {title: Named Problem references, version: '1'}\nchannels: {}\noperations: {}\n",
      )
      sources += events.toUri()
    }
    val registry = SwiftTypeRegistry(setOf())
    val exported = GeneratedApiIrExporter().export(sources)
    // Frontends differ in how they compose patterns; exercise the retained alias chain at the IR boundary.
    val api =
      exported.copy(
        models =
          exported.models.map { model ->
            when (model.name) {
              "InstanceRoot" -> model.copy(validation = model.validation + ("pattern" to "^urn:"))
              "InstanceUri" ->
                model.copy(
                  aliases = listOf(GeneratedTypeRef.named("InstanceRoot")),
                  validation = model.validation + ("pattern" to ":example"),
                )
              "HttpProblem" ->
                model.copy(
                  properties =
                    model.properties.map { property ->
                      if (property.name == "instance") {
                        property.copy(
                          type = GeneratedTypeRef.named("InstanceUri"),
                          validation = property.validation + ("pattern" to "example$"),
                        )
                      } else {
                        property
                      }
                    },
                )
              else -> model
            }
          },
      )
    SwiftSundayIrGenerator(
      api,
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("NamedProblemReferenceTests.swift").writeText(
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class NamedProblemReferenceTests: XCTestCase {
        func testProblemReferences() throws {
          let decoder = JSONDecoder()
          let encoder = JSONEncoder()
          let missing = try decoder.decode(HttpProblem.self, from: Data("{}".utf8))
          XCTAssertEqual(missing.type, "about:blank")
          for input in [#"{"type":null,"instance":null}"#, #"{"type":42,"instance":42}"#] {
            let value = try decoder.decode(HttpProblem.self, from: Data(input.utf8))
            XCTAssertEqual(value.type, missing.type)
            XCTAssertNil(value.instance)
          }
          let value = try HttpProblem(type: "", instance: "urn:example")
          let reference: String = value.type
          XCTAssertEqual(reference, "")
          XCTAssertNil(value.typeURI)
          XCTAssertEqual(try decoder.decode(HttpProblem.self, from: encoder.encode(value)).instance, "urn:example")
          XCTAssertThrowsError(try HttpProblem(instance: "x"))
          XCTAssertThrowsError(try decoder.decode(HttpProblem.self, from: Data(#"{"instance":"x"}"#.utf8)))
          for invalid in ["https:example", "urn:otherexample", "urn:example-tail"] {
            let data = try JSONSerialization.data(withJSONObject: ["instance": invalid])
            XCTAssertThrowsError(try HttpProblem(instance: invalid), invalid)
            XCTAssertThrowsError(try decoder.decode(HttpProblem.self, from: data), invalid)
          }
          for type in ["about:blank", "https://example.com"] {
            let value = try EnumProblem(type: type)
            XCTAssertEqual(try decoder.decode(EnumProblem.self, from: encoder.encode(value)).type, type)
          }
          XCTAssertThrowsError(try EnumProblem(type: "https://example.org/wrong"))
          let ordinary = OrdinaryRecord(uri: ${if (frontend == "raml") "\"https://example.com\"" else "URL(string: \"https://example.com\")!"})
          let ordinaryReference: ${if (frontend == "raml") "String" else "URL"} = ordinary.uri
          XCTAssertEqual(String(describing: ordinaryReference), "https://example.com")
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
