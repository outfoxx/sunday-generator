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
@DisplayName("[Swift/Sunday] [RAML] Request Header Params Test")
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI
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
            obj: Test,
            strReq: String,
            int: Int = 5
          ) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: [
                "obj": obj,
                "str-req": strReq,
                "int": int
              ]
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
  fun `test optional header parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI
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
            obj: Test? = nil,
            str: String? = nil,
            int: Int? = nil
          ) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: [
                "obj": obj,
                "str": str,
                "int": int
              ]
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
  fun `test generation of multiple header parameters with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI
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

          func fetchTest(category: FetchTestCategoryHeaderParam, type: FetchTestTypeHeaderParam) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: nil,
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: [
                "category": category,
                "type": type
              ]
            )
          }

          public enum FetchTestCategoryHeaderParam : String, CaseIterable, Codable {

            case politics = "politics"
            case science = "science"

          }

          public enum FetchTestTypeHeaderParam : String, CaseIterable, Codable {

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
