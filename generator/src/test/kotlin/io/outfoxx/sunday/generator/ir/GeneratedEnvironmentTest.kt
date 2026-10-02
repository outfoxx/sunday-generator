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

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.PayloadUse
import io.outfoxx.sunday.generator.Tolerance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedEnvironmentTest {
  @Test
  fun `more local shared declarations override inherited role and profile values`() {
    val tag =
      GeneratedEnvironment(
        client = mapOf("timeout" to "PT5S"),
        profiles = mapOf("internal" to GeneratedEnvironment.Scope(client = mapOf("timeout" to "PT9S"))),
      )
    val path = GeneratedEnvironment(all = mapOf("retry" to "3"))
    val operation = GeneratedEnvironment(all = mapOf("timeout" to "PT2S"))
    val merged = tag.inherit(path).inherit(operation)
    for (profile in listOf(null, "internal", "external")) {
      assertEquals(
        mapOf("timeout" to "PT2S", "retry" to "3"),
        merged.resolve(GenerationContext(GenerationMode.Client, profile)) { first, second -> first + second },
      )
    }
  }

  @Test
  fun `merges each role and profile independently before resolving precedence`() {
    val inherited =
      GeneratedEnvironment(
        all = mapOf("timeout" to "shared", "retry" to "shared"),
        client = mapOf("timeout" to "client"),
        server = mapOf("timeout" to "server"),
        profiles = mapOf("internal" to GeneratedEnvironment.Scope(all = mapOf("retry" to "profile"))),
      )
    val local =
      GeneratedEnvironment(
        client = mapOf("retry" to "local-client"),
        profiles =
          mapOf("internal" to GeneratedEnvironment.Scope(client = mapOf("timeout" to "profile-client"))),
      )
    val merged = inherited.merge(local) { base, override -> base + override }
    assertEquals(
      mapOf("timeout" to "profile-client", "retry" to "profile"),
      merged.resolve(GenerationContext(GenerationMode.Client, "internal")) { base, override -> base + override },
    )
    assertEquals(
      mapOf("timeout" to "server", "retry" to "shared"),
      merged.resolve(GenerationContext(GenerationMode.Server)) { base, override -> base + override },
    )
    assertEquals(
      mapOf("timeout" to "client", "retry" to "local-client"),
      merged.resolve(GenerationContext(GenerationMode.Client, "external")) { base, override -> base + override },
    )
  }

  @Test
  fun `explicit tolerance overrides default scope without affecting other payload uses`() {
    for (role in GenerationMode.entries) {
      val responseOnly = GenerationContext(role, payloadUse = PayloadUse.Request)
      val all = responseOnly.copy(defaultTolerance = Tolerance.All)
      assertEquals(Tolerance.Response, responseOnly.defaultTolerance)
      assertFalse(null.allowsUnknown(responseOnly))
      assertTrue(null.allowsUnknown(all))
      assertFalse(GeneratedTolerance.RESPONSE.allowsUnknown(all))
      assertTrue(GeneratedTolerance.ALL.allowsUnknown(responseOnly))
      for (use in listOf(PayloadUse.Response, PayloadUse.Event, PayloadUse.Standalone)) {
        assertTrue(GeneratedTolerance.RESPONSE.allowsUnknown(responseOnly.copy(payloadUse = use)))
      }
    }
    for (value in listOf("read", "strict", "tolerant")) {
      assertThrows(IllegalArgumentException::class.java) { GeneratedTolerance.parse(value, "test") }
    }
    assertThrows(IllegalArgumentException::class.java) { GeneratedTolerance.parse(true, "test") }
    assertThrows(IllegalArgumentException::class.java) { GenerationContext(GenerationMode.Client, " ") }
  }
}
