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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.emit.clientSecurity
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class GeneratedSourceEnvironmentProjectionTest {
  private val mapper = ObjectMapper(YAMLFactory())
  private val mapType = object : TypeReference<Map<String, Any?>>() {}
  private val external = GenerationContext(GenerationMode.Client, "external")

  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "asyncapi3", "composed"])
  fun `source projection preserves wire requirements and all client security profiles`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val original = scopedSecurityApi(frontend, directory)
    val sources = Files.list(directory).use { it.sorted().toList() }
    val projected =
      sources.map { source ->
        val input = mapper.readValue(source.toFile(), mapType)
        val result = GeneratedSourceEnvironmentProjection.project(input, external)
        assertEquals(input, mapper.readValue(source.toFile(), mapType))
        val yaml = mapper.writeValueAsString(result)
        assertTrue(yaml.contains("identity.internal"))
        assertFalse(yaml.contains("verifier"))
        assertTrue(yaml.contains("internal:"))
        directory.resolve("public-${source.fileName}").also { Files.writeString(it, yaml) }.toUri()
      }
    val output = GeneratedApiIrExporter().export(projected)
    original.services.zip(output.services).forEach { (before, after) ->
      before.operations.zip(after.operations).forEach { (operation, projectedOperation) ->
        val expected = original.clientSecurity(before, operation, external)!!
        val actual = output.clientSecurity(after, projectedOperation, external)!!
        assertEquals(expected.requirement, actual.requirement)
        assertEquals(expected.bindings, actual.bindings)
      }
    }
  }

  @Test
  fun `scope merging preserves replacements disabled policies and literal schema values`() {
    val example = mapOf("x-sunday-policy" to "literal payload")
    val input =
      mapOf(
        "x-sunday-policy" to
          mapOf(
            "all" to mapOf("retry" to mapOf("maxRetries" to 3, "retryOn" to listOf("java.io.IOException"))),
            "client" to mapOf("timeout" to "PT5S"),
            "server" to mapOf("rateLimit" to mapOf("value" to 4, "window" to "PT1S")),
            "profiles" to
              mapOf(
                "external" to
                  mapOf("client" to mapOf("retry" to mapOf("retryOn" to emptyList<String>()), "timeout" to false)),
              ),
          ),
        "x-sunday-security" to
          mapOf(
            "all" to mapOf("alternative" to mapOf("first" to listOf("read"))),
            "profiles" to mapOf("external" to mapOf("client" to mapOf("alternative" to emptyMap<String, Any>()))),
          ),
        "components" to
          mapOf(
            "schemas" to mapOf("x-sunday-policy" to mapOf("type" to "object", "example" to example)),
          ),
        "security" to listOf(mapOf("x-sunday-security" to emptyList<String>())),
      )
    val output = GeneratedSourceEnvironmentProjection.project(input, external)
    val policy =
      GeneratedPolicyReader
        .read(
          output["x-sunday-policy"],
          "policy",
        ).resolve(external) { a, b -> a.merge(b) }!!
    assertEquals(3, policy.retry!!.value!!.maxRetries)
    assertEquals(emptyList<Any>(), policy.retry.value.retryOn)
    assertEquals(false, policy.timeout!!.enabled)
    assertNull(policy.rateLimit)
    val selection =
      GeneratedSecurityReader
        .selection(
          output["x-sunday-security"],
          "security",
        ).resolve(external) { a, b -> a.merge(b) }!!
    assertTrue(selection.alternative!!.schemes.isEmpty())
    assertEquals(input["components"], output["components"])
    assertEquals(input["security"], output["security"])
  }

  @Test
  fun `missing selected providers remain actionable rather than reverting to manual credentials`() {
    val scheme =
      mapOf(
        "type" to "http",
        "scheme" to "bearer",
        "x-sunday-security" to mapOf("server" to mapOf("provider" to "internal")),
      )
    val output =
      GeneratedSourceEnvironmentProjection.project(
        mapOf(
          "components" to mapOf("securitySchemes" to mapOf("token" to scheme)),
        ),
        external,
      )
    val binding =
      mapper
        .valueToTree<com.fasterxml.jackson.databind.JsonNode>(
          output,
        ).at("/components/securitySchemes/token/x-sunday-security")
    assertTrue(binding.isObject)
    assertTrue(binding.isEmpty)
  }

  @Test
  fun `profiled client projection retains alternatives and rejects removed policy members`() {
    val projected =
      GeneratedSourceEnvironmentProjection.project(
        mapOf(
          "x-sunday-security" to
            mapOf(
              "profiles" to mapOf("external" to mapOf("client" to mapOf("alternative" to emptyMap<String, Any>()))),
            ),
        ),
        GenerationContext(GenerationMode.Client),
      )
    assertTrue(mapper.writeValueAsString(projected).contains("external:"))
    assertThrows(GenerationException::class.java) {
      GeneratedSourceEnvironmentProjection.project(mapOf("x-sunday-policy" to mapOf("clientRateLimit" to 3)), external)
    }
  }
}
