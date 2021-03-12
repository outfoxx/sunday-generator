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
  fun `test request method generation in client mode`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
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

          func fetchTest() -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          func putTest(body: Test) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
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

          func postTest(body: Test) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
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

          func patchTest(body: Test) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
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

          func deleteTest() -> RequestCompletePublisher {
            return self.requestFactory.result(
              method: .delete,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          func headTest() -> RequestCompletePublisher {
            return self.requestFactory.result(
              method: .head,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          func optionsTest() -> RequestCompletePublisher {
            return self.requestFactory.result(
              method: .options,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          func patchableTest(body: PatchableTest.Patch) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
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

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }
}
