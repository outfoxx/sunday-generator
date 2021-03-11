package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerator
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Swift/Sunday] [RAML] Request Query Params Test")
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
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
            int: Int? = nil
          ) -> RequestResultPublisher<Test> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: [
                "obj": obj,
                "str-req": strReq,
                "int": int ?? 5
              ],
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
  fun `test optional query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
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
              queryParameters: [
                "obj": obj,
                "str": str,
                "int": int
              ],
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
  fun `test generation of multiple query parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
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

          func fetchTest(category: FetchTestCategoryQueryParam, type: FetchTestTypeQueryParam) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests",
              pathParameters: nil,
              queryParameters: [
                "category": category,
                "type": type
              ],
              body: nil as Empty?,
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
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

}
