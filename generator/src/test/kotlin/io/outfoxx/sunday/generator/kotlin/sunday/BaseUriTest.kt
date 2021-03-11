package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/Sunday] [RAML] Base URI Test")
class BaseUriTest {

  @Test
  fun `test baseUrl generation in API`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Client, setOf())

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

        import Environment
        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.URITemplate
        import io.outfoxx.sunday.http.Method
        import kotlin.String
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(): String = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = this.defaultAcceptTypes
          )

          public companion object {
            public fun baseURL(
              server: String = "master",
              environment: Environment = Environment.Sbx,
              version: String = "1"
            ): URITemplate = URITemplate(
              "http://{server}.{environment}.example.com/api/{version}",
              mapOf("server" to server, "environment" to environment, "version" to version)
            )
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
