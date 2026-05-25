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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedMedia
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedOperationMediaTest {

  @Test
  fun `selects first unspecified or two hundred response`() {
    val notFound = response(404)
    val created = response(201)
    val ok = response(200)
    val operation = operation(responses = listOf(notFound, created, ok))
    val unspecified = response(null)

    assertSame(created, operation.primarySuccessResponse())
    assertSame(unspecified, operation(responses = listOf(notFound, unspecified, ok)).primarySuccessResponse())
    assertNull(operation(responses = listOf(notFound)).primarySuccessResponse())
  }

  @Test
  fun `detects no content responses`() {
    assertTrue(response(204).isNoContent)
    assertFalse(response(200).isNoContent)
    assertFalse(response(null).isNoContent)
  }

  @Test
  fun `orders default media from target preferences before source declaration order`() {
    val api =
      GeneratedApi(
        name = "Example",
        source = source(),
        media =
          GeneratedMedia(
            request = listOf("application/json", "application/yaml"),
            response = listOf("application/json", "text/plain"),
          ),
      )

    assertEquals(
      listOf("text/plain", "application/json", "application/yaml"),
      api.orderedDefaultMediaTypes(listOf("text/plain", "application/json")),
    )
  }

  @Test
  fun `selects service default media from referenced operation media`() {
    val defaults =
      listOf(
        "application/json",
        "application/yaml",
        "text/plain",
        GeneratedMediaSelection.EVENT_STREAM,
      )
    val service =
      GeneratedService(
        name = "Projects",
        operations =
          listOf(
            operation(
              requestBody = payload("application/json"),
              responses = listOf(response(200, "text/plain")),
            ),
            operation(
              requestBody = payload("application/custom"),
              responses = listOf(response(200)),
            ),
            operation(
              streaming = GeneratedStreaming(GeneratedStreaming.Kind.EVENT_STREAM),
              responses = listOf(response(200, "application/json")),
            ),
          ),
      )

    assertEquals(
      GeneratedMediaSelection(
        contentTypes = listOf("application/json"),
        acceptTypes =
          listOf(
            "application/json",
            "application/yaml",
            "text/plain",
            GeneratedMediaSelection.EVENT_STREAM,
          ),
      ),
      service.defaultMediaSelection(defaults),
    )
  }

  @Test
  fun `selects explicit operation media only when it differs from defaults`() {
    val defaults = listOf("application/json", "application/yaml")

    assertNull(payload("application/json").explicitContentTypes(defaults))
    assertEquals(listOf("application/custom"), payload("application/custom").explicitContentTypes(defaults))
    assertNull(response(200, "application/json", "application/yaml").explicitAcceptTypes(defaults))
    assertEquals(listOf("text/plain"), response(200, "text/plain").explicitAcceptTypes(defaults))
  }

  private fun operation(
    requestBody: GeneratedPayload? = null,
    responses: List<GeneratedResponse> = listOf(),
    streaming: GeneratedStreaming? = null,
  ) = GeneratedOperation(
    id = "fetch",
    method = "GET",
    path = "/projects",
    requestBody = requestBody,
    responses = responses,
    streaming = streaming,
  )

  private fun payload(vararg mediaTypes: String) =
    GeneratedPayload(
      type = GeneratedTypeRef.scalar("string"),
      mediaTypes = mediaTypes.toList(),
    )

  private fun response(
    status: Int?,
    vararg mediaTypes: String,
  ) = GeneratedResponse(
    status = status,
    type = GeneratedTypeRef.scalar("string"),
    mediaTypes = mediaTypes.toList(),
  )

  private fun source() = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
}
