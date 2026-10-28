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
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.narrowedProblemDetailApi
import io.quarkus.jackson.ObjectMapperCustomizer
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.zalando.problem.jackson.ProblemModule
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
class KotlinProblemEncodingTest {
  companion object {
    @JvmStatic
    fun targets(): List<Arguments> =
      buildList {
        for (target in listOf("sunday", "jaxrs")) {
          for (library in KotlinProblemLibrary.entries) {
            if (target == "sunday" && library == KotlinProblemLibrary.QUARKUS) continue
            for (frontend in listOf("openapi", "asyncapi", "composed")) {
              for (validation in listOf(false, true)) add(Arguments.of(target, library, frontend, validation))
            }
          }
        }
      }
  }

  @ParameterizedTest
  @MethodSource("targets")
  fun `problem serializers omit detail when descendants disallow null`(
    target: String,
    library: KotlinProblemLibrary,
    frontend: String,
    validation: Boolean,
    @TempDir directory: Path,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        if (target == "sunday") GenerationMode.Client else GenerationMode.Server,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (validation) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
        problemLibrary = library,
      )
    val api = narrowedProblemDetailApi(frontend, directory)
    if (target == "sunday") {
      KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
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
          placement = kotlinJAXRSTestOptions.placement,
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = library == KotlinProblemLibrary.QUARKUS,
        ),
      ).generateServiceTypes()
    }
    val result =
      compileTypesResult(
        registry.buildTypes(),
        listOf(
          SourceFile.kotlin(
            "Consumer.kt",
            """
            import io.test.AccountFieldsInvalidProblem
            fun problem(detail: String?): AccountFieldsInvalidProblem = AccountFieldsInvalidProblem(detail = detail)
            """.trimIndent(),
          ),
        ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation("jakarta", result.classLoader) {
      val create = result.classLoader.loadClass("ConsumerKt").getMethod("problem", String::class.java)
      val problemClass = result.classLoader.loadClass("io.test.AccountFieldsInvalidProblem")
      val mapper = jacksonObjectMapper()
      when (library) {
        KotlinProblemLibrary.SUNDAY -> Unit
        KotlinProblemLibrary.ZALANDO -> {
          mapper.registerModule(ProblemModule())
          mapper.registerSubtypes(NamedType(problemClass, "https://example.com/problems/account-fields-invalid"))
        }
        KotlinProblemLibrary.QUARKUS -> {
          val registrar =
            Class
              .forName("io.quarkiverse.resteasy.problem.jackson.JacksonProblemModuleRegistrar")
              .getDeclaredConstructor()
              .apply { isAccessible = true }
              .newInstance() as ObjectMapperCustomizer
          registrar.customize(mapper)
        }
      }
      for (detail in listOf(null, "", "Invalid field")) {
        val problem = create.invoke(null, detail)
        val encoded = mapper.readTree(mapper.writeValueAsBytes(problem))
        assertEquals(detail != null, encoded.has("detail"), "$target/$library: $encoded")
        if (detail != null) assertEquals(detail, encoded["detail"].asText())
        val decoded = mapper.readValue(encoded.toString(), problemClass)
        assertEquals(encoded, mapper.readTree(mapper.writeValueAsBytes(decoded)))
        val withExtension = encoded.deepCopy<ObjectNode>().putNull("extra")
        val decodedExtension = mapper.readValue(withExtension.toString(), problemClass)
        assertEquals(withExtension, mapper.readTree(mapper.writeValueAsBytes(decodedExtension)))
        val explicitNull = encoded.deepCopy<ObjectNode>().putNull("detail")
        if (library == KotlinProblemLibrary.SUNDAY) {
          val missing = encoded.deepCopy<ObjectNode>().apply { remove("detail") }
          assertEquals(
            mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(missing.toString(), problemClass))),
            mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(explicitNull.toString(), problemClass))),
          )
        } else {
          assertThrows(JsonMappingException::class.java) { mapper.readValue(explicitNull.toString(), problemClass) }
        }
      }
    }
  }
}
