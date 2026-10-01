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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.tools.scopedPolicyApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class ScopedPolicyIrTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `preserves scoped policy declarations through source conversion and IR1`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi(frontend, directory)
    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    assertEquals(api, decoded)
    assertEquals("1", decoded.irVersion)
    for (operation in decoded.services.flatMap { it.operations }) {
      val policy = requireNotNull(operation.policy)
      assertEquals(1, policy.inherited.size)
      val client =
        policy.resolve(
          GenerationContext(GenerationMode.Client, "internal"),
        ) { first, second -> first.merge(second) }!!
      assertEquals(GeneratedPolicyDuration(2), client.timeout?.value)
      assertEquals(1, client.retry?.value?.maxRetries)
      assertEquals(GeneratedPolicyDuration(nanos = 1), client.retry?.value?.delay)
      assertEquals(emptyList<GeneratedExceptionRef>(), client.retry?.value?.abortOn)
      assertEquals(3, client.rateLimit?.value?.value)
      val server = policy.resolve(GenerationContext(GenerationMode.Server)) { first, second -> first.merge(second) }!!
      assertEquals(3, server.retry?.value?.maxRetries)
      assertEquals(7, server.rateLimit?.value?.value)
    }
  }

  @Test
  fun `tag policies merge disjoint scopes before path and operation overrides`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("tags.yaml")
    val yaml =
      """
      openapi: 3.1.0
      info: {title: Policies, version: 1.0.0}
      tags:
        - name: first
          x-sunday-policy:
            client: {timeout: PT5S, retry: {maxRetries: 3}}
            profiles: {internal: {client: {timeout: PT9S}}}
        - name: second
          x-sunday-policy:
            client: {retry: {delay: PT1S}}
      paths:
        /items:
          x-sunday-policy:
            all: {timeout: PT2S}
          get:
            tags: [first, second]
            x-sunday-policy:
              all: {retry: {maxRetries: 1}}
            responses: {'204': {description: OK}}
      """.trimIndent()
    source.writeText(yaml)
    val policy =
      OpenApiToGeneratedApi()
        .convert(source.toUri())
        .services
        .single()
        .operations
        .single()
        .policy!!
    val resolved =
      policy.resolve(
        GenerationContext(GenerationMode.Client, "internal"),
      ) { first, second -> first.merge(second) }!!
    assertEquals(GeneratedPolicyDuration(2), resolved.timeout?.value)
    assertEquals(1, resolved.retry?.value?.maxRetries)
    assertEquals(GeneratedPolicyDuration(1), resolved.retry?.value?.delay)
    source.writeText(yaml.replace("retry: {delay: PT1S}", "retry: {maxRetries: 2}"))
    assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
  }

  @Test
  fun `composition preserves framing policies and rejects conflicting definitions`() {
    val policy =
      GeneratedPolicy(
        client = GeneratedPolicyValues(timeout = GeneratedPolicySetting(value = GeneratedPolicyDuration(1))),
      )

    fun fragment(
      kind: GeneratedSourceSpec.Kind,
      local: GeneratedPolicy?,
    ) = GeneratedApiFragment(
      apiId = GeneratedIdentity.native("policies"),
      api =
        GeneratedApi(
          name = "Policies",
          source = GeneratedSourceSpec(kind, "memory://$kind"),
          services =
            listOf(
              GeneratedService(
                name = "Events",
                operations =
                  listOf(
                    GeneratedOperation(
                      id = "events",
                      method =
                        if (kind ==
                          GeneratedSourceSpec.Kind.OPENAPI
                        ) {
                          "GET"
                        } else {
                          "SUBSCRIBE"
                        },
                      path = "/events",
                      responses =
                        listOf(
                          GeneratedResponse(
                            type = GeneratedTypeRef.scalar("string"),
                            mediaTypes = listOf("text/event-stream"),
                          ),
                        ),
                      streaming = GeneratedStreaming(GeneratedStreaming.Kind.EVENT_STREAM),
                      policy = local,
                    ),
                  ),
              ),
            ),
        ),
    )
    val framing = fragment(GeneratedSourceSpec.Kind.OPENAPI, policy)
    val stream = fragment(GeneratedSourceSpec.Kind.ASYNCAPI, null)
    for (sources in listOf(listOf(framing, stream), listOf(stream, framing))) {
      val composed = GeneratedApiComposer().compose(sources)
      assertEquals(
        policy,
        composed.services
          .single()
          .operations
          .single()
          .policy,
      )
      assertEquals(composed, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composed)))
    }
    val conflict = GeneratedPolicy(all = GeneratedPolicyValues(timeout = GeneratedPolicySetting(enabled = false)))
    assertThrows(GeneratedApiCompositionException::class.java) {
      GeneratedApiComposer().compose(listOf(framing, fragment(GeneratedSourceSpec.Kind.ASYNCAPI, conflict)))
    }
    assertThrows(GeneratedApiCompositionException::class.java) {
      GeneratedApiComposer().compose(
        listOf(
          framing.copy(api = framing.api.copy(tags = listOf(GeneratedTag("shared", policy = policy)))),
          stream.copy(api = stream.api.copy(tags = listOf(GeneratedTag("shared", policy = conflict)))),
        ),
      )
    }
  }

  @Test
  fun `IR preserves explicit disable empty lists and zero members`() {
    val policy =
      GeneratedPolicy(
        all =
          GeneratedPolicyValues(
            timeout = GeneratedPolicySetting(enabled = false),
            retry = GeneratedPolicySetting(value = GeneratedPolicyValues.Retry(maxRetries = 0, retryOn = emptyList())),
            circuitBreaker =
              GeneratedPolicySetting(
                value = GeneratedPolicyValues.CircuitBreaker(failureRatio = 0.0, skipOn = emptyList()),
              ),
          ),
      )
    val api =
      GeneratedApi(
        name = "Policies",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://policies"),
        services =
          listOf(
            GeneratedService(
              name = "Items",
              operations = listOf(GeneratedOperation(id = "get", method = "GET", path = "/", policy = policy)),
            ),
          ),
      )
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
  }

  @Test
  fun `IR rejects removed policy fields instead of silently discarding them`(
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi("openapi", directory)
    val yaml = GeneratedApiYaml.writeString(api)
    for (field in listOf("timeout: PT5S", "clientRateLimit: {}", "serverRateLimit: {}", "source: old")) {
      val malformed = yaml.replace("policy:\n", "policy:\n      $field\n")
      assertThrows(
        com.fasterxml.jackson.databind.JsonMappingException::class.java,
      ) { GeneratedApiYaml.readString(malformed) }
    }
  }
}
