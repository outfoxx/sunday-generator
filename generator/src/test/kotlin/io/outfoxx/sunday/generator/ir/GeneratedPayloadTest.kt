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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedPayloadTest {
  @Test
  fun `IR preserves optional bodies while omitting the required default`() {
    val api = GeneratedApiIrExporter().export(javaClass.getResource("/optional-bodies/api.yaml")!!.toURI())
    val yaml = GeneratedApiYaml.writeString(api)
    val tree = YAMLMapper().readTree(yaml)
    val operations = api.services.flatMap { it.operations }
    val bodies =
      tree["services"].flatMap { it["operations"].toList() }.associate {
        it["id"].asText() to
          it["requestBody"]
      }
    assertTrue(operations.any { it.requestBody?.required == true })
    assertTrue(operations.any { it.requestBody?.required == false })
    for (operation in operations) {
      val body = requireNotNull(operation.requestBody)
      val serialized = bodies.getValue(operation.id)
      if (body.required) {
        assertFalse(serialized.has("required"))
      } else {
        assertTrue(serialized.has("required"))
        assertFalse(serialized["required"].asBoolean())
      }
    }
    assertEquals(
      operations.map { it.requestBody },
      GeneratedApiYaml
        .readString(yaml)
        .services
        .flatMap { it.operations }
        .map { it.requestBody },
    )
  }
}
