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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedJaxrs
import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedOperationMetadataTest {

  @Test
  fun `selects most specific non-empty auth metadata`() {
    val apiAuth = auth("api")
    val serviceAuth = auth("service")
    val operationAuth = auth("operation")
    val api = api(apiAuth)
    val service = service(serviceAuth)

    assertSame(operationAuth, api.effectiveAuth(service, operation(auth = operationAuth)))
    assertSame(serviceAuth, api.effectiveAuth(service, operation()))
    assertSame(apiAuth, api.effectiveAuth(service(auth = null), operation()))
    assertNull(api(auth = null).effectiveAuth(service(auth = null), operation()))
  }

  @Test
  fun `exposes auth scheme lookup and scheme parameters by location`() {
    val header = parameter("Authorization", GeneratedParameter.Location.HEADER)
    val query = parameter("access_token", GeneratedParameter.Location.QUERY)
    val cookie = parameter("SESSION_ID", GeneratedParameter.Location.COOKIE)
    val auth =
      GeneratedAuth(
        schemes = listOf("oauth2"),
        requirements = listOf(GeneratedSecurityRequirement(listOf("oauth2"))),
        securitySchemes =
          listOf(
            GeneratedSecurityScheme(
              name = "oauth2",
              type = "OAuth 2.0",
              headers = listOf(header),
              queryParameters = listOf(query),
              cookieParameters = listOf(cookie),
            ),
          ),
      )

    assertFalse(auth.isEmpty)
    assertEquals(listOf("oauth2"), auth.requirementSchemeNames)
    assertEquals(listOf(header), auth.schemeOrNull("oauth2")?.parameters(GeneratedParameter.Location.HEADER))
    assertEquals(listOf(query), auth.securityParameters(GeneratedParameter.Location.QUERY))
    assertEquals(listOf(cookie), auth.securityParameters(GeneratedParameter.Location.COOKIE))
    assertNull(auth.schemeOrNull("missing"))
  }

  @Test
  fun `overlays policy metadata without losing base maps`() {
    val base =
      GeneratedPolicy(
        timeout = "PT1S",
        retry = mapOf("maxRetries" to "2", "delay" to "PT100MS"),
        serverRateLimit = mapOf("value" to "20"),
        source = "api",
      )
    val override =
      GeneratedPolicy(
        retry = mapOf("maxRetries" to "3"),
        circuitBreaker = mapOf("requestVolumeThreshold" to "10"),
        source = "operation",
      )

    assertTrue(GeneratedPolicy().isEmpty)
    assertEquals(
      GeneratedPolicy(
        timeout = "PT1S",
        retry = mapOf("maxRetries" to "3", "delay" to "PT100MS"),
        circuitBreaker = mapOf("requestVolumeThreshold" to "10"),
        serverRateLimit = mapOf("value" to "20"),
        source = "operation",
      ),
      base.withOverrides(override),
    )
  }

  @Test
  fun `reads JAX-RS mode flags for target generation mode`() {
    val jaxrs =
      GeneratedJaxrs(
        asynchronous = true,
        reactive = false,
        sse = GeneratedModeFlag(client = true, server = false),
        jsonBody = GeneratedModeFlag(all = true, server = false),
        context = listOf("securityContext"),
      )

    assertTrue(jaxrs.isAsynchronous)
    assertFalse(jaxrs.isReactive)
    assertTrue(jaxrs.sseEnabled(GenerationMode.Client))
    assertFalse(jaxrs.sseEnabled(GenerationMode.Server))
    assertTrue(jaxrs.jsonBodyEnabled(GenerationMode.Client))
    assertFalse(jaxrs.jsonBodyEnabled(GenerationMode.Server))
    assertEquals(listOf("securityContext"), jaxrs.contextParameters)
  }

  private fun api(auth: GeneratedAuth? = null) =
    GeneratedApi(
      name = "Example",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml"),
      auth = auth,
    )

  private fun service(auth: GeneratedAuth? = null) =
    GeneratedService(
      name = "Projects",
      auth = auth,
    )

  private fun operation(auth: GeneratedAuth? = null) =
    GeneratedOperation(
      id = "fetch",
      method = "GET",
      path = "/projects",
      auth = auth,
    )

  private fun auth(name: String) =
    GeneratedAuth(
      schemes = listOf(name),
      requirements = listOf(GeneratedSecurityRequirement(listOf(name))),
    )

  private fun parameter(
    name: String,
    location: GeneratedParameter.Location,
  ) = GeneratedParameter(
    name = name,
    location = location,
    type = GeneratedTypeRef.scalar("string"),
  )
}
