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
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.zalando.problem.jackson.ProblemModule
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
@Tag("validation")
class KotlinInheritedProblemTest {
  companion object {
    @JvmStatic
    fun targets(): List<Arguments> =
      buildList {
        for (target in listOf("sunday", "jaxrs-client", "jaxrs-server", "quarkus")) {
          for (library in listOf(KotlinProblemLibrary.SUNDAY, KotlinProblemLibrary.ZALANDO)) {
            for (namespace in if (target == "quarkus") listOf("jakarta") else listOf("javax", "jakarta")) {
              for (validation in listOf(false, true)) add(Arguments.of(target, library, namespace, validation))
            }
          }
        }
      }
  }

  @ParameterizedTest
  @MethodSource("targets")
  fun `inherited extensions retain the same wire contract across Kotlin frameworks`(
    target: String,
    library: KotlinProblemLibrary,
    namespace: String,
    validation: Boolean,
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        if (target == "sunday" || target == "jaxrs-client") GenerationMode.Client else GenerationMode.Server,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (validation) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
        problemLibrary = library,
      )
    val api = GeneratedApiIrExporter().export(listOf(source))
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test.inherited", "io.test.inherited"),
          "http://example.com/",
          listOf("application/json"),
          "API",
        ),
      ).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(
        api,
        registry,
        KotlinJAXRSOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = false,
          reactiveResponseType = null,
          explicitSecurityParameters = false,
          baseUriMode = null,
          alwaysUseResponseReturn = false,
          placement = kotlinPlacement("io.test.inherited", "io.test.inherited"),
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = target == "quarkus",
        ),
      ).generateServiceTypes()
    }
    val result =
      compileTypesResult(
        registry.buildTypes(),
        additionalSources =
          listOf(
            SourceFile.kotlin(
              "Consumer.kt",
              """
              package io.test.inherited

              fun problem(key: String = "A", position: Int = 2): QueuedProblem = QueuedProblem(
                detail = "Resource A is rebuilding",
                traceId = "trace-A",
                resourceKey = key,
                nullableChild = null,
                metadata = Metadata(region = "west"),
                queuePosition = position,
              ).also {
                val baseDefaults = DefaultedProblem()
                check(baseDefaults.detail == "Something bad happened")
                check(baseDefaults.enabled == false && baseDefaults.count == 0)
                check(baseDefaults.note == "fallback")
                check(baseDefaults.location == java.net.URI.create("https://example.com/default"))
                check(baseDefaults.revision == 1)
                check(DefaultedProblem(note = null, enabled = true, count = 3).note == null)
                val defaulted = QueuedProblem(nullableChild = null, metadata = Metadata(region = "west"))
                check(defaulted.detail == "Resource A is rebuilding")
                check(defaulted.traceId == "trace-A")
                check(defaulted.resourceKey == "A")
                check(defaulted.queuePosition == 2)
                val overridden = QueuedProblem(detail = "Override", traceId = "trace-B", resourceKey = "B",
                  queuePosition = 3, nullableChild = null, metadata = Metadata(region = "east"))
                check(overridden.detail == "Override")
                check(overridden.traceId == "trace-B")
                check(overridden.resourceKey == "B")
                check(overridden.queuePosition == 3)
                check(Throwable::class.java.isInstance(it))
                check(it.resourceKey == key)
                check(it.retryAfterSeconds == 1)
                check(it.detail == "Resource A is rebuilding")
              }
              """.trimIndent() +
                if (target == "sunday" && library == KotlinProblemLibrary.SUNDAY) {
                  """

                  fun httpProblem(): Throwable {
                    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
                    server.createContext("/inherited-problems") { exchange ->
                      val body = ${'"'}${'"'}${'"'}$INHERITED_PROBLEM_JSON${'"'}${'"'}${'"'}.toByteArray()
                      exchange.responseHeaders.add("Content-Type", "application/problem+json")
                      exchange.sendResponseHeaders(409, body.size.toLong())
                      exchange.responseBody.use { it.write(body) }
                    }
                    server.start()
                    try {
                      io.outfoxx.sunday.jdk.JdkTransport(
                        io.outfoxx.sunday.URITemplate("http://127.0.0.1:" + server.address.port),
                        io.outfoxx.sunday.problems.SundayHttpProblem.Factory,
                      ).use { transport ->
                        val client = API(transport)
                        check(transport.registeredProblemTypes["https://example.com/problems/rebuilding"] == QueuedProblem::class)
                        try { kotlinx.coroutines.runBlocking { client.readResource().execute() } }
                        catch (problem: RebuildingProblem) { return problem }
                      }
                      error("Expected an HTTP problem")
                    } finally { server.stop(0) }
                  }
                  """.trimIndent()
                } else {
                  ""
                },
            ),
          ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val consumer = result.classLoader.loadClass("io.test.inherited.ConsumerKt")
      val create = consumer.getMethod("problem", String::class.java, Int::class.javaPrimitiveType)
      val problem = create.invoke(null, "A", 2) as Throwable
      assertEquals("RebuildingProblem", problem.javaClass.superclass.simpleName)
      assertEquals("Problem", problem.javaClass.superclass.superclass.simpleName)
      val mapper = jacksonObjectMapper()
      if (library == KotlinProblemLibrary.ZALANDO) {
        mapper.registerModule(ProblemModule())
        mapper.registerSubtypes(
          com.fasterxml.jackson.databind.jsontype.NamedType(
            problem.javaClass,
            "https://example.com/problems/rebuilding",
          ),
        )
      }
      val expected = mapper.readTree(INHERITED_PROBLEM_JSON)
      assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(problem)))
      val decoded = mapper.readValue(expected.toString(), problem.javaClass)
      assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(decoded)))
      if (target == "sunday" && library == KotlinProblemLibrary.SUNDAY) {
        val httpProblem = consumer.getMethod("httpProblem").invoke(null)
        assertEquals(problem.javaClass, httpProblem.javaClass)
        assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(httpProblem)))
      }
      val second = create.invoke(null, "B", 3) as Throwable
      assertEquals("B", mapper.readTree(mapper.writeValueAsBytes(second))["resourceKey"].asText())
      assertEquals("A", mapper.readTree(mapper.writeValueAsBytes(problem))["resourceKey"].asText())
      val childJson =
        expected.deepCopy<ObjectNode>().apply {
          remove(listOf("queuePosition", "queue-name"))
          put("optionalChild", "present")
        }
      val child = result.classLoader.loadClass("io.test.inherited.RebuildingProblem")
      if (library == KotlinProblemLibrary.ZALANDO) {
        mapper.registerSubtypes(
          com.fasterxml.jackson.databind.jsontype
            .NamedType(child, "https://example.com/problems/rebuilding"),
        )
      }
      assertEquals(childJson, mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(childJson.toString(), child))))
      val rootJson =
        mapper.readTree(
          """{"type":"about:blank","title":"Failure","status":400,"traceId":"root","nullableParent":null}""",
        )
      val root = result.classLoader.loadClass("io.test.inherited.Problem")
      if (library == KotlinProblemLibrary.ZALANDO) {
        mapper.registerSubtypes(
          com.fasterxml.jackson.databind.jsontype
            .NamedType(root, "about:blank"),
        )
      }
      val decodedRoot = mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(rootJson.toString(), root)))
      assertEquals(rootJson.deepCopy<ObjectNode>().put("detail", "Something bad happened"), decodedRoot)
      if (library == KotlinProblemLibrary.ZALANDO) {
        mapper.registerSubtypes(
          com.fasterxml.jackson.databind.jsontype.NamedType(
            problem.javaClass,
            "https://example.com/problems/rebuilding",
          ),
        )
      }
      for (json in listOf(
        expected.toString().replace("\"resourceKey\":\"A\"", "\"resourceKey\":\"\""),
        expected.toString().replace("\"retryAfterSeconds\":1", "\"retryAfterSeconds\":2"),
        expected.toString().replace("\"queuePosition\":2", "\"queuePosition\":0"),
        expected.toString().replace("\"region\":\"west\"", "\"region\":\"\""),
        expected.toString().replace("\"nullableChild\":null,", ""),
        expected.toString().replace("\"resourceKey\":\"A\",", ""),
        expected.toString().replace("\"queuePosition\":2,", ""),
        expected.toString().replace("\"detail\":\"Resource A is rebuilding\"", "\"detail\":null"),
      )) {
        assertThrows(JsonMappingException::class.java) { mapper.readValue(json, problem.javaClass) }
      }
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) { create.invoke(null, "", 2) }
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) { create.invoke(null, "A", 0) }
    }
  }
}
