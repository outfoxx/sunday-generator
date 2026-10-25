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
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
class KotlinAboutBlankProblemTest {
  @ParameterizedTest
  @ValueSource(
    strings = ["sunday", "jaxrs-client", "jaxrs-server", "quarkus", "quarkus-models", "quarkus-custom-models"],
  )
  fun `standard status models compile across frameworks and use generic client errors`(
    target: String,
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        if (target in listOf("sunday", "jaxrs-client")) GenerationMode.Client else GenerationMode.Server,
        setOf(
          KotlinTypeRegistry.Option.ImplementModel,
          KotlinTypeRegistry.Option.JacksonAnnotations,
          KotlinTypeRegistry.Option.UseJakartaPackages,
        ),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val original = GeneratedApiIrExporter().export(source)
    val typeUri = if (target.contains("custom")) "urn:example:shared" else "about:blank"
    val api =
      original.copy(
        models =
          original.models.map { model ->
            model.copy(
              properties =
                model.properties.map { property ->
                  if (property.name == "type" && property.allowedValues != null) {
                    property.copy(allowedValues = listOf(typeUri))
                  } else {
                    property
                  }
                },
            )
          },
      )
    val placement = kotlinPlacement("io.test.blank", "io.test.blank")
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          placement,
          "https://example.test/",
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
          placement = placement,
          defaultProblemBaseUri = "https://example.test/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = target.startsWith("quarkus"),
        ),
      ).generateServiceTypes()
    }
    val selection =
      if (target.endsWith("models")) {
        GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem))
      } else {
        GeneratedOutputSelection()
      }
    val consumer =
      """
      package io.test.blank
      fun problems(): List<Any> = listOf(
        BadRequestProblem(detail = "Details", traceId = "trace"),
        NotFoundProblem(detail = "Details", traceId = "trace"),
      )
      """.trimIndent() +
        if (target == "sunday") {
          """

          fun httpProblems() {
            for ((status, title) in listOf(400 to "Bad Request", 404 to "Not Found", 503 to "Service Unavailable")) {
              for (explicit in listOf(false, true)) {
                val raw = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().createObjectNode()
                  .put("status", status).put("title", title).put("detail", "Details").put("traceId", "trace")
                raw.putObject("extra").putArray("nested").add(1).add(2)
                if (explicit) raw.put("type", "about:blank")
                val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
                server.createContext("/example") { exchange ->
                  val body = raw.toString().toByteArray()
                  exchange.responseHeaders.add("Content-Type", "application/problem+json")
                  exchange.sendResponseHeaders(status, body.size.toLong())
                  exchange.responseBody.use { it.write(body) }
                }
                server.start()
                try {
                  io.outfoxx.sunday.jdk.JdkTransport(
                    io.outfoxx.sunday.URITemplate("http://127.0.0.1:" + server.address.port),
                    io.outfoxx.sunday.problems.SundayHttpProblem.Factory,
                  ).use { transport ->
                    val client = API(transport)
                    check("about:blank" !in transport.registeredProblemTypes)
                    try {
                      kotlinx.coroutines.runBlocking { client.getExample().execute() }
                      error("Expected generic HTTP problem")
                    } catch (problem: io.outfoxx.sunday.problems.SundayHttpProblem) {
                      check(problem !is BadRequestProblem && problem !is NotFoundProblem)
                      check(problem.type.toString() == "about:blank")
                      check(problem.status == status && problem.title == title && problem.detail == "Details")
                      check(problem.extensions["traceId"] == "trace")
                      val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                      check(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(problem.extensions["extra"]) == raw["extra"])
                    }
                  }
                } finally { server.stop(0) }
              }
            }
          }
          """.trimIndent()
        } else {
          ""
        }
    val result =
      compileTypesResult(
        registry.buildTypes(selection),
        additionalSources = listOf(SourceFile.kotlin("Consumer.kt", consumer)),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val consumerClass = result.classLoader.loadClass("io.test.blank.ConsumerKt")
    val values = consumerClass.getMethod("problems").invoke(null) as List<*>
    val mapper = jacksonObjectMapper()
    for ((index, value) in values.withIndex()) {
      val raw = mapper.readTree(mapper.writeValueAsBytes(value))
      assertEquals(typeUri, raw["type"].asText())
      assertEquals(if (index == 0) 400 else 404, raw["status"].asInt())
      assertEquals(if (index == 0) "Bad Request" else "Not Found", raw["title"].asText())
      assertEquals("trace", raw["traceId"].asText())
      for (invalid in listOf(
        raw.deepCopy<ObjectNode>().put("status", 500),
        raw.deepCopy<ObjectNode>().put("title", "Wrong"),
        raw.deepCopy<ObjectNode>().put("type", "https://example.com/wrong"),
      )) {
        assertThrows(
          JsonMappingException::class.java,
        ) { mapper.readValue(invalid.toString(), requireNotNull(value).javaClass) }
      }
    }
    if (target == "sunday") consumerClass.getMethod("httpProblems").invoke(null)
  }
}
