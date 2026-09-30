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

package io.test.jaxrs

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.ObjectMapper
import io.test.defaults.Probe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.validation.constraints.Size

/** Verifies the compiled output of the CLI with no model flags supplied. */
class ModelDefaultsTest {

  @Test
  fun `default models use javax validation and preserve unknown fields`() {
    val model = Probe("valid")
    assertEquals("valid", model.name)
    assertNotNull(Probe::class.java.getDeclaredField("name").getAnnotation(Size::class.java))
    assertTrue(Probe::class.java.declaredMethods.any { it.isAnnotationPresent(JsonAnyGetter::class.java) })
    assertTrue(Probe::class.java.declaredMethods.any { it.isAnnotationPresent(JsonAnySetter::class.java) })
    model.setAdditionalProperty("future", mapOf("nested" to listOf(null, true)))
    val mapper = ObjectMapper()
    assertEquals(
      mapper.readTree("""{"name":"valid","future":{"nested":[null,true]}}"""),
      mapper.readTree(mapper.writeValueAsBytes(model)),
    )
  }
}
