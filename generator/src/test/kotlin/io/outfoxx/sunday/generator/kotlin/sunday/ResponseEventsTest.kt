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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/Sunday] [RAML] Response Events Test")
class ResponseEventsTest {

  @Test
  fun `test event source method`(
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI
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

        import io.outfoxx.sunday.EventSource
        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchEvents(): EventSource = this.requestFactory.eventSource(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test event stream method generation`(
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI
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
        import io.outfoxx.sunday.typeOf
        import io.test.Test1
        import io.test.Test2
        import kotlin.Any
        import kotlin.collections.List
        import kotlinx.coroutines.flow.Flow

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchEvents(): Flow<Any> = this.requestFactory.eventStream(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = this.defaultAcceptTypes,
            eventTypes = mapOf(
              "Test1" to typeOf<Test1>(), 
              "Test2" to typeOf<Test2>()
            )
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test event stream method generation for common base events`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI
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
        import io.outfoxx.sunday.typeOf
        import io.test.Base
        import io.test.Test1
        import io.test.Test2
        import kotlin.collections.List
        import kotlinx.coroutines.flow.Flow

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchEvents(): Flow<Base> = this.requestFactory.eventStream(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = this.defaultAcceptTypes,
            eventTypes = mapOf(
              "Test1" to typeOf<Test1>(), 
              "Test2" to typeOf<Test2>()
            )
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

}
