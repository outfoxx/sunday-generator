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
import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.parameterToleranceApi
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.lang.reflect.Proxy
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinParameterToleranceTest {
  @ParameterizedTest
  @CsvSource(
    "raml,javax,sunday",
    "openapi,jakarta,sunday",
    "asyncapi,javax,sunday",
    "composed,jakarta,sunday",
    "raml,javax,server",
    "openapi,jakarta,server",
    "asyncapi,javax,client",
    "composed,jakarta,client",
    "openapi-all,jakarta,sunday",
    "openapi-all,jakarta,server",
  )
  fun `native parameter validation enforces request mode and collection mutation`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val role = if (target == "server") GenerationMode.Server else GenerationMode.Client
    val tolerance = if (frontend.endsWith("-all")) Tolerance.All else Tolerance.Response
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        role,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = parameterToleranceApi(frontend.removeSuffix("-all"), directory, cookies = target != "sunday")
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          "io.test.service",
          "http://example.com/",
          listOf("application/json"),
          "API",
          defaultTolerance = tolerance,
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
          defaultServicePackageName = "io.test.service",
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
          defaultTolerance = tolerance,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper =
        com.fasterxml.jackson.module.kotlin
          .jacksonObjectMapper()

      fun value(
        type: String,
        raw: String,
      ): Any = mapper.readValue("\"$raw\"", result.classLoader.loadClass("io.test.$type"))
      val known = value("State", "active")
      val unknown = value("State", "future")
      val open = value("OpenState", "future")
      val states = mutableListOf(known)
      val service = result.classLoader.loadClass("io.test.service.ParametersAPI")
      val method = service.methods.single { it.name == "parameters" }
      val arguments =
        if (target == "sunday") {
          arrayOf(known, states, null, open, null)
        } else {
          arrayOf(known, states, open, null, null, null)
        }
      if (target == "sunday") {
        JdkTransport(URITemplate("https://example.com"), problemFactory = SundayHttpProblem.Factory).use { transport ->
          val client =
            service.constructors
              .single { it.parameterCount == 3 }
              .newInstance(
                transport,
                listOf(MediaType.JSON),
                listOf(MediaType.JSON),
              )
          val operation = method.invoke(client, *arguments) as Operation<*, *, *>
          runBlocking { operation.transportRequest() }
          states += unknown
          val failure = assertThrows(RuntimeException::class.java) { runBlocking { operation.transportRequest() } }
          assertTrue(
            generateSequence<Throwable>(failure) { it.cause }.any {
              it.javaClass.name == "$namespace.validation.ConstraintViolationException"
            },
            failure.toString(),
          )
          states.clear()
          states += known
          runBlocking { operation.transportRequest() }
          arguments[4] = value("DefaultState", "future")
          val defaultOperation = method.invoke(client, *arguments) as Operation<*, *, *>
          if (tolerance == Tolerance.All) {
            runBlocking { defaultOperation.transportRequest() }
          } else {
            assertThrows(RuntimeException::class.java) { runBlocking { defaultOperation.transportRequest() } }
          }
        }
      } else {
        val instance = Proxy.newProxyInstance(result.classLoader, arrayOf(service)) { _, _, _ -> null }
        val validation = result.classLoader.loadClass("io.outfoxx.sunday.validation.$namespace.ModelValidation")
        val provider =
          validation
            .getMethod(
              "getValidatorProvider",
            ).invoke(validation.getField("INSTANCE").get(null)) as Function0<*>
        val validator = provider.invoke()

        fun violations(): Set<String> =
          if (namespace == "javax") {
            (validator as javax.validation.Validator)
              .forExecutables()
              .validateParameters(instance, method, arguments)
              .map { it.propertyPath.toString() }
              .toSet()
          } else {
            (validator as jakarta.validation.Validator)
              .forExecutables()
              .validateParameters(instance, method, arguments)
              .map { it.propertyPath.toString() }
              .toSet()
          }
        assertTrue(violations().isEmpty(), violations().toString())
        assertTrue(nativeConstraintPaths(namespace, unknown, "Request").isNotEmpty(), "Fallback constraint missing")
        for (index in listOf(0, 4, 5)) {
          val previous = arguments[index]
          arguments[index] = unknown
          assertTrue(
            violations().isNotEmpty(),
            "Unvalidated parameter $index",
          )
          arguments[index] = previous
        }
        arguments[3] = value("DefaultState", "future")
        assertEquals(tolerance == Tolerance.All, violations().isEmpty(), violations().toString())
        arguments[3] = null
        states += unknown
        assertTrue(violations().isNotEmpty(), "Unvalidated collection element")
      }
    }
  }
}
