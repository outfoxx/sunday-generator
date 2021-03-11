package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GenerationMode.Client
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
@DisplayName("[Swift/Sunday] [RAML] Request Uri Params Test")
class RequestUriParamsTest {

  @Test
  fun `test basic uri parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

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
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          func fetchTest(
            def: String,
            obj: Test,
            strReq: String,
            int: Int = 5
          ) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{obj}/{str-req}/{int}/{def}",
              pathParameters: [
                "def": def,
                "obj": obj,
                "str-req": strReq,
                "int": int
              ],
              queryParameters: nil,
              body: nil as Empty?,
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
  fun `test inherited uri parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

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

          func fetchTest(
            obj: [String : Any],
            str: String,
            def: String,
            int: Int
          ) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{obj}/{str}/{int}/{def}",
              pathParameters: [
                "obj": obj,
                "str": str,
                "def": def,
                "int": int
              ],
              queryParameters: nil,
              body: nil as Empty?,
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
  fun `test optional uri parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

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
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          func fetchTest(
            def: String,
            obj: Test? = nil,
            str: String? = nil,
            int: Int? = nil
          ) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{obj}/{str}/{int}/{def}",
              pathParameters: [
                "def": def,
                "obj": obj,
                "str": str,
                "int": int
              ],
              queryParameters: nil,
              body: nil as Empty?,
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
  fun `test generation of multiple uri parameters with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

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

          func fetchTest(category: FetchTestCategoryUriParam, type: FetchTestTypeUriParam) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{category}/{type}",
              pathParameters: [
                "category": category,
                "type": type
              ],
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: nil
            )
          }

          public enum FetchTestCategoryUriParam : String, CaseIterable, Codable {

            case politics = "politics"
            case science = "science"

          }

          public enum FetchTestTypeUriParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

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
