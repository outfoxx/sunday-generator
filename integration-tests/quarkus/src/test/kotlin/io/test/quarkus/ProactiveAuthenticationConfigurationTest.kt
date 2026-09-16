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

package io.test.quarkus

import io.quarkus.test.QuarkusUnitTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProactiveAuthenticationConfigurationTest {
  companion object {
    @JvmField
    @RegisterExtension
    val application =
      QuarkusUnitTest()
        .withApplicationRoot { archive ->
          archive
            .addPackages(true, "io.test.quarkus.secure")
            .addClasses(SecurityBindings::class.java, SecurePolicy::class.java, SecureHealth::class.java)
        }.overrideConfigKey("quarkus.http.auth.proactive", "true")
        .assertException { error ->
          val messages = generateSequence(error) { it.cause }.joinToString("\n") { it.toString() }
          assertTrue(messages.contains("proactive authentication is disabled"), messages)
        }
  }

  @Test
  fun `rejects proactive authentication before serving annotated endpoints`() {
    fail<Unit>("The application must fail to start")
  }
}
