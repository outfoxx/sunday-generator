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

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("requests")
class KotlinParameterDefaultsTest {
  @ParameterizedTest
  @CsvSource(
    "raml,client",
    "openapi,client",
    "composed,client",
    "raml,server",
    "openapi,server",
    "composed,server",
    "raml,sunday",
    "openapi,sunday",
    "composed,sunday",
  )
  fun `clients accept absent defaulted parameters while servers retain resolved defaults`(
    frontend: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val mode = if (target == "server") GenerationMode.Server else GenerationMode.Client
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        mode,
        setOf(KotlinTypeRegistry.Option.UseJakartaPackages),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = parameterDefaultsApi(frontend, directory, cookies = target != "sunday")
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api.withOptionalParameterTemplates(),
        registry,
        KotlinSundayOptions("io.test.service", "http://example.com/", listOf("application/json"), "API"),
      ).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    }
    val types = registry.buildTypes()
    val result = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val service = findType("io.test.service.ParametersAPI", types)
    val parameters =
      service.funSpecs
        .single { it.name == "probe" }
        .parameters
        .associateBy { it.name }
    for (name in listOf("pathValue", "queryValue", "zeroValue", "falseValue", "headerValue")) {
      assertEquals(target != "server", parameters.getValue(name).type.isNullable, "$target $name")
      assertEquals(target != "server", parameters.getValue(name).defaultValue != null, "$target $name default")
    }
    if ("cookieValue" in parameters) {
      assertEquals(target != "server", parameters.getValue("cookieValue").type.isNullable)
    }
    assertTrue(parameters.getValue("nullableValue").type.isNullable)
    assertTrue(parameters.getValue("optionalValue").type.isNullable)
    val required = service.funSpecs.single { it.name == "required" }.parameters
    assertFalse(required.single { it.name == "pathValue" }.type.isNullable)
    assertFalse(required.single { it.name == "queryValue" }.type.isNullable)
    if (target == "sunday") {
      val generated = result.classLoader.loadClass("io.test.service.ParametersAPI")
      val method = generated.methods.single { it.name == "probe" }
      val companion = generated.getField("Companion").get(null)
      val baseURL = companion.javaClass.getMethod("baseURL", String::class.java).invoke(companion, null) as URITemplate
      assertNull(baseURL.parameters["host"])
      assertEquals("https://example.com", baseURL.resolve().toURI().toString())
      JdkTransport(URITemplate("https://example.com"), problemFactory = SundayHttpProblem.Factory).use { transport ->
        val client =
          generated.constructors
            .single { it.parameterCount == 3 }
            .newInstance(transport, listOf(MediaType.JSON), listOf(MediaType.JSON))

        fun request(values: Array<Any?>): Request {
          val operation = method.invoke(client, *values) as Operation<*, *, *>
          return runBlocking { operation.transportRequest() }
        }
        val omittedOperation = method.invoke(client, *arrayOfNulls<Any?>(method.parameterCount)) as Operation<*, *, *>
        assertTrue(
          omittedOperation.spec.pathParameters
            .orEmpty()
            .isEmpty(),
        )
        val collapsed = runBlocking { omittedOperation.transportRequest() }
        assertEquals("https://example.com/probe", collapsed.uri.toString())
        val emptyPath = request(arrayOf("", null, null, null, null, null, null))
        assertEquals("https://example.com/probe/", emptyPath.uri.toString())
        val omitted = request(arrayOf("explicit", null, null, null, null, null, null))
        assertEquals("https://example.com/probe/explicit", omitted.uri.toString())
        assertTrue(omitted.headers.none { it.first.equals("headerValue", ignoreCase = true) })
        val defaultMethod = generated.methods.single { it.name == "probe\$default" }
        val defaultOperation =
          defaultMethod.invoke(
            null,
            client,
            *arrayOfNulls<Any?>(method.parameterCount),
            (1 shl method.parameterCount) - 1,
            null,
          ) as Operation<*, *, *>
        val defaults = runBlocking { defaultOperation.transportRequest() }
        assertEquals("/probe/fallback", defaults.uri.path)
        assertTrue(defaults.uri.query.contains("queryValue=5"))
        assertTrue(defaults.uri.query.contains("zeroValue=0"))
        assertTrue(defaults.uri.query.contains("falseValue=false"))
        val explicit = request(arrayOf("explicit", 7, null, null, 0, false, "custom"))
        assertEquals("/probe/explicit", explicit.uri.path)
        assertTrue(explicit.uri.query.contains("queryValue=7"))
        assertTrue(explicit.uri.query.contains("zeroValue=0"))
        assertTrue(explicit.uri.query.contains("falseValue=false"))
        assertEquals("custom", explicit.headers.single { it.first.equals("headerValue", ignoreCase = true) }.second)
      }
      val defaultBaseURL =
        companion.javaClass.methods
          .single { it.name == "baseURL\$default" }
          .invoke(null, companion, null, 1, null) as URITemplate
      assertEquals("https://example.com/example.com", defaultBaseURL.resolve().toURI().toString())
    }
  }
}
