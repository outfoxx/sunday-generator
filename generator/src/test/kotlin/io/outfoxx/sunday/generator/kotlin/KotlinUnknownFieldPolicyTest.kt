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

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.discriminatedPatchApi
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.patchableApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinUnknownFieldPolicyTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `unconstrained inherited patches respect mapper unknown-field policy`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory)
    for (mode in listOf(UnknownFieldPreservation.Off, UnknownFieldPreservation.Schema)) {
      for (jaxrs in listOf(false, true)) {
        val loader = compile(api, mode, jaxrs)
        val type = loader.loadClass("io.test.SomeRequestPatch")
        assertFalse(type.methods.any { it.getAnnotation(JsonAnySetter::class.java)?.enabled == true })
        for (strict in listOf(false, true)) {
          val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
          mapper.readValue("""{"count":2,"display-name":"valid"}""", type)
          for (extra in listOf("1", "null", "{}", "[]")) {
            val wire = """{"count":2,"misspelled":$extra}"""
            if (strict) {
              val error = assertThrows(UnrecognizedPropertyException::class.java) { mapper.readValue(wire, type) }
              assertEquals("misspelled", error.propertyName)
            } else {
              val value = mapper.readValue(wire, type)
              assertFalse(mapper.readTree(mapper.writeValueAsBytes(value)).has("misspelled"))
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `disabled storage retains typed dynamic validation`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val original = openModelFieldsApi(frontend, directory)
    // Plain value types still require a decoder even without extra constraints or stored values.
    val api =
      original.copy(
        models =
          original.models.map { model ->
            if (model.name == "TypedRecord") {
              model.copy(
                additionalProperties = model.additionalProperties?.copy(validation = emptyMap(), allowedValues = null),
              )
            } else {
              model
            }
          },
      )
    for (jaxrs in listOf(false, true)) {
      val type = compile(api, UnknownFieldPreservation.Off, jaxrs).loadClass("io.test.TypedRecord")
      for (strict in listOf(false, true)) {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
        val value = mapper.readValue("""{"id":"one","future":2}""", type)
        // RAML represents the declared field as a pattern, whose storage is independent of catch-all policy.
        assertEquals(frontend == "raml", mapper.readTree(mapper.writeValueAsBytes(value)).has("future"))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"id":"one","future":"wrong"}""", type)
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed", "three-level"])
  fun `patch variants handle every undeclared field using the same mapper policy`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = discriminatedPatchApi(frontend, directory)
    val json = jacksonObjectMapper()
    for (mode in UnknownFieldPreservation.entries) {
      for (jaxrs in listOf(false, true)) {
        val loader = compile(api, mode, jaxrs, validation = true)
        val cat = loader.loadClass("io.test.CatPatch")
        val base = loader.loadClass("io.test.AnimalPatch")
        if (mode != UnknownFieldPreservation.On) {
          assertFalse(cat.methods.any { it.getAnnotation(JsonAnySetter::class.java)?.enabled == true })
          assertFalse(cat.methods.any { it.name == "getAdditionalProperties" })
        }
        for (mapper in listOf(json.copy(), ObjectMapper(CBORFactory()).registerKotlinModule())) {
          for (strict in listOf(false, true)) {
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
            for (type in listOf(cat, base)) {
              val valid = json.readTree("""{"kind":"cat","indoor":true}""")
              assertEquals(valid, mapper.valueToTree(mapper.readValue(mapper.writeValueAsBytes(valid), type)))
              for (field in listOf("trained", "state", "parent")) {
                for (value in listOf("null", "true", "{}", "[]")) {
                  val unrelated = json.readTree("""{"kind":"cat","$field":$value}""")
                  if (strict && mode != UnknownFieldPreservation.On) {
                    val error =
                      assertThrows(UnrecognizedPropertyException::class.java) {
                        mapper.readValue(mapper.writeValueAsBytes(unrelated), type)
                      }
                    assertEquals(field, error.propertyName)
                  } else {
                    val decoded = mapper.readValue(mapper.writeValueAsBytes(unrelated), type)
                    val encoded = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(decoded)
                    if (mode == UnknownFieldPreservation.On) {
                      assertEquals(unrelated, encoded)
                    } else {
                      assertFalse(encoded.has(field))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `adding a sibling field leaves generated patch source unchanged`(
    @TempDir directory: Path,
  ) {
    val original = discriminatedPatchApi("openapi", directory)
    val changed = discriminatedPatchApi("openapi", directory, extraDogField = "knownAliases")
    for (mode in UnknownFieldPreservation.entries) {
      compile(original, mode, jaxrs = true, validation = true)
      val before = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/CatPatch.kt")
      compile(changed, mode, jaxrs = true, validation = true)
      assertEquals(before, CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/CatPatch.kt"))
    }
  }

  @Test
  fun `unrestricted additional properties do not create an empty any-setter when off`(
    @TempDir directory: Path,
  ) {
    val original = patchableApi("openapi", directory)
    for (additional in listOf(
      null,
      GeneratedAdditionalProperties(allowed = true),
      GeneratedAdditionalProperties(allowed = true, type = GeneratedTypeRef.scalar("any")),
      GeneratedAdditionalProperties(allowed = true, type = GeneratedTypeRef.scalar("any", nullable = true)),
    )) {
      val api = original.copy(models = original.models.map { it.copy(additionalProperties = additional) })
      for (jaxrs in listOf(false, true)) {
        val type = compile(api, UnknownFieldPreservation.Off, jaxrs).loadClass("io.test.SomeRequestPatch")
        assertFalse(type.methods.any { it.getAnnotation(JsonAnySetter::class.java)?.enabled == true })
        val mapper = jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        assertThrows(UnrecognizedPropertyException::class.java) { mapper.readValue("""{"misspelled":null}""", type) }
      }
    }
  }

  private fun compile(
    api: GeneratedApi,
    mode: UnknownFieldPreservation,
    jaxrs: Boolean,
    validation: Boolean = false,
  ): ClassLoader {
    val registry =
      KotlinTypeRegistry(
        null,
        if (jaxrs) GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          if (validation) {
            add(KotlinTypeRegistry.Option.ValidationConstraints)
            add(KotlinTypeRegistry.Option.UseJakartaPackages)
          }
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (jaxrs) {
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
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
          preserveUnknownFields = mode,
        ),
      ).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test", "io.test.service"),
          "https://example.com/",
          listOf("application/json"),
          "API",
          preserveUnknownFields = mode,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return result.classLoader
  }
}
