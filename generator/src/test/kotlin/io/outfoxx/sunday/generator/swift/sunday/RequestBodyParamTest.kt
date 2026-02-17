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
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift/Sunday] [RAML] Request Body Param Test")
class RequestBodyParamTest {

  @Test
  fun `test basic body parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
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
          defaultContentTypes: [MediaType] = [.json],
          defaultAcceptTypes: [MediaType] = [.json]
        ) {
          self.requestFactory = requestFactory
          self.defaultContentTypes = defaultContentTypes
          self.defaultAcceptTypes = defaultAcceptTypes
        }

        public func fetchTest(body: Test) async throws -> Test {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: nil,
            body: body,
            contentTypes: self.defaultContentTypes,
            acceptTypes: self.defaultAcceptTypes,
            headers: nil
          )
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional body parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
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
          defaultContentTypes: [MediaType] = [.json],
          defaultAcceptTypes: [MediaType] = [.json]
        ) {
          self.requestFactory = requestFactory
          self.defaultContentTypes = defaultContentTypes
          self.defaultAcceptTypes = defaultAcceptTypes
        }

        public func fetchTest(body: Test?) async throws -> Test {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: nil,
            body: body,
            contentTypes: self.defaultContentTypes,
            acceptTypes: self.defaultAcceptTypes,
            headers: nil
          )
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
      import Foundation
      import PotentCodables
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

        public func fetchTest(body: Data) async throws -> [String : AnyValue] {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: nil,
            body: body,
            contentTypes: [.octetStream],
            acceptTypes: self.defaultAcceptTypes,
            headers: nil
          )
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
