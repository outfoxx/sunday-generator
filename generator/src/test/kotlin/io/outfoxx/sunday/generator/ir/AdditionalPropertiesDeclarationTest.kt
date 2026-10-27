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

import io.outfoxx.sunday.generator.tools.closedModelsApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class AdditionalPropertiesDeclarationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `explicit storage declarations remain distinguishable from implicit openness`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val models = closedModelsApi(frontend, directory).models.associateBy { it.name }
    assertNull(models.getValue("OpenRecord").additionalProperties, frontend)
    assertEquals(true, models.getValue("ExplicitRecord").additionalProperties?.allowed, frontend)
    assertEquals(true, models.getValue("ExplicitChild").additionalProperties?.allowed, frontend)
  }
}
