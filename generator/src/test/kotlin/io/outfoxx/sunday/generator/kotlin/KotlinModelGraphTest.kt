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

package io.outfoxx.sunday.generator.kotlin

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.tools.assertReusableDiscriminatorMappingRoundTrips
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.reusableDiscriminatorMappingApi
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinModelGraphTest {
  @ParameterizedTest
  @CsvSource("raml,javax", "openapi,jakarta", "asyncapi,javax", "composed,jakarta")
  fun `native model field views preserve presence and shared references but reject cycles`(
    frontend: String,
    namespace: String,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve(if (frontend == "raml") "api.raml" else "api.yaml")
    // TurnPost's mixed required/optional fields exercise line wrapping inside the generated field view.
    val schema =
      """
      components:
        schemas:
          Node:
            type: object
            required: [child-nodes]
            properties:
              child-nodes: {type: array, items: {${'$'}ref: '#/components/schemas/Node'}}
          ComposeDiegesisFrame:
            type: object
            required: [nested]
            properties:
              nested: {type: boolean}
              nestedRefs: {type: array, items: {${'$'}ref: '#/components/schemas/Node'}}
              provenance: {type: string}
      """.trimIndent()
    source.writeText(
      when (frontend) {
        "raml" ->
          """
          #%RAML 1.0
          title: Model graph
          types:
            Node:
              properties:
                child-nodes: Node[]
            ComposeDiegesisFrame:
              properties:
                nested: boolean
                nestedRefs?: Node[]
                provenance?: string
          """.trimIndent()
        "asyncapi" ->
          """
          asyncapi: 3.0.0
          info: {title: Model graph, version: 1.0.0}
          channels:
            nodes:
              address: nodes
              messages:
                node: {payload: {${'$'}ref: '#/components/schemas/Node'}}
                frame: {name: ComposeDiegesisFrame, payload: {${'$'}ref: '#/components/schemas/ComposeDiegesisFrame'}}
          operations:
            receiveNode:
              action: receive
              channel: {${'$'}ref: '#/channels/nodes'}
              messages: [{${'$'}ref: '#/channels/nodes/messages/node'}]
            receiveFrame:
              action: receive
              channel: {${'$'}ref: '#/channels/nodes'}
              messages: [{${'$'}ref: '#/channels/nodes/messages/frame'}]
          """.trimIndent() + "\n" + schema
        else -> "openapi: 3.1.0\ninfo: {title: Model graph, version: 1.0.0}\npaths: {}\n" + schema
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      events.writeText("asyncapi: 3.0.0\ninfo: {title: Model graph, version: 1.0.0}\nchannels: {}\noperations: {}\n")
      sources += events.toUri()
    }
    val exported = GeneratedApiIrExporter().export(sources)
    val api =
      exported.copy(
        models =
          exported.models +
            reusableDiscriminatorMappingApi().models.map { model ->
              if (model.name in setOf("EventEnvelope", "NotificationEventEnvelope")) {
                model.copy(
                  properties =
                    model.properties +
                      buildList {
                        add(
                          GeneratedModelProperty(
                            "occurredAt",
                            GeneratedTypeRef.scalar("datetime"),
                            required = false,
                            validation =
                              if (model.name ==
                                "NotificationEventEnvelope"
                              ) {
                                mapOf("pattern" to "^2026-")
                              } else {
                                emptyMap()
                              },
                          ),
                        )
                        if (model.name == "NotificationEventEnvelope") {
                          add(
                            GeneratedModelProperty(
                              "data",
                              GeneratedTypeRef.scalar("string"),
                              required = false,
                              validation = mapOf("minLength" to "2"),
                            ),
                          )
                        }
                      },
                )
              } else {
                model
              }
            },
      )
    for (target in listOf("sunday", "jaxrs")) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          buildSet {
            add(KotlinTypeRegistry.Option.ImplementModel)
            add(KotlinTypeRegistry.Option.JacksonAnnotations)
            add(KotlinTypeRegistry.Option.ValidationConstraints)
            if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
          },
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (target == "sunday") {
        KotlinSundayIrGenerator(
          api,
          registry,
          KotlinSundayOptions("io.test.service", "https://example.test", emptyList(), "API"),
        ).generateServiceTypes()
      } else {
        KotlinJAXRSIrGenerator(
          api,
          registry,
          KotlinJAXRSOptions(
            false,
            false,
            null,
            false,
            null,
            false,
            "io.test.service",
            "https://example.test",
            emptyList(),
            "API",
            false,
          ),
        ).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      withNativeBeanValidation(namespace, result.classLoader) {
        val node = result.classLoader.loadClass("io.test.Node")
        val constructor = node.getConstructor(List::class.java)
        val shared = constructor.newInstance(emptyList<Any>())
        val children = mutableListOf(shared, shared)
        val root = constructor.newInstance(children)
        for (mode in listOf("Request", "Response")) {
          assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, root, mode))
        }
        children.clear()
        children += root
        for (mode in listOf("Request", "Response")) {
          assertTrue(nativeConstraintPaths(namespace, root, mode).contains("child-nodes[0]"))
        }
        children.clear()
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, root, "Request"))
        val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        val frameType = result.classLoader.loadClass("io.test.ComposeDiegesisFrame")
        val frame = mapper.readValue("""{"nested":true}""", frameType)
        assertEquals(
          mapOf("nested" to true),
          frameType.getMethod("validationFields").invoke(frame),
        )
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, frame, "Request"))
        mapper.readValue("""{"type":"event.one","data":"x"}""", result.classLoader.loadClass("io.test.EventOne"))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue(
            """{"event":{"type":"event.one","data":"x"}}""",
            result.classLoader.loadClass("io.test.Notification"),
          )
        }
        val notificationType = result.classLoader.loadClass("io.test.Notification")
        val timed =
          mapper.readValue(
            """{"event":{"type":"event.one","data":"known","occurredAt":"2026-06-25T12:00:00Z"}}""",
            notificationType,
          )
        val encoded = mapper.writeValueAsString(timed)
        assertTrue(mapper.readTree(encoded)["event"]["occurredAt"].isNumber)
        assertEquals(encoded, mapper.writeValueAsString(mapper.readValue(encoded, notificationType)))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue(
            """{"event":{"type":"event.one","data":"known","occurredAt":"2025-06-25T12:00:00Z"}}""",
            notificationType,
          )
        }
        assertReusableDiscriminatorMappingRoundTrips(result)
      }
    }
  }
}
