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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class OpenApiStreamingRequestModeTest {

  @Test
  fun `maps OpenAPI streaming request body mode variants`(
    @ResourceUri("openapi/ir/streaming-request-modes-3.1.yaml") testUri: URI,
  ) {
    val operations =
      OpenApiToGeneratedApi()
        .convert(testUri)
        .services
        .single()
        .operations
        .associateBy { operation -> operation.id }

    assertEquals(
      GeneratedModeFlag(client = true),
      operations.getValue("streamClient").requestBody?.streaming,
    )
    assertEquals(
      GeneratedModeFlag(server = true),
      operations.getValue("streamServer").requestBody?.streaming,
    )
    assertEquals(
      GeneratedModeFlag(all = true),
      operations.getValue("streamBoth").requestBody?.streaming,
    )
    assertEquals(
      GeneratedModeFlag(all = true),
      operations.getValue("streamTrue").requestBody?.streaming,
    )
    assertEquals(
      GeneratedModeFlag(client = true, server = false),
      operations.getValue("streamMapped").requestBody?.streaming,
    )
    assertEquals(
      GeneratedModeFlag(server = true),
      operations.getValue("streamReference").requestBody?.streaming,
    )
    assertEquals(
      null,
      operations.getValue("streamInvalid").requestBody?.streaming,
    )
    assertEquals(
      null,
      operations.getValue("streamBlank").requestBody?.streaming,
    )
    assertEquals(
      null,
      operations.getValue("streamEmptyMapped").requestBody?.streaming,
    )
  }
}
