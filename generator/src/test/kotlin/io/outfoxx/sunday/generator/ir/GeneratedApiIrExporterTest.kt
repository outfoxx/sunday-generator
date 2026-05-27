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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class GeneratedApiIrExporterTest {

  @Test
  fun `exports AsyncAPI source to generated API IR`(
    @ResourceUri("asyncapi/ir/project-events.yaml") sourceUri: URI,
  ) {

    val api =
      GeneratedApiIrExporter()
        .export(sourceUri, GeneratedApiIrSourceKind.ASYNCAPI)

    assertThat(api.name, equalTo("Craft Events API"))
    assertThat(api.source.kind, equalTo(GeneratedSourceSpec.Kind.ASYNCAPI))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { operation -> operation.id },
      equalTo(listOf("projectChanged")),
    )
  }

  @Test
  fun `composes OpenAPI and AsyncAPI sources to generated API IR`(
    @ResourceUri("openapi/ir/composition-audit-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/composition-audit.yaml") asyncApiUri: URI,
  ) {

    val api =
      GeneratedApiIrExporter()
        .export(listOf(openApiUri, asyncApiUri))

    assertThat(api.name, equalTo("Craft HTTP API"))
    assertThat(api.services.map { service -> service.name }, equalTo(listOf("ProjectsService", "UsersService")))
    assertThat(
      api.services.associate { service ->
        service.name to service.operations.map { operation -> operation.id }
      },
      equalTo(
        mapOf(
          "ProjectsService" to listOf("getProject", "projectChanged"),
          "UsersService" to listOf("getUser", "userChanged"),
        ),
      ),
    )
  }

  @Test
  fun `exports OpenAPI 3_1 schemas with const keyword`(
    @ResourceUri("openapi/ir/const-schema-3.1.yaml") sourceUri: URI,
  ) {

    val api =
      GeneratedApiIrExporter()
        .export(sourceUri, GeneratedApiIrSourceKind.OPENAPI)

    assertThat(api.models.map { model -> model.name }, equalTo(listOf("BadRequest")))
    assertThat(
      api
        .models
        .single()
        .properties
        .map { property -> property.name },
      equalTo(listOf("title", "status")),
    )
  }

  @Test
  fun `composed AsyncAPI stream replaces OpenAPI event stream framing placeholder`(
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/event-stream-payload.yaml") asyncApiUri: URI,
  ) {

    val api =
      GeneratedApiIrExporter()
        .export(listOf(openApiUri, asyncApiUri))

    assertThat(api.services.map { service -> service.name }, equalTo(listOf("EventsService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { operation -> operation.id },
      equalTo(listOf("streamProjectEvents")),
    )
    assertThat(
      api
        .services
        .single()
        .operations
        .single()
        .responses
        .single()
        .type,
      equalTo(GeneratedTypeRef.named("ProjectEvent")),
    )
  }

  @Test
  fun `composed AsyncAPI 3_1 stream replaces OpenAPI event stream framing placeholder`(
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {

    val api =
      GeneratedApiIrExporter()
        .export(listOf(openApiUri, asyncApiUri))

    assertThat(api.services.map { service -> service.name }, equalTo(listOf("EventsService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .single(),
      equalTo(
        GeneratedOperation(
          id = "streamEvents",
          method = "SUBSCRIBE",
          path = "/events",
          responses =
            listOf(
              GeneratedResponse(
                type = GeneratedTypeRef.named("EventEnvelope"),
                mediaTypes = listOf("application/json"),
              ),
            ),
          streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
          documentation = GeneratedDocumentation(summary = "Stream project events"),
        ),
      ),
    )
    assertThat(api.models.map { model -> model.name }.contains("EventEnvelope"), equalTo(true))
    assertThat(api.models.map { model -> model.name }.contains("EventData"), equalTo(true))
  }
}
