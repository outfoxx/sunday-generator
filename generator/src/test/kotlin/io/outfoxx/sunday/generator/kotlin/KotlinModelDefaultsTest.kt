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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.sunday.generator.tools.modelDefaultsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.AnnotatedType
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
class KotlinModelDefaultsTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `decoding factories delegate nested container constraints to constructors`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val source = modelDefaultsApi(frontend, directory)
    val strings =
      GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(GeneratedTypeRef.scalar("string")))
    val maps = GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(strings))
    val model =
      GeneratedModel(
        "FactoryModel",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "values",
              GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(maps)),
            ),
            GeneratedModelProperty(
              "label",
              GeneratedTypeRef.scalar("string"),
              required = false,
              defaultValue = "default",
            ),
          ),
      )
    val api = source.copy(models = source.models + model)
    for (jaxrs in listOf(false, true)) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(
            KotlinTypeRegistry.Option.ImplementModel,
            KotlinTypeRegistry.Option.JacksonAnnotations,
            KotlinTypeRegistry.Option.ValidationConstraints,
            KotlinTypeRegistry.Option.UseJakartaPackages,
          ),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (jaxrs) {
        KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      withNativeBeanValidation("jakarta", result.classLoader) {
        val type = result.classLoader.loadClass("io.test.FactoryModel")
        val factory = type.getDeclaredMethod("fromJson", List::class.java, String::class.java)

        fun assertNoValidationAnnotations(type: AnnotatedType) {
          assertTrue(
            type.annotations.none {
              it.annotationClass.java.name
                .contains("validation.")
            },
          )
          (type as? AnnotatedParameterizedType)?.annotatedActualTypeArguments?.forEach(::assertNoValidationAnnotations)
        }
        factory.annotatedParameterTypes.forEach(::assertNoValidationAnnotations)
        val mapper = jacksonObjectMapper()
        val value = mapper.readValue("""{"values":[{"first":["valid"]}]}""", type)
        assertEquals("default", type.getMethod("getLabel").invoke(value))
        assertEquals(emptySet<String>(), nativeConstraintPaths("jakarta", value, "Response"))
        val failure =
          assertThrows(Exception::class.java) {
            mapper.readValue("""{"values":[{"first":[null]}]}""", type)
          }
        assertTrue(
          generateSequence(failure as Throwable) { it.cause }.any {
            it.javaClass.name == "jakarta.validation.ConstraintViolationException"
          },
        )
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `unset constructor defaults are omitted while decoding applies them`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = modelDefaultsApi(frontend, directory)
    for (jaxrs in listOf(false, true)) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
          problemLibrary = KotlinProblemLibrary.SUNDAY,
        )
      if (jaxrs) {
        KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      }
      val callsName = ClassName("io.test", "DefaultCalls")
      val calls = TypeSpec.objectBuilder(callsName)
      for (name in listOf("DefaultRecord", "DefaultChild")) {
        val type = ClassName("io.test", name)
        calls.addFunction(
          FunSpec
            .builder("unset$name")
            .returns(type)
            .addStatement("return %T(name = %S)", type, "test")
            .build(),
        )
        calls.addFunction(
          FunSpec
            .builder("set$name")
            .returns(type)
            .addStatement(
              "return %T(name = %S, executionMode = %S, count = 3, enabled = true, choice = %T.fromValue(%S))",
              type,
              "test",
              "fast",
              ClassName("io.test", "Mode"),
              "fast",
            ).build(),
        )
      }
      val result = compileTypesResult(registry.buildTypes() + (callsName to calls.build()))
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      val mapper = jacksonObjectMapper()
      val callsType = result.classLoader.loadClass("io.test.DefaultCalls")
      val instance = callsType.getField("INSTANCE").get(null)
      for (name in listOf("DefaultRecord", "DefaultChild")) {
        val type = result.classLoader.loadClass("io.test.$name")
        val unset = callsType.getMethod("unset$name").invoke(instance)
        assertEquals(mapper.readTree("""{"name":"test"}"""), mapper.readTree(mapper.writeValueAsBytes(unset)))
        val set = callsType.getMethod("set$name").invoke(instance)
        val wire = mapper.readTree(mapper.writeValueAsBytes(set))
        assertEquals("fast", wire["execution-mode"].textValue())
        assertEquals(3, wire["count"].intValue())
        assertEquals(true, wire["enabled"].booleanValue())
        assertEquals("fast", wire["choice"].textValue())
        val decoded = mapper.readValue("""{"name":"test"}""", type)
        assertEquals(if (frontend == "raml") null else "fast", type.getMethod("getExecutionMode").invoke(decoded))
        assertEquals(if (frontend == "raml") null else 3, type.getMethod("getCount").invoke(decoded))
        assertEquals(if (frontend == "raml") null else true, type.getMethod("getEnabled").invoke(decoded))
        val choice = type.getMethod("getChoice").invoke(decoded)
        val expectedChoice = if (frontend == "raml") mapper.nullNode() else mapper.readTree("\"fast\"")
        assertEquals(expectedChoice, mapper.valueToTree(choice))
        if (name == "DefaultChild") {
          val expectedLocal = if (frontend == "raml") null else "child"
          assertEquals(expectedLocal, type.getMethod("getLocal").invoke(decoded))
        }
        assertThrows(Exception::class.java) { mapper.readValue("{}", type) }
        assertThrows(Exception::class.java) { mapper.readValue("""{"name":"test","execution-mode":null}""", type) }
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `decoding factories retain inherited pattern validation and avoid property collisions`(
    jaxrs: Boolean,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("pattern-defaults.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Pattern defaults",
        """
        FactoryBase:
          type: object
          properties:
            x-fixed: {type: string, default: okay}
            fromJson: {type: string}
        FactoryChild:
          allOf: [{${'$'}ref: '#/components/schemas/FactoryBase'}]
          properties:
            x-fixed: {type: string}
            fromJson: {type: string}
          patternProperties:
            '^x-': {type: string, minLength: 3}
          additionalProperties: false
        """.trimIndent(),
      ),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (jaxrs) {
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val type = result.classLoader.loadClass("io.test.FactoryChild")
    val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val unset = type.getConstructor().newInstance()
    assertEquals(mapper.readTree("{}"), mapper.readTree(mapper.writeValueAsBytes(unset)))
    val decoded = mapper.readValue("{}", type)
    assertEquals("okay", type.getMethod("getXFixed").invoke(decoded))
    assertThrows(Exception::class.java) { mapper.readValue("""{"x-fixed":"ab"}""", type) }
    assertThrows(Exception::class.java) { mapper.readValue("""{"extra":true}""", type) }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `long boundary defaults compile and round trip`(
    jaxrs: Boolean,
    @TempDir directory: Path,
  ) {
    val defaults =
      linkedMapOf(
        "minimum" to Long.MIN_VALUE,
        "next" to Long.MIN_VALUE + 1,
        "maximum" to Long.MAX_VALUE,
        "zero" to 0L,
        "negative" to -42L,
      )

    fun properties(withDefaults: Boolean): String =
      defaults.entries.joinToString("\n") { (name, value) ->
        val default = if (withDefaults) ", default: $value" else ""
        "    $name: {type: [integer, 'null'], format: int64$default}\n" +
          "    ${name}Alias: {\$ref: '#/components/schemas/LongValue'$default}"
      }
    val file = directory.resolve("defaults.yaml")
    file.writeText(
      OpenApiReferenceDocuments.document(
        "Long defaults",
        """
        LongValue:
          type: [integer, 'null']
          format: int64
        Long:
          type: object
          properties:
            marker: {type: string}
        RequiredDefault:
          type: object
          required: [value]
          properties:
            value: {type: integer, format: int64, default: -9223372036854775808}
        """.trimIndent() +
          "\nLongDefaults:\n  type: object\n  properties:\n" + properties(true) +
          "\nLongBase:\n  type: object\n  properties:\n" + properties(false) +
          "\nLongChild:\n  allOf: [{\$ref: '#/components/schemas/LongBase'}]\n  properties:\n" + properties(true),
      ),
    )
    val api = OpenApiToGeneratedApi().convert(file.toUri())
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (jaxrs) {
      KotlinJAXRSIrGenerator(
        api,
        registry,
        KotlinJAXRSOptions(
          false,
          false,
          null,
          false,
          null,
          false,
          "io.test",
          "https://example.test/",
          emptyList(),
          "API",
          false,
        ),
      ).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test", "https://example.test/", emptyList(), "API"),
      ).generateServiceTypes()
    }
    val callsName = ClassName("io.test", "DefaultCalls")
    val calls = TypeSpec.objectBuilder(callsName)
    for (name in listOf("LongDefaults", "LongChild")) {
      val type = ClassName("io.test", name)
      calls.addFunction(
        FunSpec
          .builder("create$name")
          .returns(type)
          .addStatement("return %T()", type)
          .build(),
      )
    }
    val result = compileTypesResult(registry.buildTypes() + (callsName to calls.build()))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val mapper =
      jacksonObjectMapper().enable(
        DeserializationFeature.USE_LONG_FOR_INTS,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
      )
    val expected = defaults.flatMap { (name, value) -> listOf(name to value, "${name}Alias" to value) }.toMap()
    val callsType = result.classLoader.loadClass("io.test.DefaultCalls")
    val callsInstance = callsType.getField("INSTANCE").get(null)
    val parent = result.classLoader.loadClass("io.test.LongBase")
    for (name in listOf("LongDefaults", "LongChild")) {
      val type = result.classLoader.loadClass("io.test.$name")
      if (name == "LongChild") assertTrue(parent.isAssignableFrom(type))
      val constructed = callsType.getMethod("create$name").invoke(callsInstance)
      val decoded = mapper.readValue("{}", type)
      for (property in expected.keys) {
        val getter = "get" + property.replaceFirstChar { it.uppercase() }
        assertNull(type.getMethod(getter).invoke(constructed))
      }
      assertEquals(mapper.valueToTree(expected), mapper.valueToTree(decoded))
      assertEquals(mapper.valueToTree(expected), mapper.readTree(mapper.writeValueAsBytes(decoded)))
      val supplied = expected.mapValues { 7L }
      val suppliedValue = mapper.convertValue(supplied, type)
      assertEquals(mapper.valueToTree(supplied), mapper.valueToTree(suppliedValue))
      val roundTrip = mapper.readValue(mapper.writeValueAsBytes(suppliedValue), type)
      assertEquals(mapper.valueToTree(supplied), mapper.valueToTree(roundTrip))
      val nulls = expected.mapValues { null }
      val explicitNull = mapper.convertValue(nulls, type)
      for (property in expected.keys) {
        val getter = "get" + property.replaceFirstChar { it.uppercase() }
        assertNull(type.getMethod(getter).invoke(explicitNull))
      }
    }
    val parentValue = mapper.readValue("{}", parent)
    assertNull(parent.getMethod("getMinimum").invoke(parentValue))
    val required = result.classLoader.loadClass("io.test.RequiredDefault")
    assertThrows(Exception::class.java) { mapper.readValue("{}", required) }
    val requiredValue = mapper.readValue("{\"value\":-9223372036854775808}", required)
    assertEquals(Long.MIN_VALUE, required.getMethod("getValue").invoke(requiredValue))
  }
}
