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
@DisplayName("[Swift/Sunday] [RAML] Response Events Test")
class ResponseEventsTest {

  @Test
  fun `test event source method`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI
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
            defaultAcceptTypes: [MediaType] = []
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          public func fetchEvents() -> EventSource {
            return self.requestFactory.eventSource(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: [.eventStream],
              headers: nil
            )}

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test event stream method generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI
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
            defaultAcceptTypes: [MediaType] = []
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          public func fetchEventsSimple() -> AsyncStream<Test1> {
            return self.requestFactory.eventStream(
              method: .get,
              pathTemplate: "/test1",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: [.eventStream],
              headers: nil,
              decoder: { decoder, _, _, data, _ in try decoder.decode(Test1.self, from: data) }
            )
          }
        
          public func fetchEventsDiscriminated() -> AsyncStream<Any> {
            return self.requestFactory.eventStream(
              method: .get,
              pathTemplate: "/test2",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: [.eventStream],
              headers: nil,
              decoder: { decoder, event, _, data, log in
                switch event {
                case "Test1": return try decoder.decode(Test1.self, from: data)
                case "test2": return try decoder.decode(Test2.self, from: data)
                case "t3": return try decoder.decode(Test3.self, from: data)
                default:
                  log.error("Unknown event type, ignoring event: event=\(event)")
                  return nil
                }
              }
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
  fun `test event stream method generation for common base events`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI
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
            defaultAcceptTypes: [MediaType] = []
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          public func fetchEventsSimple() -> AsyncStream<Base> {
            return self.requestFactory.eventStream(
              method: .get,
              pathTemplate: "/test1",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: [.eventStream],
              headers: nil,
              decoder: { decoder, _, _, data, _ in try decoder.decode(Base.AnyRef.self, from: data).value }
            )
          }

          public func fetchEventsDiscriminated() -> AsyncStream<Base> {
            return self.requestFactory.eventStream(
              method: .get,
              pathTemplate: "/test2",
              pathParameters: nil,
              queryParameters: nil,
              body: Empty.none,
              contentTypes: nil,
              acceptTypes: [.eventStream],
              headers: nil,
              decoder: { decoder, event, _, data, log in
                switch event {
                case "Test1": return try decoder.decode(Test1.self, from: data)
                case "Test2": return try decoder.decode(Test2.self, from: data)
                default:
                  log.error("Unknown event type, ignoring event: event=\(event)")
                  return nil
                }
              }
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
