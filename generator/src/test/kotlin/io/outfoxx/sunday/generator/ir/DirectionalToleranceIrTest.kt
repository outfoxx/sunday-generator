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

import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.discriminatorFallbackOrNull
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class DirectionalToleranceIrTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `preserves directional tolerance and resolves string discriminator fallbacks`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = directionalToleranceApi(frontend, directory)
    val models = api.models.associateBy { it.name }
    assertEquals(GeneratedTolerance.RESPONSE, models.getValue("State").tolerance)
    assertEquals(GeneratedTolerance.ALL, models.getValue("OpenState").tolerance)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    assertEquals("1", api.irVersion)
    val event = models.getValue("Event")
    val fallback = event.discriminatorFallbackOrNull(GeneratedApiIndex(api))
    assertNotNull(fallback)
    assertEquals("EventUnknown", fallback!!.modelName)
    assertEquals(setOf("created"), fallback.mappedValues)
    assertNull(fallback.discriminatorProperty.allowedValues)
    assertNull(event.copy(tolerance = null).discriminatorFallbackOrNull(GeneratedApiIndex(api)))
  }
}
