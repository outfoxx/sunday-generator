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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.problemExtensionsApi
import io.outfoxx.sunday.problems.SundayHttpProblem
import io.quarkiverse.resteasy.problem.HttpProblem
import io.quarkus.jackson.ObjectMapperCustomizer
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.zalando.problem.ThrowableProblem
import org.zalando.problem.jackson.ProblemModule
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinProblemPreservationTest {
  companion object {
    @JvmStatic
    fun targets(): List<Arguments> =
      buildList {
        for (frontend in listOf("raml", "openapi", "asyncapi", "composed")) {
          for (target in listOf("sunday", "client", "server")) {
            for (library in KotlinProblemLibrary.entries) {
              if (target != "sunday" || library != KotlinProblemLibrary.QUARKUS) {
                add(Arguments.of(frontend, target, library))
              }
            }
          }
        }
      }
  }

  @ParameterizedTest
  @MethodSource("targets")
  fun `problem output and extension behavior are independent of preservation mode`(
    frontend: String,
    target: String,
    library: KotlinProblemLibrary,
    @TempDir directory: Path,
  ) {
    val original = problemExtensionsApi(frontend, directory)
    // An implicit-open root must retain extensions just like an explicit schema opt-in.
    val api =
      original.copy(
        models =
          original.models.map {
            if (it.name == "Problem") it.copy(additionalProperties = null) else it
          },
      )
    val names =
      listOf("Problem", "InvalidLimitProblem", "OtherProblem", "ClosedProblem", "TypedProblem", "PatternProblem")
    var expectedSources: Map<String, String>? = null
    for (mode in UnknownFieldPreservation.entries) {
      val registry =
        KotlinTypeRegistry(
          null,
          if (target == "server") GenerationMode.Server else GenerationMode.Client,
          setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
          problemLibrary = library,
        )
      if (target == "sunday") {
        KotlinSundayIrGenerator(
          api,
          registry,
          KotlinSundayOptions(
            kotlinPlacement("io.test", "io.test.service"),
            "http://example.com/",
            listOf("application/json"),
            "API",
            preserveUnknownFields = mode,
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
            placement = kotlinPlacement("io.test", "io.test.service"),
            defaultProblemBaseUri = "http://example.com/",
            defaultMediaTypes = listOf("application/json"),
            serviceSuffix = "API",
            quarkus = library == KotlinProblemLibrary.QUARKUS,
            preserveUnknownFields = mode,
          ),
        ).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      val sources =
        names.associateWith {
          CompiledGeneratedSources.source(
            GeneratedCodeLanguage.Kotlin,
            "io/test/$it.kt",
          )
        }
      names.forEach { name ->
        val type = result.classLoader.loadClass("io.test.$name")
        assertFalse(type.methods.any { it.name == "getAdditionalProperties" }, "$name/$mode")
        assertFalse(type.declaredFields.any { it.name == "extensionFields" }, "$name/$mode")
      }
      if (expectedSources == null) expectedSources = sources else assertEquals(expectedSources, sources, mode.name)
      val mapper = jacksonObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      when (library) {
        KotlinProblemLibrary.SUNDAY -> Unit
        KotlinProblemLibrary.ZALANDO -> mapper.registerModule(ProblemModule())
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

      fun mapperFor(name: String) =
        mapper.copy().apply {
          if (library == KotlinProblemLibrary.ZALANDO) {
            val subtype = if (name == "ProblemResponse") "InvalidLimitProblem" else name
            registerSubtypes(NamedType(result.classLoader.loadClass("io.test.$subtype"), "about:blank"))
          }
        }
      val wire = """{"type":"about:blank","title":"Failure","status":400,"future":null,"nested":{"value":[1,true]}}"""
      for (name in listOf("Problem", "InvalidLimitProblem", "OtherProblem", "ProblemResponse")) {
        val type = result.classLoader.loadClass("io.test.$name")
        val mapper = mapperFor(name)
        val value = mapper.readValue(wire, type)
        assertEquals(mapper.readTree(wire), mapper.readTree(mapper.writeValueAsBytes(value)), "$name/$mode")
        val extensions =
          when (value) {
            is SundayHttpProblem -> value.extensions
            is HttpProblem -> value.parameters
            is ThrowableProblem -> value.parameters
            else -> error("Expected runtime problem: $value")
          }
        assertEquals(mapOf("future" to null, "nested" to mapOf("value" to listOf(1, true))), extensions)
        if (value is HttpProblem) {
          assertEquals(
            mapper.readTree(wire),
            mapper.readTree(mapper.writeValueAsBytes(HttpProblem.builder(value).build())),
          )
        }
      }
      val base = """{"type":"about:blank","title":"Failure","status":400}"""
      val closed = result.classLoader.loadClass("io.test.ClosedProblem")
      val lenient = mapperFor("ClosedProblem").disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      lenient.readValue(base, closed)
      assertThrows(JsonMappingException::class.java) { lenient.readValue(wire, closed) }
      if (frontend != "raml") {
        val mapper = mapperFor("TypedProblem")
        val typed = result.classLoader.loadClass("io.test.TypedProblem")
        val valid = base.dropLast(1) + """, "count":2}"""
        assertEquals(mapper.readTree(valid), mapper.readTree(mapper.writeValueAsBytes(mapper.readValue(valid, typed))))
        assertThrows(
          JsonMappingException::class.java,
        ) { mapper.readValue(base.dropLast(1) + """, "count":0}""", typed) }
      }
      val patternedMapper = mapperFor("PatternProblem")
      val patterned = result.classLoader.loadClass("io.test.PatternProblem")
      val patternWire = base.dropLast(1) + """, "x-count":2}"""
      assertEquals(
        patternedMapper.readTree(patternWire),
        patternedMapper.readTree(patternedMapper.writeValueAsBytes(patternedMapper.readValue(patternWire, patterned))),
      )
      assertThrows(JsonMappingException::class.java) {
        patternedMapper.readValue(base.dropLast(1) + """, "x-count":0}""", patterned)
      }
      // The exception applies to problem classes, not ordinary response envelopes.
      val ordinary = result.classLoader.loadClass("io.test.ProblemFixtures")
      if (mode != UnknownFieldPreservation.On) {
        assertFalse(ordinary.methods.any { it.name == "getAdditionalProperties" })
      }
    }
  }
}
