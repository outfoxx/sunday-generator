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
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift/Sunday] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
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

          public func fetchTest() async throws -> Test {
            return try await self.requestFactory.result(
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

          public func putTest(body: Test) async throws -> Test {
            return try await self.requestFactory.result(
              method: .put,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func postTest(body: Test) async throws -> Test {
            return try await self.requestFactory.result(
              method: .post,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func patchTest(body: Test) async throws -> Test {
            return try await self.requestFactory.result(
              method: .patch,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func deleteTest() async throws {
            return try await self.requestFactory.result(
              method: .delete,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func headTest() async throws {
            return try await self.requestFactory.result(
              method: .head,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func optionsTest() async throws {
            return try await self.requestFactory.result(
              method: .options,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func patchableTest(body: PatchableTest) async throws -> Test {
            return try await self.requestFactory.result(
              method: .patch,
              pathTemplate: "/tests2",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func requestTest() async throws -> URLRequest {
            return try await self.requestFactory.request(
              method: .get,
              pathTemplate: "/request",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func responseTest() async throws -> (Data?, HTTPURLResponse) {
            return try await self.requestFactory.response(
              method: .get,
              pathTemplate: "/response",
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
      },
    )
  }

  @Test
  fun `test request method generation with result response`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          SwiftSundayGenerator.Options(
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
        import Foundation
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

          public func fetchTest() async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
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

          public func putTest(body: Test) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .put,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func postTest(body: Test) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .post,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func patchTest(body: Test) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .patch,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func deleteTest() async throws -> ResultResponse<Void> {
            return try await self.requestFactory.resultResponse(
              method: .delete,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func headTest() async throws -> ResultResponse<Void> {
            return try await self.requestFactory.resultResponse(
              method: .head,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func optionsTest() async throws -> ResultResponse<Void> {
            return try await self.requestFactory.resultResponse(
              method: .options,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func patchableTest(body: PatchableTest) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .patch,
              pathTemplate: "/tests2",
              pathParameters: nil,
              queryParameters: nil,
              body: body,
              contentTypes: self.defaultContentTypes,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func requestTest() async throws -> URLRequest {
            return try await self.requestFactory.request(
              method: .get,
              pathTemplate: "/request",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func responseTest() async throws -> (Data?, HTTPURLResponse) {
            return try await self.requestFactory.response(
              method: .get,
              pathTemplate: "/response",
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
      },
    )
  }

  @Test
  fun `test request method generation with nullify`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
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
            requestFactory.registerProblem(type: "http://example.com/test_not_found", problemType: TestNotFoundProblem.self)
            requestFactory.registerProblem(type: "http://example.com/another_not_found", problemType: AnotherNotFoundProblem.self)
          }

          public func fetchTest1OrNil(limit: Int) async throws -> Test? {
            return try await nilifyResponse(
                statuses: [404, 405],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest1(limit: limit)
              }
          }

          public func fetchTest1(limit: Int) async throws -> Test {
            return try await self.requestFactory.result(
              method: .get,
              pathTemplate: "/test1",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest2OrNil(limit: Int) async throws -> Test? {
            return try await nilifyResponse(
                statuses: [404],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest2(limit: limit)
              }
          }

          public func fetchTest2(limit: Int) async throws -> Test {
            return try await self.requestFactory.result(
              method: .get,
              pathTemplate: "/test2",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest3OrNil(limit: Int) async throws -> Test? {
            return try await nilifyResponse(
                statuses: [],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest3(limit: limit)
              }
          }

          public func fetchTest3(limit: Int) async throws -> Test {
            return try await self.requestFactory.result(
              method: .get,
              pathTemplate: "/test3",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest4OrNil(limit: Int) async throws -> Test? {
            return try await nilifyResponse(
                statuses: [404, 405],
                problemTypes: []
              ) {
                try await fetchTest4(limit: limit)
              }
          }

          public func fetchTest4(limit: Int) async throws -> Test {
            return try await self.requestFactory.result(
              method: .get,
              pathTemplate: "/test4",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest5OrNil(limit: Int) async throws -> Test? {
            return try await nilifyResponse(
                statuses: [404],
                problemTypes: []
              ) {
                try await fetchTest5(limit: limit)
              }
          }

          public func fetchTest5(limit: Int) async throws -> Test {
            return try await self.requestFactory.result(
              method: .get,
              pathTemplate: "/test5",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
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
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation with nullify and result response`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          SwiftSundayGenerator.Options(
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
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
            requestFactory.registerProblem(type: "http://example.com/test_not_found", problemType: TestNotFoundProblem.self)
            requestFactory.registerProblem(type: "http://example.com/another_not_found", problemType: AnotherNotFoundProblem.self)
          }

          public func fetchTest1OrNil(limit: Int) async throws -> ResultResponse<Test>? {
            return try await nilifyResponse(
                statuses: [404, 405],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest1(limit: limit)
              }
          }

          public func fetchTest1(limit: Int) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .get,
              pathTemplate: "/test1",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest2OrNil(limit: Int) async throws -> ResultResponse<Test>? {
            return try await nilifyResponse(
                statuses: [404],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest2(limit: limit)
              }
          }

          public func fetchTest2(limit: Int) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .get,
              pathTemplate: "/test2",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest3OrNil(limit: Int) async throws -> ResultResponse<Test>? {
            return try await nilifyResponse(
                statuses: [],
                problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
              ) {
                try await fetchTest3(limit: limit)
              }
          }

          public func fetchTest3(limit: Int) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .get,
              pathTemplate: "/test3",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest4OrNil(limit: Int) async throws -> ResultResponse<Test>? {
            return try await nilifyResponse(
                statuses: [404, 405],
                problemTypes: []
              ) {
                try await fetchTest4(limit: limit)
              }
          }

          public func fetchTest4(limit: Int) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .get,
              pathTemplate: "/test4",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
              ],
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public func fetchTest5OrNil(limit: Int) async throws -> ResultResponse<Test>? {
            return try await nilifyResponse(
                statuses: [404],
                problemTypes: []
              ) {
                try await fetchTest5(limit: limit)
              }
          }

          public func fetchTest5(limit: Int) async throws -> ResultResponse<Test> {
            return try await self.requestFactory.resultResponse(
              method: .get,
              pathTemplate: "/test5",
              pathParameters: nil,
              queryParameters: [
                "limit": limit
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
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
