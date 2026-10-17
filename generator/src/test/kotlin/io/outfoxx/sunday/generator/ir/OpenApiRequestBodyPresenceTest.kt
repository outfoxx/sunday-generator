/*
 * Copyright 2026 Outfox, Inc.
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

import io.outfoxx.sunday.generator.tools.optionalRequestBodyApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OpenApiRequestBodyPresenceTest {
  @Test
  fun `presence is independent from nullability and survives IR serialization`() {
    val api = optionalRequestBodyApi("openapi")
    for (candidate in listOf(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))) {
      val bodies =
        candidate.services
          .single()
          .operations
          .associate { it.id to requireNotNull(it.requestBody) }
      assertEquals(false, bodies.getValue("optionalPost").required)
      assertEquals(false, bodies.getValue("defaultPost").required)
      assertEquals(true, bodies.getValue("requiredPost").required)
      bodies.values.forEach { assertFalse(it.type.nullable) }
    }
  }
}
