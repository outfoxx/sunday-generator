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

import io.outfoxx.sunday.generator.swift.SwiftSundayGenerator
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift/Sunday] [RAML] Response Problems Test")
class ResponseProblemsTest {

  @Test
  fun `test API problem registration`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
        import Sunday

        public class API {

          public let requestFactory: RequestFactory
          public let defaultContentTypes: [MediaType]
          public let defaultAcceptTypes: [MediaType]

          public init(
            requestFactory: RequestFactory,
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
            requestFactory.registerProblem(uri: "http://example.com/invalid_id", type: InvalidIdProblem.self)
            requestFactory.registerProblem(uri: "http://example.com/test_not_found", type: TestNotFoundProblem.self)
          }

          func fetchTest() -> RequestResultPublisher<Test?> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test API problem registration when no problems`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
        import Sunday

        public class API {

          public let requestFactory: RequestFactory
          public let defaultContentTypes: [MediaType]
          public let defaultAcceptTypes: [MediaType]

          public init(
            requestFactory: RequestFactory,
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          func fetchTest() -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://api.example.com/api/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://errors.example.com/docs/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/api/errors/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }
}
