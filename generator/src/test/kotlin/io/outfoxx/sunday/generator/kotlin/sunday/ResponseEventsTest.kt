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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/Sunday] [RAML] Response Events Test")
class ResponseEventsTest {

  @Test
  fun `test event source method`(
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
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
        public val defaultAcceptTypes: List<MediaType> = listOf(),
      ) {
        public suspend fun fetchEvents(): EventSource = this.requestFactory
          .eventSource(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = listOf(MediaType.EventStream)
          )
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event stream method generation`(
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
      package io.test.service

      import io.outfoxx.sunday.MediaType
      import io.outfoxx.sunday.RequestFactory
      import io.outfoxx.sunday.http.Method
      import io.test.Test1
      import io.test.Test2
      import io.test.Test3
      import kotlin.Any
      import kotlin.collections.List
      import kotlin.reflect.typeOf
      import kotlinx.coroutines.flow.Flow

      public class API(
        public val requestFactory: RequestFactory,
        public val defaultContentTypes: List<MediaType> = listOf(),
        public val defaultAcceptTypes: List<MediaType> = listOf(),
      ) {
        public fun fetchEventsSimple(): Flow<Test1> = this.requestFactory
          .eventStream(
            method = Method.Get,
            pathTemplate = "/test1",
            acceptTypes = listOf(MediaType.EventStream),
            decoder = { decoder, _, _, data, _ -> decoder.decode<Test1>(data, typeOf<Test1>()) }
          )

        public fun fetchEventsDiscriminated(): Flow<Any> = this.requestFactory
          .eventStream(
            method = Method.Get,
            pathTemplate = "/test2",
            acceptTypes = listOf(MediaType.EventStream),
            decoder = { decoder, event, _, data, logger ->
              when (event) {
                "Test1" -> decoder.decode<Test1>(data, typeOf<Test1>())
                "test2" -> decoder.decode<Test2>(data, typeOf<Test2>())
                "t3" -> decoder.decode<Test3>(data, typeOf<Test3>())
                else -> {
                  logger.error("Unknown event type, ignoring event: event=${'$'}event")
                  null
                }
              }
            }
          )
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event stream method generation for common base events`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
      package io.test.service

      import io.outfoxx.sunday.MediaType
      import io.outfoxx.sunday.RequestFactory
      import io.outfoxx.sunday.http.Method
      import io.test.Base
      import io.test.Test1
      import io.test.Test2
      import kotlin.collections.List
      import kotlin.reflect.typeOf
      import kotlinx.coroutines.flow.Flow

      public class API(
        public val requestFactory: RequestFactory,
        public val defaultContentTypes: List<MediaType> = listOf(),
        public val defaultAcceptTypes: List<MediaType> = listOf(),
      ) {
        public fun fetchEventsSimple(): Flow<Base> = this.requestFactory
          .eventStream(
            method = Method.Get,
            pathTemplate = "/test1",
            acceptTypes = listOf(MediaType.EventStream),
            decoder = { decoder, _, _, data, _ -> decoder.decode<Base>(data, typeOf<Base>()) }
          )

        public fun fetchEventsDiscriminated(): Flow<Base> = this.requestFactory
          .eventStream(
            method = Method.Get,
            pathTemplate = "/test2",
            acceptTypes = listOf(MediaType.EventStream),
            decoder = { decoder, event, _, data, logger ->
              when (event) {
                "Test1" -> decoder.decode<Test1>(data, typeOf<Test1>())
                "Test2" -> decoder.decode<Test2>(data, typeOf<Test2>())
                else -> {
                  logger.error("Unknown event type, ignoring event: event=${'$'}event")
                  null
                }
              }
            }
          )
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
