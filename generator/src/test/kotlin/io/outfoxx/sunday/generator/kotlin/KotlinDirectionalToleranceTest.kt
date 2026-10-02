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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinDirectionalToleranceTest {
  @ParameterizedTest
  @MethodSource("commonTargets")
  fun `union common constraints retain independent payload schemas`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target == "server") GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = objectUnionValidationApi(frontend, directory, discriminated = true, commonMaximum = 5)
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test.service", "http://example.com/", listOf("application/json"), "API"),
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
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper = jacksonObjectMapper()
      val payload = mapper.readValue("""{"kind":"large","value":9}""", result.classLoader.loadClass("io.test.Large"))
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, payload, "Request"))
      val view = result.classLoader.loadClass("io.test.ChoiceValidation")
      val wrapped = view.constructors.single { !it.isSynthetic }.newInstance(payload)
      assertEquals(setOf("value.value"), nativeConstraintPaths(namespace, wrapped, "Request"))
      val holder = result.classLoader.loadClass("io.test.Holder")
      val valid = mapper.readValue("""{"choice":{"kind":"large","value":2}}""", holder)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, valid, "Request"))
      val field = holder.getDeclaredField("choice").apply { isAccessible = true }
      field.set(valid, payload)
      assertEquals(setOf("choice.value"), nativeConstraintPaths(namespace, valid, "Request"))
      val choice = result.classLoader.loadClass("io.test.Choice")
      mapper.readValue("""{"kind":"large","value":2}""", choice)
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue("""{"kind":"large","value":9}""", choice)
      }
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue("""{"kind":"large","value":5.00000000000000000001}""", choice)
      }
    }
  }

  @ParameterizedTest
  @MethodSource("targets")
  fun `string discriminator fallbacks retain unknown payloads and reject malformed known branches`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
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
    val api = directionalToleranceApi(frontend, directory)
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test.service", "http://example.com/", listOf("application/json"), "API"),
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
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper = jacksonObjectMapper()
      val type = result.classLoader.loadClass("io.test.Event")
      val raw = """{"kind":"future","detail":{"attempt":2}}"""
      val unknown = mapper.readValue(raw, type)
      assertEquals("EventUnknown", unknown.javaClass.simpleName)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, unknown, "Response"))
      assertEquals(setOf(""), nativeConstraintPaths(namespace, unknown, "Request"))
      val itemType = result.classLoader.loadClass("io.test.Item")
      val item = mapper.readValue("""{"state":"active","states":["active","future"]}""", itemType)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, item, "Response"))
      assertEquals(setOf("states[1]"), nativeConstraintPaths(namespace, item, "Request"))
      @Suppress("UNCHECKED_CAST")
      val states = itemType.getMethod("getStates").invoke(item) as MutableList<Any>
      val future = states.removeAt(1)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, item, "Request"))
      states.add(future)
      assertEquals(setOf("states[1]"), nativeConstraintPaths(namespace, item, "Request"))
      val unknownState =
        result.classLoader
          .loadClass(
            "io.test.State\$Unknown",
          ).getConstructor(String::class.java)
          .newInstance("active")
      assertEquals(setOf(""), nativeConstraintPaths(namespace, unknownState, "Request"))
      val openStateType = result.classLoader.loadClass("io.test.OpenState")
      val openState = mapper.readValue("\"future\"", openStateType)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, openState, "Request"))
      assertEquals(mapper.readTree(raw), mapper.readTree(mapper.writeValueAsBytes(unknown)))
      val retained = mapper.readValue("""{"kind":"future","note":"valid","detail":true}""", type)
      val retainedBody =
        retained.javaClass
          .getMethod(
            "getRawBody",
          ).invoke(retained) as com.fasterxml.jackson.databind.node.ObjectNode
      retainedBody.put("note", "x")
      assertEquals("valid", mapper.readTree(mapper.writeValueAsBytes(retained))["note"].textValue())
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, retained, "Response"))
      retainedBody.set<JsonNode>("note", retainedBody)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, retained, "Response"))
      retainedBody.put("note", "x")
      val shared = mapper.createObjectNode().put("value", 1)
      retainedBody.set<JsonNode>("first", shared)
      retainedBody.set<JsonNode>("second", shared)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, retained, "Response"))
      shared.set<JsonNode>("self", shared)
      assertEquals(setOf("first[self]"), nativeConstraintPaths(namespace, retained, "Response"))
      assertThrows(JsonMappingException::class.java) { mapper.writeValueAsBytes(retained) }
      val constructor = retained.javaClass.constructors.single { !it.isSynthetic }
      val construction =
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
          constructor.newInstance("future", "valid", null, retainedBody)
        }
      assertEquals("$namespace.validation.ConstraintViolationException", construction.cause?.javaClass?.name)
      shared.remove("self")
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, retained, "Response"))
      mapper.readValue("""{"kind":"created","count":1}""", type)
      for (invalid in listOf(
        """{"kind":"x"}""",
        """{"kind":"FUTURE"}""",
        """{"kind":"future","note":"x"}""",
        """{"kind":"future","note":null}""",
        """{"kind":"created","count":0}""",
        """{"kind":"created"}""",
        """{"kind":null}""",
        "{}",
      )) {
        assertThrows(JsonMappingException::class.java) { mapper.readValue(invalid, type) }
      }
    }
  }

  companion object {
    @JvmStatic
    fun commonTargets(): List<Arguments> = targets().filter { it.get()[0] != "raml" }

    @JvmStatic
    fun targets(): List<Arguments> =
      listOf("raml", "openapi", "asyncapi", "composed").flatMap { source ->
        listOf("javax", "jakarta").flatMap { namespace ->
          listOf("sunday", "client", "server").map { target -> Arguments.of(source, namespace, target) }
        }
      }
  }
}
