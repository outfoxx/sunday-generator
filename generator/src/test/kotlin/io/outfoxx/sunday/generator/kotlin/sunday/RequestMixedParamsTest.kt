package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/Sunday] [RAML] Request Mixed Params Test")
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service
        
        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.Any
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(
            select: FetchTestSelectUriParam,
            page: FetchTestPageQueryParam,
            xType: FetchTestXTypeHeaderParam
          ): Map<String, Any> = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests/{select}",
            pathParameters = mapOf(
              "select" to select
            ),
            queryParameters = mapOf(
              "page" to page
            ),
            acceptTypes = this.defaultAcceptTypes,
            headers = mapOf(
              "x-type" to xType
            )
          )

          public enum class FetchTestPageQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestSelectUriParam {
            All,
            Limited,
          }

          public enum class FetchTestXTypeHeaderParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test @Disabled
  fun `test generation of multiple parameters of same name with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.Any
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(
            type: FetchTestTypeUriParam,
            type_: FetchTestTypeQueryParam,
            type__: FetchTestTypeHeaderParam
          ): Map<String, Any> = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests/{type}",
            pathParameters = mapOf(
              "type" to type
            ),
            queryParameters = mapOf(
              "type" to type_
            ),
            acceptTypes = this.defaultAcceptTypes,
            headers = mapOf(
              "type" to type__
            )
          )

          public enum class FetchTestTypeQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeUriParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeHeaderParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

}