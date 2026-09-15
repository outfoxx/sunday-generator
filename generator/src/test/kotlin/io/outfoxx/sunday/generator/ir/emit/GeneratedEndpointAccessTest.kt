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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeneratedEndpointAccessTest {
  @Test
  fun `resolves inheritance explicit public overrides and anonymous alternatives`() {
    val operation = GeneratedOperation("get", "GET", "/")
    val service = GeneratedService("Service", operations = listOf(operation))
    val protectedAuth = GeneratedAuth(requirements = listOf(GeneratedSecurityRequirement(listOf("bearer"))))
    val api =
      GeneratedApi(
        name = "Test",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://test"),
        services = listOf(service),
        auth = protectedAuth,
      )
    assertEquals(GeneratedEndpointAccess.AUTHENTICATED, api.endpointAuthentication(service, operation))
    assertEquals(
      GeneratedEndpointAccess.PUBLIC,
      api.endpointAuthentication(service, operation.copy(auth = GeneratedAuth(securityOverride = true))),
    )
    assertEquals(
      GeneratedEndpointAccess.PUBLIC,
      api.endpointAuthentication(
        service,
        operation.copy(
          auth =
            protectedAuth.copy(
              requirements =
                protectedAuth.requirements + GeneratedSecurityRequirement(),
            ),
        ),
      ),
    )
    assertEquals(
      GeneratedEndpointAccess.PUBLIC,
      api.endpointAuthentication(service.copy(auth = GeneratedAuth(securityOverride = true)), operation),
    )
    assertEquals(GeneratedEndpointAccess.UNSPECIFIED, api.copy(auth = null).endpointAuthentication(service, operation))
  }

  @Test
  fun `authorization metadata does not declare authentication or select a mechanism`() {
    val operation =
      GeneratedOperation("get", "GET", "/", auth = GeneratedAuth(zanzibar = mapOf("relation" to "reader")))
    val service = GeneratedService("Service", operations = listOf(operation))
    val api =
      GeneratedApi(
        name = "Test",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://test"),
        services = listOf(service),
      )
    assertEquals(GeneratedEndpointAccess.UNSPECIFIED, api.endpointAuthentication(service, operation))
    assertEquals(
      GeneratedEndpointAccess.AUTHENTICATED,
      api
        .copy(auth = GeneratedAuth(schemes = listOf("application-selected-mechanism")))
        .endpointAuthentication(service, operation),
    )
  }
}
