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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedPayloadOption
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

@ExtendWith(ResourceExtension::class)
class GeneratedSchemaProblemsTest {
  @TempDir
  lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `optional problem types require an explicit compatible about blank default`(version: String) {
    for ((schema, expected) in listOf(
      "type: string" to "default of 'about:blank'",
      "type: string, default: 'https://example.com/custom'" to "default of 'about:blank'",
      "type: string, enum: ['about:blank']" to "default of 'about:blank'",
      "type: string, default: 'about:blank', enum: ['https://example.com/custom']" to "constraints",
      "type: string, default: 'about:blank', pattern: '^https:'" to "constraints",
      "type: string, default: 'about:blank', minLength: 12" to "constraints",
      "type: string, default: 'about:blank', maxLength: 10" to "constraints",
    )) {
      val source = directory.resolve("problem.yaml")
      source.writeText(
        """
        openapi: $version
        info: {title: Problems, version: '1'}
        paths: {}
        components:
          schemas:
            Problem:
              type: object
              required: [title, status]
              properties:
                type: {$schema}
                title: {type: string}
                status: {type: integer}
        """.trimIndent(),
      )
      val api = GeneratedApiIrExporter().export(source.toUri())
      val error =
        assertThrows(IllegalArgumentException::class.java) { GeneratedApiIndex(api).validateSchemaProblemResponses() }
      assertTrue(error.message.orEmpty().contains("Problem.type"), error.message)
      assertTrue(error.message.orEmpty().contains(expected), error.message)
    }
  }

  @Test
  fun `optional base types retain defaults while custom descendants can require type`(
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val index = GeneratedApiIndex(api)
    index.validateSchemaProblemResponses()
    val properties = GeneratedModelProperties(index::modelOrNull)
    for (name in listOf("HttpProblem", "FlexibleProblem", "BadRequestProblem", "NotFoundProblem", "CustomProblem")) {
      val model = api.models.single { it.name == name }
      val type = properties.fields(model).single { it.wireName == "type" }.effective
      assertEquals(name == "CustomProblem", type.required, name)
      if (!type.required) assertEquals("about:blank", type.defaultValue, name)
    }
  }

  @Test
  fun `standard status problems never register a global about blank subtype`(
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val index = GeneratedApiIndex(api)
    index.validateSchemaProblemResponses()
    val service = api.services.single()
    assertTrue(service.referencedSchemaProblems(index).isEmpty())
    for (response in service.operations
      .single()
      .responses
      .filter { it.status in listOf(400, 404) }) {
      val onlyOne = service.copy(operations = listOf(service.operations.single().copy(responses = listOf(response))))
      assertTrue(onlyOne.referencedSchemaProblems(index).isEmpty())
    }
  }

  @Test
  fun `conflicting response problem URIs fail regardless of response or operation order`(
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val service = api.services.single()
    val operation = service.operations.first()
    val responses =
      listOf("QueuedProblem", "RebuildingProblem").mapIndexed { index, name ->
        GeneratedResponse(status = 409 + index, type = GeneratedTypeRef.named(name))
      }
    val messages = mutableSetOf<String?>()
    for (ordered in listOf(responses, responses.reversed())) {
      for (operations in listOf(
        listOf(operation.copy(responses = ordered)),
        ordered.mapIndexed { index, response -> operation.copy(id = "read$index", responses = listOf(response)) },
      )) {
        val index = GeneratedApiIndex(api.copy(services = listOf(service.copy(operations = operations))))
        val error =
          assertThrows(
            IllegalArgumentException::class.java,
          ) {
            index.validateSchemaProblemResponses()
          }
        messages.add(error.message)
        for (part in listOf(service.name, "QueuedProblem", "RebuildingProblem", "distinct fixed type URIs")) {
          assertTrue(error.message.orEmpty().contains(part), error.message)
        }
        assertTrue(error.message.orEmpty().contains("https://example.com/problems/rebuilding"), error.message)
      }
    }
    assertEquals(1, messages.size)
  }

  @Test
  fun `repeated references to the same response problem share one registration`(
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val service = api.services.single()
    val type = GeneratedTypeRef.named("QueuedProblem")
    val response = GeneratedResponse(status = 409, type = type, payloads = listOf(GeneratedPayloadOption(type)))
    val repeated =
      service.copy(
        operations = service.operations.map { it.copy(responses = listOf(response, response.copy(status = 503))) },
      )
    val registered = repeated.referencedSchemaProblems(GeneratedApiIndex(api))
    assertEquals(listOf("QueuedProblem"), registered.values.map { it.name })
  }

  @Test
  fun `inline and named anyOf retain original inherited problem references`(
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val index = GeneratedApiIndex(api)
    index.validateSchemaProblemResponses()
    val service = api.services.single()
    for (name in listOf("readMissing", "readMissingNamed")) {
      val operation = service.operations.single { it.id == name }
      val problems = service.copy(operations = listOf(operation)).referencedSchemaProblems(index)
      assertEquals(setOf("TeamNotFoundProblem", "UserNotFoundProblem"), problems.values.map { it.name }.toSet())
      assertEquals(
        setOf("https://example.com/problems/team-not-found", "https://example.com/problems/user-not-found"),
        problems.keys,
      )
      problems.values.forEach { assertEquals(listOf("NotFoundProblem"), it.inherits.map { parent -> parent.name }) }
    }
  }

  @Test
  fun `unsupported problem union shapes fail with actionable diagnostics`(
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val union = api.models.single { it.name == "NotFoundProblems" }
    val team = union.aliases.first()
    val invalid =
      listOf(
        union.copy(aliases = listOf(team, GeneratedTypeRef.scalar("string"))) to "direct references",
        union.copy(aliases = listOf(team, team.copy(nullable = true))) to "direct references",
        union.copy(aliases = listOf(team, GeneratedTypeRef.named("NotFoundProblems"))) to "direct references",
        union.copy(aliases = listOf(team, GeneratedTypeRef.named("Metadata"))) to "direct references",
        union.copy(aliases = listOf(team, GeneratedTypeRef.named("Problem"))) to "non-leaf",
        union.copy(aliases = listOf(team, team)) to "distinct type URIs",
        union.copy(closed = true) to "cannot add constraints",
        union.copy(discriminator = "status") to "discriminator must map",
        union.copy(discriminatorMappings = emptyMap()) to "discriminator must map",
      )
    for ((replacement, message) in invalid) {
      val index = GeneratedApiIndex(api.copy(models = api.models.map { if (it == union) replacement else it }))
      val error = assertThrows(IllegalArgumentException::class.java) { index.validateSchemaProblemResponses() }
      assertTrue(error.message.orEmpty().contains(message), error.message)
    }
    val withoutType =
      api.copy(
        models =
          api.models.map { model ->
            if (model.name ==
              "TeamNotFoundProblem"
            ) {
              model.copy(properties = model.properties.filter { it.name != "type" })
            } else {
              model
            }
          },
      )
    val error =
      assertThrows(
        IllegalArgumentException::class.java,
      ) { GeneratedApiIndex(withoutType).validateSchemaProblemResponses() }
    assertTrue(error.message.orEmpty().contains("fixed type URI"), error.message)
    val ordinaryUnion =
      union.copy(
        aliases = listOf(GeneratedTypeRef.named("Metadata"), GeneratedTypeRef.scalar("string")),
      )
    GeneratedApiIndex(
      api.copy(
        models =
          api.models.map {
            if (it ==
              union
            ) {
              ordinaryUnion
            } else {
              it
            }
          },
      ),
    ).validateSchemaProblemResponses()
  }
}
