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
@DisplayName("[Swift/Sunday] [RAML] Request Mixed Params Test")
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
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

        public func fetchTest(
          select: FetchTestSelectUriParam,
          page: FetchTestPageQueryParam,
          xType: FetchTestXTypeHeaderParam
        ) async throws -> [String : AnyValue] {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests/{select}",
            pathParameters: [
              "select": select
            ],
            queryParameters: [
              "page": page
            ],
            body: Empty.none,
            contentTypes: nil,
            acceptTypes: self.defaultAcceptTypes,
            headers: [
              "x-type": xType
            ]
          )
        }

        public enum FetchTestSelectUriParam : String, CaseIterable, Codable {

          case all = "all"
          case limited = "limited"

        }

        public enum FetchTestPageQueryParam : String, CaseIterable, Codable {

          case all = "all"
          case limited = "limited"

        }

        public enum FetchTestXTypeHeaderParam : String, CaseIterable, Codable {

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

  @Test
  fun `test generation of multiple parameters of same name with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
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

        public func fetchTest(
          type: FetchTestTypeUriParam,
          type_: FetchTestTypeQueryParam,
          type__: FetchTestTypeHeaderParam
        ) async throws -> [String : AnyValue] {
          return try await self.requestFactory.result(
            method: .get,
            pathTemplate: "/tests/{type}",
            pathParameters: [
              "type": type
            ],
            queryParameters: [
              "type": type_
            ],
            body: Empty.none,
            contentTypes: nil,
            acceptTypes: self.defaultAcceptTypes,
            headers: [
              "type": type__
            ]
          )
        }

        public enum FetchTestTypeUriParam : String, CaseIterable, Codable {

          case all = "all"
          case limited = "limited"

        }

        public enum FetchTestTypeQueryParam : String, CaseIterable, Codable {

          case all = "all"
          case limited = "limited"

        }

        public enum FetchTestTypeHeaderParam : String, CaseIterable, Codable {

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
