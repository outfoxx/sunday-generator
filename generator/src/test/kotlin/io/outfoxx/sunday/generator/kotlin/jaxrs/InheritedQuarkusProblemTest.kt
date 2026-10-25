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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.quarkiverse.resteasy.problem.HttpProblem
import io.quarkus.jackson.ObjectMapperCustomizer
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
@Tag("validation")
class InheritedQuarkusProblemTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inherited extensions survive the Quarkiverse serialization pipeline`(
    validation: Boolean,
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        GenerationMode.Server,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (validation) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
        problemLibrary = KotlinProblemLibrary.QUARKUS,
      )
    KotlinJAXRSIrGenerator(
      GeneratedApiIrExporter().export(listOf(source)),
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
        quarkus = true,
      ),
    ).generateServiceTypes()
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
                check(RuntimeException::class.java.isInstance(it))
                check(it.resourceKey == key)
                check(it.retryAfterSeconds == 1)
                check(it.statusCode == 409)
                check(it.detail == "Resource A is rebuilding")
              }
              """.trimIndent(),
            ),
          ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation("jakarta", result.classLoader) {
      val consumer = result.classLoader.loadClass("io.test.inherited.ConsumerKt")
      val create = consumer.getMethod("problem", String::class.java, Int::class.javaPrimitiveType)
      val problem = create.invoke(null, "A", 2) as HttpProblem
      assertEquals("RebuildingProblem", problem.javaClass.superclass.simpleName)
      assertEquals("Problem", problem.javaClass.superclass.superclass.simpleName)
      assertTrue(problem.parameters.containsKey("resourceKey"), "Missing child resourceKey in runtime parameters")
      assertEquals("A", problem.parameters["resourceKey"])
      assertEquals(1, problem.parameters["retryAfterSeconds"])
      assertEquals("trace-A", problem.parameters["traceId"])
      assertEquals(2, problem.parameters["queuePosition"])
      assertEquals("rebuilds", problem.parameters["queue-name"])
      assertTrue(problem.parameters.containsKey("nullableChild"))
      assertTrue(problem.parameters.containsKey("nullableParent"))
      assertFalse(problem.parameters.containsKey("optionalChild"))
      val mapper = jacksonObjectMapper()
      // Install the same package-private registrar that Quarkus discovers through CDI.
      val registrar =
        Class
          .forName("io.quarkiverse.resteasy.problem.jackson.JacksonProblemModuleRegistrar")
          .getDeclaredConstructor()
          .apply { isAccessible = true }
          .newInstance() as ObjectMapperCustomizer
      registrar.customize(mapper)
      val expected =
        mapper.readTree(
          """{"type":"https://example.com/problems/rebuilding","title":"Resource Rebuilding","status":409,
          "detail":"Resource A is rebuilding","traceId":"trace-A","resourceKey":"A","retryAfterSeconds":1,
          "nullableParent":null,"nullableChild":null,"metadata":{"region":"west"},
          "queuePosition":2,"queue-name":"rebuilds"}""",
        )
      problem.toResponse().use { response ->
        assertEquals(409, response.status)
        assertEquals("application/problem+json", response.mediaType.toString())
        for (value in listOf(problem, response.entity, HttpProblem.builder(problem).build())) {
          assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(value)))
        }
      }
      val decoded = mapper.readValue(expected.toString(), problem.javaClass)
      assertEquals(expected, mapper.readTree(mapper.writeValueAsBytes(decoded)))
      val second = create.invoke(null, "B", 3) as HttpProblem
      assertEquals("B", second.parameters["resourceKey"])
      assertEquals("A", problem.parameters["resourceKey"])
      val childJson =
        expected.deepCopy<ObjectNode>().apply {
          remove(listOf("queuePosition", "queue-name"))
          put("optionalChild", "present")
        }
      val child = result.classLoader.loadClass("io.test.inherited.RebuildingProblem")
      assertEquals(childJson, mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(childJson.toString(), child))))
      val rootJson =
        mapper.readTree(
          """{"type":"about:blank","title":"Failure","status":400,"traceId":"root","nullableParent":null}""",
        )
      val root = result.classLoader.loadClass("io.test.inherited.Problem")
      val decodedRoot = mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(rootJson.toString(), root)))
      assertEquals(rootJson.deepCopy<ObjectNode>().put("detail", "Something bad happened"), decodedRoot)
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
