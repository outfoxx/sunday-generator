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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiCompositionException
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedJaxrs
import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedSseServerResponseMode
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@KotlinTest
@Tag("events")
@OptIn(ExperimentalCompilerApi::class)
class KotlinJAXRSSseServerResponseModeTest {
  @TempDir lateinit var tempDir: Path

  @ParameterizedTest
  @ValueSource(strings = ["api.yaml", "api.raml", "events.yaml", "events3.yaml"])
  fun `server overrides compile for every source frontend`(file: String) {
    val api = export("sse-response-modes/$file")
    val roundTrip = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    assertEquals(
      api.services.flatMap { it.operations }.map { it.jaxrs?.sseServerResponseMode },
      roundTrip.services.flatMap { it.operations }.map { it.jaxrs?.sseServerResponseMode },
    )
    val output = compile(roundTrip)
    assertTrue(output.contains("fun typedEvents(): Multi<String>"), output)
    assertTrue(output.contains("fun envelopeEvents(): Multi<OutboundSseEvent>"), output)
    assertTrue(output.contains("RestMulti<ByteArray>"), output)
    assertFalse(output.contains("SseEventSink"), output)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `composed framing retains raw override and typed payload in either source order`(reverse: Boolean) {
    val paths = listOf("sse-response-modes/framing.yaml", "asyncapi/ir/event-stream-payload.yaml")
    val api = export(*(if (reverse) paths.reversed() else paths).toTypedArray())
    assertEquals(
      GeneratedSseServerResponseMode.RAW,
      api.services
        .single()
        .operations
        .single()
        .jaxrs
        ?.sseServerResponseMode,
    )
    val output = compile(api)
    assertTrue(output.contains("RestMulti<ByteArray>"), output)
    assertTrue(output.contains("subscriberId: String"), output)
    assertTrue(output.contains("ProjectEvent"), output)
    val client = compile(api, client = true)
    assertTrue(client.contains("Multi<ProjectEvent>"), client)
    assertFalse(client.contains("RestMulti"), client)
  }

  @ParameterizedTest
  @ValueSource(strings = ["api.yaml", "api.raml", "events.yaml", "events3.yaml"])
  fun `server options do not change client output`(file: String) {
    val api = export("sse-response-modes/$file")
    val withoutModes =
      api.mapOperations {
        it.copy(
          jaxrs =
            it.jaxrs?.copy(sseServerResponseMode = null)?.takeUnless { value ->
              value ==
                GeneratedJaxrs()
            },
        )
      }
    val expected = compile(withoutModes, client = true)
    assertEquals(expected, compile(api, client = true, mode = GeneratedSseServerResponseMode.RAW))
  }

  @ParameterizedTest
  @ValueSource(strings = ["javax", "jakarta"])
  fun `portable targets retain their namespace and sink API for envelopes`(target: String) {
    val api =
      export("sse-response-modes/api.yaml").mapOperations {
        it.copy(jaxrs = it.jaxrs?.copy(sseServerResponseMode = null))
      }
    val output = compile(api, target = target, mode = GeneratedSseServerResponseMode.OUTBOUND_EVENT, coroutines = true)
    assertTrue(output.contains("import $target.ws.rs.sse.SseEventSink"), output)
    assertTrue(output.contains("import $target.ws.rs.sse.Sse"), output)
    assertFalse(output.contains("Multi<"), output)
  }

  @Test
  fun `global raw default honors typed overrides and preserves ordinary media annotations`() {
    val api =
      export("sse-response-modes/api.yaml").mapOperations {
        if (it.id == "envelopeEvents") it.copy(jaxrs = null) else it
      }
    val output = compile(api, mode = GeneratedSseServerResponseMode.RAW)
    assertTrue(output.contains("fun envelopeEvents(): RestMulti<ByteArray>"), output)
    assertTrue(output.contains("fun typedEvents(): Multi<String>"), output)
    assertTrue(output.contains("@Produces(value = [\"application/json\"])"), output)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `raw routes compile without inherited media defaults through aggregate resources`(adapters: Boolean) {
    val api = export("sse-response-modes/api.yaml")
    val service = api.services.single()
    val (valueOperations, eventOperations) = service.operations.partition { it.id == "jsonValue" }
    val grouped =
      api.copy(
        services =
          listOf(
            service.copy(name = "Events", group = "Events", operations = eventOperations),
            service.copy(name = "Values", group = "Values", operations = valueOperations),
          ),
      )
    val output = compile(grouped, aggregate = true, adapters = adapters)
    assertTrue(output.contains("RestMulti<ByteArray>"), output)
    assertTrue(output.contains("Multi<OutboundSseEvent>"), output)
    val suffix = if (adapters) "Resource" else ""
    val aggregate = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/AllAPI$suffix.kt")
    assertFalse(aggregate.contains("@Produces"), aggregate)
    val events = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/EventsAPI$suffix.kt")
    val declaration = if (adapters) "class EventsAPI" else "interface EventsAPI"
    assertFalse(events.substringBefore(declaration).contains("@Produces"), events)
    val values = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/ValuesAPI$suffix.kt")
    assertTrue(values.contains("@Produces(value = [\"application/json\"])"), values)
  }

  @Test
  fun `outbound default applies to inferred and discriminated streams`() {
    val api =
      export("sse-response-modes/api.yaml").mapOperations {
        it.copy(
          jaxrs = null,
          streaming = it.streaming?.copy(eventMode = GeneratedStreaming.EventMode.DISCRIMINATED),
        )
      }
    val output = compile(api, mode = GeneratedSseServerResponseMode.OUTBOUND_EVENT)
    assertTrue(output.contains("fun typedEvents(): Multi<OutboundSseEvent>"), output)
    assertTrue(output.contains("fun jsonEvents(): Multi<OutboundSseEvent>"), output)
  }

  @Test
  fun `unsupported combinations fail before emission`() {
    val api = export("sse-response-modes/api.yaml")
    assertThrows(GenerationException::class.java) { compile(api, target = "javax") }
    val async =
      api.mapOperations {
        if (it.id == "rawEvents") it.copy(jaxrs = it.jaxrs?.copy(asynchronous = true)) else it
      }
    assertThrows(GenerationException::class.java) { compile(async) }
    val ordinary =
      api.mapOperations {
        if (it.id ==
          "jsonValue"
        ) {
          it.copy(jaxrs = GeneratedJaxrs(sseServerResponseMode = GeneratedSseServerResponseMode.RAW))
        } else {
          it
        }
      }
    assertThrows(GenerationException::class.java) { compile(ordinary) }
  }

  @Test
  fun `conflicting composed modes and unknown source values are rejected`() {
    val payload = requireNotNull(javaClass.getResource("/asyncapi/ir/event-stream-payload.yaml")).readText()
    val conflicting = tempDir.resolve("events.yaml")
    Files.writeString(
      conflicting,
      payload.replace(
        "operationId: streamProjectEvents",
        "operationId: streamProjectEvents\n      x-sunday-jaxrs: {sse-server-response-mode: outbound-event}",
      ),
    )
    val framing = requireNotNull(javaClass.getResource("/sse-response-modes/framing.yaml")).toURI()
    assertThrows(GeneratedApiCompositionException::class.java) {
      GeneratedApiIrExporter().export(listOf(framing, conflicting.toUri()))
    }
    val invalid = tempDir.resolve("invalid.yaml")
    Files.writeString(
      invalid,
      requireNotNull(
        javaClass.getResource("/sse-response-modes/api.yaml"),
      ).readText().replace("sse-server-response-mode: raw", "sse-server-response-mode: typo"),
    )
    assertThrows(IllegalArgumentException::class.java) { GeneratedApiIrExporter().export(invalid.toUri()) }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `legacy flag only preserves source and sink defaults while server modes take precedence`(coroutines: Boolean) {
    val api = export("raml/resource-gen/res-sse.raml")
    val baseline = compile(api, coroutines = coroutines)
    assertTrue(baseline.contains("SseEventSink") != coroutines, baseline)
    assertTrue(baseline.contains("suspend fun fetchEvents()") == coroutines, baseline)
    val client = compile(api, client = true, coroutines = coroutines)
    assertTrue(client.contains("fun fetchEvents(): SseEventSource"), client)
    for (mode in listOf(GeneratedSseServerResponseMode.OUTBOUND_EVENT, GeneratedSseServerResponseMode.RAW)) {
      val output = compile(api, mode = mode, coroutines = coroutines)
      val responseType =
        if (mode ==
          GeneratedSseServerResponseMode.RAW
        ) {
          "RestMulti<ByteArray>"
        } else {
          "Multi<OutboundSseEvent>"
        }
      assertTrue(output.contains("fun fetchEvents(): $responseType"), output)
      assertFalse(output.contains("SseEventSink"), output)
      assertFalse(output.contains("suspend fun fetchEvents()"), output)
      assertEquals(client, compile(api, client = true, mode = mode, coroutines = coroutines))
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `legacy simple streams retain envelopes and typed overrides restore the legacy API`(coroutines: Boolean) {
    val source = export("sse-response-modes/api.yaml")
    val service = source.services.single()
    val operation = service.operations.single { it.id == "typedEvents" }
    val legacy = GeneratedJaxrs(sse = GeneratedModeFlag(all = true))
    val api =
      source.copy(
        services =
          listOf(
            service.copy(
              operations =
                listOf(
                  operation.copy(
                    jaxrs = legacy,
                    streaming =
                      GeneratedStreaming(
                        GeneratedStreaming.Kind.EVENT_STREAM,
                        GeneratedStreaming.EventMode.SIMPLE,
                      ),
                  ),
                ),
            ),
          ),
      )
    val baseline = compile(api, coroutines = coroutines)
    assertTrue(baseline.contains("Multi<OutboundSseEvent>"), baseline)
    assertEquals(!coroutines, baseline.contains("SseEventSink"), baseline)
    val client = compile(api, client = true, mode = GeneratedSseServerResponseMode.RAW, coroutines = coroutines)
    assertTrue(client.contains("fun typedEvents(): Multi<InboundSseEvent>"), client)
    val outbound = compile(api, mode = GeneratedSseServerResponseMode.OUTBOUND_EVENT, coroutines = coroutines)
    assertTrue(outbound.contains("fun typedEvents(): Multi<OutboundSseEvent>"), outbound)
    assertFalse(outbound.contains("SseEventSink"), outbound)
    val typed =
      api.mapOperations {
        it.copy(jaxrs = legacy.copy(sseServerResponseMode = GeneratedSseServerResponseMode.TYPED))
      }
    assertEquals(baseline, compile(typed, mode = GeneratedSseServerResponseMode.RAW, coroutines = coroutines))
    val discriminated =
      api.mapOperations {
        it.copy(streaming = it.streaming?.copy(eventMode = GeneratedStreaming.EventMode.DISCRIMINATED))
      }
    val payload = compile(discriminated, coroutines = coroutines)
    assertTrue(payload.contains("Multi<String>"), payload)
    assertFalse(payload.contains("OutboundSseEvent"), payload)
  }

  @ParameterizedTest
  @ValueSource(strings = ["012", "021", "102", "120", "201", "210"])
  fun `conflicting OpenAPI framing modes fail with an AsyncAPI payload in every source order`(order: String) {
    for (mode in listOf(GeneratedSseServerResponseMode.OUTBOUND_EVENT, GeneratedSseServerResponseMode.TYPED)) {
      val sources = framingSources(mode)
      val error =
        assertThrows(GeneratedApiCompositionException::class.java) {
          GeneratedApiIrExporter().export(order.map { sources[it.digitToInt()] })
        }
      assertEquals("Conflicting SSE response modes for event stream '/events'", error.message)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `compatible OpenAPI framing modes preserve raw server output in every source order`(explicit: Boolean) {
    val sources = framingSources(GeneratedSseServerResponseMode.RAW.takeIf { explicit })
    for (order in listOf("012", "021", "102", "120", "201", "210")) {
      val api = GeneratedApiIrExporter().export(order.map { sources[it.digitToInt()] })
      assertEquals(
        GeneratedSseServerResponseMode.RAW,
        api.services
          .single()
          .operations
          .single()
          .jaxrs
          ?.sseServerResponseMode,
      )
      val output = compile(api)
      assertTrue(output.contains("RestMulti<ByteArray>"), output)
      assertTrue(output.contains("subscriberId: String"), output)
      assertTrue(output.contains("ProjectEvent"), output)
    }
  }

  private fun framingSources(secondMode: GeneratedSseServerResponseMode?): List<URI> {
    val framing = requireNotNull(javaClass.getResource("/sse-response-modes/framing.yaml"))
    val second = tempDir.resolve("second-framing.yaml")
    val setting = "      x-sunday-jaxrs: {sse-server-response-mode: raw}"
    val replacement = secondMode?.let { "      x-sunday-jaxrs: {sse-server-response-mode: ${it.value}}" }.orEmpty()
    Files.writeString(second, framing.readText().replace(setting, replacement))
    return listOf(
      framing.toURI(),
      second.toUri(),
      requireNotNull(javaClass.getResource("/asyncapi/ir/event-stream-payload.yaml")).toURI(),
    )
  }

  private fun GeneratedApi.mapOperations(transform: (GeneratedOperation) -> GeneratedOperation): GeneratedApi =
    copy(services = services.map { it.copy(operations = it.operations.map(transform)) })

  private fun export(vararg paths: String): GeneratedApi =
    GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
      .export(paths.map { requireNotNull(javaClass.getResource("/$it")).toURI() })

  private fun compile(
    api: GeneratedApi,
    client: Boolean = false,
    target: String = "quarkus",
    mode: GeneratedSseServerResponseMode = GeneratedSseServerResponseMode.TYPED,
    coroutines: Boolean = false,
    aggregate: Boolean = false,
    adapters: Boolean = true,
  ): String {
    val registry =
      KotlinTypeRegistry(
        null,
        if (client) GenerationMode.Client else GenerationMode.Server,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.ValidationConstraints) +
          if (target == "javax") emptySet() else setOf(KotlinTypeRegistry.Option.UseJakartaPackages),
      )
    val options =
      KotlinJAXRSOptions(
        coroutineFlowMethods = coroutines,
        coroutineServiceMethods = coroutines,
        reactiveResponseType = null,
        explicitSecurityParameters = false,
        baseUriMode = null,
        alwaysUseResponseReturn = true,
        placement = kotlinPlacement("io.test", "io.test"),
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
        quarkus = target == "quarkus",
        resourceAdapters = !client && adapters,
        aggregateServices = aggregate,
        aggregateServiceName = "AllAPI",
        sseServerResponseMode = mode,
      )
    KotlinJAXRSIrGenerator(api, registry, options).generateServiceTypes()
    val types = registry.buildTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypesResult(types).exitCode)
    return types.keys.filter { it == it.topLevelClassName() }.sortedBy { it.canonicalName }.joinToString("\n") {
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Kotlin,
        "${it.packageName.replace('.', '/')}/${it.simpleName}.kt",
      )
    }
  }
}
