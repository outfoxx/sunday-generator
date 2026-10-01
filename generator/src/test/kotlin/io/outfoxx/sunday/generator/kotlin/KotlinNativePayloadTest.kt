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
import io.outfoxx.sunday.PayloadValidator
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinNativePayloadTest {
  @ParameterizedTest
  @CsvSource(
    "raml,javax,sunday",
    "openapi,jakarta,sunday",
    "asyncapi,javax,sunday",
    "composed,jakarta,sunday",
    "raml,javax,client",
    "openapi,jakarta,client",
    "asyncapi,javax,client",
    "composed,jakarta,client",
    "raml,javax,server",
    "openapi,jakarta,server",
    "asyncapi,javax,server",
    "composed,jakarta,server",
  )
  fun `native schema views validate root collections aliases and mutations`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val schemas =
      """
      Code: {type: string, minLength: 3}
      Codes: {type: array, items: {${'$'}ref: '#/components/schemas/Code'}, minItems: 1, maxItems: 2}
      State: {type: string, enum: [active, unknown], x-unknown-value: unknown, x-sunday-tolerant: response}
      States: {type: array, items: {${'$'}ref: '#/components/schemas/State'}, minItems: 1}
      """.trimIndent()
    val source = directory.resolve(if (frontend == "raml") "api.raml" else "api.yaml")
    source.writeText(
      if (frontend == "raml") {
        """
        #%RAML 1.0
        title: Native payloads
        annotationTypes:
          sunday.unknownValue: {type: string, allowedTargets: [TypeDeclaration]}
          sunday.tolerant: {type: string, allowedTargets: [TypeDeclaration]}
        types:
          Code: {type: string, minLength: 3}
          Codes: {type: array, items: Code, minItems: 1, maxItems: 2}
          State: {type: string, enum: [active, unknown], (sunday.unknownValue): unknown, (sunday.tolerant): response}
          States: {type: array, items: State, minItems: 1}
        /codes:
          post:
            body:
              application/json: Codes
            responses:
              200:
                body:
                  application/json: Codes
        """.trimIndent()
      } else {
        val header =
          if (frontend == "asyncapi") {
            """
            asyncapi: 3.0.0
            info: {title: Native payloads, version: 1.0.0}
            channels:
              codes:
                address: codes
                messages:
                  codes: {payload: {${'$'}ref: '#/components/schemas/Codes'}}
                  states: {payload: {${'$'}ref: '#/components/schemas/States'}}
            operations:
              receiveCodes:
                action: receive
                channel: {${'$'}ref: '#/channels/codes'}
                messages: [{${'$'}ref: '#/channels/codes/messages/codes'}]
              receiveStates:
                action: receive
                channel: {${'$'}ref: '#/channels/codes'}
                messages: [{${'$'}ref: '#/channels/codes/messages/states'}]
            components:
              schemas:
            """.trimIndent()
          } else {
            """
            openapi: 3.1.0
            info: {title: Native payloads, version: 1.0.0}
            paths:
              /codes:
                post:
                  operationId: codes
                  requestBody:
                    content:
                      application/json:
                        schema: {${'$'}ref: '#/components/schemas/Codes'}
                  responses:
                    '200':
                      description: Codes
                      content:
                        application/json:
                          schema: {${'$'}ref: '#/components/schemas/Codes'}
            components:
              schemas:
            """.trimIndent()
          }
        header + "\n" + schemas.prependIndent("    ")
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      events.writeText(
        "asyncapi: 3.0.0\ninfo: {title: Native payloads, version: 1.0.0}\nchannels: {}\noperations: {}\n",
      )
      sources += events.toUri()
    }
    val api = GeneratedApiIrExporter().export(sources)
    val role = if (target == "server") GenerationMode.Server else GenerationMode.Client
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
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test.service", "https://example.com/", listOf("application/json"), "API"),
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
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
        ),
      ).generateServiceTypes()
    }
    assertTrue(
      registry.buildTypes().keys.any { it.simpleName == "CodeValidation" },
      api.models.joinToString { "${it.name}:${it.kind}:${it.scope}:${it.nominal}" },
    )
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      fun view(
        name: String,
        value: Any,
      ): Any =
        result.classLoader
          .loadClass("io.test.${name}Validation")
          .constructors
          .single()
          .newInstance(value)
      assertTrue(nativeConstraintPaths(namespace, view("Code", "x"), "Response").isNotEmpty())
      val codes = mutableListOf("valid")
      val values = view("Codes", codes)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, values, "Request"))
      val runtime = result.classLoader.loadClass("io.outfoxx.sunday.validation.$namespace.ModelValidation")
      val runtimeInstance = runtime.getField("INSTANCE").get(null)
      val request = runtime.getMethod("request", Any::class.java, Class::class.java)
      request.invoke(runtimeInstance, codes, values.javaClass)
      codes += "x"
      val rejected =
        assertThrows(InvocationTargetException::class.java) {
          request.invoke(runtimeInstance, codes, values.javaClass)
        }
      assertEquals("$namespace.validation.ConstraintViolationException", rejected.cause?.javaClass?.name)
      assertTrue(nativeConstraintPaths(namespace, values, "Request").single().contains("[1]"))
      if (frontend != "asyncapi" && target == "sunday") {
        val service =
          registry
            .buildTypes()
            .keys
            .filter { it.packageName == "io.test.service" }
            .map { result.classLoader.loadClass(it.canonicalName) }
            .single { type -> type.methods.any { it.name == "codes" || it.name == "postCodes" } }
        val transport = JdkTransport(URITemplate("https://example.com"), SundayHttpProblem.Factory)
        val instance =
          service.constructors
            .single { it.parameterCount == 3 }
            .newInstance(transport, listOf(MediaType.JSON), listOf(MediaType.JSON))
        val operation = service.methods.single { it.name == "codes" || it.name == "postCodes" }.invoke(instance, codes)
        val spec = operation.javaClass.getMethod("getSpec").invoke(operation)

        @Suppress("UNCHECKED_CAST")
        val hook = spec.javaClass.getMethod("getRequestValidation").invoke(spec) as PayloadValidator<Any>
        assertThrows(RuntimeException::class.java) { hook.validate(codes) }
        @Suppress("UNCHECKED_CAST")
        val responseHook = spec.javaClass.getMethod("getResponseValidation").invoke(spec) as PayloadValidator<Any>
        assertThrows(RuntimeException::class.java) { responseHook.validate(codes) }
        codes[1] = "valid"
        hook.validate(codes)
        responseHook.validate(codes)
        codes[1] = "x"
        assertThrows(RuntimeException::class.java) { hook.validate(codes) }
        transport.close()
      }
      codes.clear()
      assertEquals(setOf("value"), nativeConstraintPaths(namespace, values, "Response"))
      val unknown =
        result.classLoader
          .loadClass(
            "io.test.State${'$'}Unknown",
          ).getConstructor(String::class.java)
          .newInstance("future")
      val states = view("States", listOf(unknown))
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, states, "Response"))
      assertTrue(nativeConstraintPaths(namespace, states, "Request").single().contains("[0]"))
      val invalidFallback =
        assertThrows(InvocationTargetException::class.java) {
          request.invoke(runtimeInstance, listOf(unknown), states.javaClass)
        }
      assertEquals("$namespace.validation.ConstraintViolationException", invalidFallback.cause?.javaClass?.name)
    }
  }
}
