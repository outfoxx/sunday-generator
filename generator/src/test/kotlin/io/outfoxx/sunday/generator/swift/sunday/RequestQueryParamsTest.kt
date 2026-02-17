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
@DisplayName("[Swift/Sunday] [RAML] Request Query Params Test")
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
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
          defaultContentTypes: [MediaType] = [],
          defaultAcceptTypes: [MediaType] = [.json]
        ) {
          self.requestFactory = requestFactory
          self.defaultContentTypes = defaultContentTypes
          self.defaultAcceptTypes = defaultAcceptTypes
        }

        public func fetchTest(
          obj: Test,
          strReq: String,
          int: Int = 5
        ) async throws -> Test {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: [
              "obj": obj,
              "str-req": strReq,
              "int": int
            ],
            body: Empty.none,
            contentTypes: nil,
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
  fun `test optional query parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
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
          defaultContentTypes: [MediaType] = [],
          defaultAcceptTypes: [MediaType] = [.json]
        ) {
          self.requestFactory = requestFactory
          self.defaultContentTypes = defaultContentTypes
          self.defaultAcceptTypes = defaultAcceptTypes
        }

        public func fetchTest(
          obj: Test? = nil,
          str: String? = nil,
          int: Int? = nil,
          def1: String? = "test",
          def2: Int? = 10
        ) async throws -> Test {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: [
              "obj": obj as Any?,
              "str": str as Any?,
              "int": int as Any?,
              "def1": def1 as Any?,
              "def2": def2 as Any?
            ].filter { ${'$'}0.value != nil },
            body: Empty.none,
            contentTypes: nil,
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
  fun `test generation of multiple query parameters with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI,
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

        public func fetchTest(category: FetchTestCategoryQueryParam, type: FetchTestTypeQueryParam) async throws -> [String : AnyValue] {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests",
            pathParameters: nil,
            queryParameters: [
              "category": category,
              "type": type
            ],
            body: Empty.none,
            contentTypes: nil,
            acceptTypes: self.defaultAcceptTypes,
            headers: nil
          )
        }

        public enum FetchTestCategoryQueryParam : String, CaseIterable, Codable {

          case politics = "politics"
          case science = "science"

        }

        public enum FetchTestTypeQueryParam : String, CaseIterable, Codable {

          case all = "all"
          case limited = "limited"

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
