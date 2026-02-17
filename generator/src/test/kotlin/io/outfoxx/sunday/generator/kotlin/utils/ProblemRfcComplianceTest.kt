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

package io.outfoxx.sunday.generator.kotlin.utils

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("[Kotlin] Problem RFC Compliance Test")
class ProblemRfcComplianceTest {

  @Test
  fun `validation passes for quarkus rfc9457 defaults`() {
    val support = KotlinProblemLibrary.QUARKUS.support(KotlinProblemRfc.RFC9457)
    assertDoesNotThrow { ProblemRfcCompliance.validate(support.rfc, support.builderFieldMapping) }
  }

  @Test
  fun `validation passes for sunday rfc9457 defaults`() {
    val support = KotlinProblemLibrary.SUNDAY.support(KotlinProblemRfc.RFC9457)
    assertDoesNotThrow { ProblemRfcCompliance.validate(support.rfc, support.builderFieldMapping) }
  }

  @Test
  fun `validation fails when required mapping is missing`() {
    val invalidMapping = mapOf(ProblemField.TYPE to "withType")
    assertThrows(IllegalArgumentException::class.java) {
      ProblemRfcCompliance.validate(KotlinProblemRfc.RFC9457, invalidMapping)
    }
  }
}
