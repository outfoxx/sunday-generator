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
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.inheritedAdditionalPropertiesModels
import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinPatternModelsTest {
  @ParameterizedTest
  @CsvSource("false,javax", "false,jakarta", "true,javax", "true,jakarta")
  fun `native validation revisits models and enums stored in extension fields`(
    composed: Boolean,
    namespace: String,
    @TempDir directory: Path,
  ) {
    val source = patternModelsApi(directory, composed)
    val state =
      GeneratedModel(
        "ExtensionState",
        GeneratedModel.Kind.ENUM,
        values = listOf("active", "unknown"),
        unknownValue = "unknown",
      )
    val child =
      GeneratedModel(
        "ExtensionChild",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "values",
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.ARRAY,
                "array",
                arguments = listOf(GeneratedTypeRef.scalar("integer")),
              ),
              validation = mapOf("minItems" to "1"),
            ),
          ),
      )
    val record =
      GeneratedModel(
        "ExtensionRecord",
        GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = false)),
        patternProperties =
          listOf(
            GeneratedPatternProperty(
              "^labels-",
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.ARRAY,
                "array",
                arguments = listOf(GeneratedTypeRef.scalar("string")),
              ),
              validation = mapOf("minItems" to "2", "uniqueItems" to "true"),
            ),
            GeneratedPatternProperty(
              "^score-",
              GeneratedTypeRef.scalar("integer"),
              validation = mapOf("minimum" to "2", "multipleOf" to "2"),
            ),
            GeneratedPatternProperty("^state-", GeneratedTypeRef.named(state.name)),
            GeneratedPatternProperty("^child-", GeneratedTypeRef.named(child.name)),
            GeneratedPatternProperty("-other$", GeneratedTypeRef.named("OtherExtensionChild")),
            GeneratedPatternProperty(
              "^states-",
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.MAP,
                "map",
                arguments = listOf(GeneratedTypeRef.named(state.name)),
              ),
            ),
            GeneratedPatternProperty(
              "^children-",
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.ARRAY,
                "array",
                arguments =
                  listOf(
                    GeneratedTypeRef(
                      GeneratedTypeRef.Kind.MAP,
                      "map",
                      arguments = listOf(GeneratedTypeRef.named(child.name)),
                    ),
                  ),
              ),
            ),
          ),
      )
    val otherChild =
      child.copy(
        name = "OtherExtensionChild",
        properties = child.properties.map { it.copy(validation = mapOf("maxItems" to "3")) },
      )
    val api =
      source.copy(
        models =
          source.models + listOf(state, child, otherChild, record) + inheritedAdditionalPropertiesModels(),
      )
    for (jaxrs in listOf(false, true)) {
      val registry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          buildSet {
            add(KotlinTypeRegistry.Option.ImplementModel)
            add(KotlinTypeRegistry.Option.JacksonAnnotations)
            add(KotlinTypeRegistry.Option.ValidationConstraints)
            add(KotlinTypeRegistry.Option.PreserveUnknownFields)
            if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
          },
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
            "io.test.service",
            "https://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        ).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(
          api,
          registry,
          KotlinSundayOptions(
            "io.test.service",
            "https://example.com/",
            listOf("application/json"),
            "API",
          ),
        ).generateServiceTypes()
      }
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      withNativeBeanValidation(namespace, result.classLoader) {
        val decodedTypes = mutableListOf<String>()

        class CountingDeserializer(
          delegate: JsonDeserializer<*>,
          private val name: String,
        ) : DelegatingDeserializer(delegate) {
          override fun newDelegatingInstance(delegate: JsonDeserializer<*>): JsonDeserializer<*> =
            CountingDeserializer(delegate, name)

          override fun deserialize(
            parser: JsonParser,
            context: DeserializationContext,
          ): Any? {
            decodedTypes += name
            return super.deserialize(parser, context)
          }
        }
        val module =
          SimpleModule().setDeserializerModifier(
            object : BeanDeserializerModifier() {
              override fun modifyDeserializer(
                config: DeserializationConfig,
                bean: BeanDescription,
                deserializer: JsonDeserializer<*>,
              ): JsonDeserializer<*> =
                if (bean.beanClass.simpleName in setOf("ExtensionChild", "OtherExtensionChild")) {
                  CountingDeserializer(deserializer, bean.beanClass.simpleName)
                } else {
                  deserializer
                }
            },
          )
        val mapper = jacksonObjectMapper().registerModule(module)
        val patternRecord = result.classLoader.loadClass("io.test.PatternRecord")
        patternModelValid.forEach { json -> mapper.readValue(json, patternRecord) }
        patternModelInvalid.forEach { json ->
          assertThrows(JsonMappingException::class.java, { mapper.readValue(json, patternRecord) }, json)
        }
        patternModelRegressions.forEach { (name, examples) ->
          val modelType = result.classLoader.loadClass("io.test.$name")
          examples.first.forEach { json -> mapper.readValue(json, modelType) }
          examples.second.forEach { json ->
            assertThrows(JsonMappingException::class.java, { mapper.readValue(json, modelType) }, "$name: $json")
          }
        }
        val type = result.classLoader.loadClass("io.test.ExtensionRecord")
        val dynamic = mapper.readValue("""{"labels-one":["one","two"],"score-one":2}""", type)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, dynamic, "Request"))
        decodedTypes.clear()
        val overlap = mapper.readValue("""{"child-other":{"values":[1]}}""", type)
        assertEquals(listOf("OtherExtensionChild"), decodedTypes)
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"child-other":{"values":[]}}""", type) }
        assertThrows(
          JsonMappingException::class.java,
        ) { mapper.readValue("""{"child-other":{"values":[1,2,3,4]}}""", type) }
        assertEquals(listOf("OtherExtensionChild"), decodedTypes)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, overlap, "Request"))
        val overlapFields = type.getMethod("getAdditionalProperties").invoke(overlap) as Map<*, *>
        val overlapChild = requireNotNull(overlapFields["child-other"])
        assertEquals("io.test.OtherExtensionChild", overlapChild.javaClass.name)
        val overlapValues = overlapChild.javaClass.getMethod("getValues").invoke(overlapChild) as MutableList<*>
        overlapValues.clear()
        assertEquals(setOf("child-other.values"), nativeConstraintPaths(namespace, overlap, "Request"))
        for (name in listOf("DynamicChild", "DynamicLeaf")) {
          val additionalType = result.classLoader.loadClass("io.test.$name")
          val additional = mapper.readValue("""{"id":"one","extra":[1,2]}""", additionalType)
          assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, additional, "Request"))
          val setter = additionalType.getMethod("setAdditionalProperty", String::class.java, Any::class.java)
          for (invalid in listOf(listOf(1), listOf(1, 2, 3, 4))) {
            setter.invoke(additional, "extra", invalid)
            assertEquals(setOf("extra"), nativeConstraintPaths(namespace, additional, "Request"))
            assertThrows(JsonMappingException::class.java) {
              mapper.readValue("""{"id":"one","extra":${mapper.writeValueAsString(invalid)}}""", additionalType)
            }
            assertThrows(JsonMappingException::class.java) {
              mapper.readValue(
                """{"payload":{"id":"one","extra":${mapper.writeValueAsString(invalid)}}}""",
                result.classLoader.loadClass("io.test.DynamicHolder"),
              )
            }
          }
        }
        val dynamicFields = type.getMethod("getAdditionalProperties").invoke(dynamic) as Map<*, *>
        (dynamicFields["labels-one"] as MutableList<*>).clear()
        assertEquals(setOf("labels-one"), nativeConstraintPaths(namespace, dynamic, "Request"))
        type.getMethod("setAdditionalProperty", String::class.java, Any::class.java).invoke(dynamic, "score-one", 3)
        assertEquals(setOf("labels-one", "score-one"), nativeConstraintPaths(namespace, dynamic, "Response"))
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"score-one":3}""", type) }
        val erased = mapper.readValue("{}", type)
        val assign = type.getMethod("setAdditionalProperty", String::class.java, Any::class.java)
        assign.invoke(erased, "labels-one", listOf("one", 2))
        assign.invoke(erased, "states-one", mapOf("nested" to "active"))
        assign.invoke(erased, "children-one", listOf(mapOf("nested" to "invalid")))
        assign.invoke(erased, "child-map", mapOf("values" to emptyList<Int>()))
        assign.invoke(erased, "child-tree", mapper.readTree("""{"values":[]}"""))
        assertEquals(
          setOf("labels-one[1]", "states-one[nested]", "children-one[0].[nested]", "child-map", "child-tree"),
          nativeConstraintPaths(namespace, erased, "Request"),
        )
        val model = mapper.readValue("""{"child-one":{"values":[1]},"state-one":"active"}""", type)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, model, "Request"))
        val extensions = type.getMethod("getAdditionalProperties").invoke(model) as Map<*, *>
        val stored = requireNotNull(extensions["child-one"])
        val values = stored.javaClass.getMethod("getValues").invoke(stored) as MutableList<*>
        values.clear()
        assertEquals(
          setOf("additionalProperties[child-one].values"),
          nativeConstraintPaths(namespace, model, "Response"),
        )
        val future = mapper.readValue("""{"state-one":"future"}""", type)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, future, "Response"))
        assertEquals(setOf("additionalProperties[state-one]"), nativeConstraintPaths(namespace, future, "Request"))
        val nestedFuture = mapper.readValue("""{"states-one":{"nested":"future"}}""", type)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, nestedFuture, "Response"))
        assertEquals(
          setOf("additionalProperties[states-one].[nested]"),
          nativeConstraintPaths(namespace, nestedFuture, "Request"),
        )
        val nested = mapper.readValue("""{"children-one":[{"nested":{"values":[1]}}]}""", type)
        assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, nested, "Request"))
        val nestedExtensions = type.getMethod("getAdditionalProperties").invoke(nested) as Map<*, *>
        val storedChildren = nestedExtensions["children-one"] as List<*>
        val nestedChild = requireNotNull((storedChildren.single() as Map<*, *>)["nested"])
        (nestedChild.javaClass.getMethod("getValues").invoke(nestedChild) as MutableList<*>).clear()
        assertEquals(
          setOf("additionalProperties[children-one].[0].[nested].values"),
          nativeConstraintPaths(namespace, nested, "Response"),
        )
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    @TempDir directory: Path,
  ) {
    val api = patternModelsApi(directory, composed)
    for (jaxrs in listOf(false, true)) {
      for (preserve in listOf(false, true)) {
        val registry =
          KotlinTypeRegistry(
            "io.test",
            null,
            GenerationMode.Client,
            buildSet {
              add(KotlinTypeRegistry.Option.ImplementModel)
              add(KotlinTypeRegistry.Option.JacksonAnnotations)
              if (preserve) add(KotlinTypeRegistry.Option.PreserveUnknownFields)
            },
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
              "io.test.service",
              "http://example.com/",
              listOf("application/json"),
              "API",
              false,
              preserveUnknownFields = preserve,
            ),
          ).generateServiceTypes()
        } else {
          KotlinSundayIrGenerator(
            api,
            registry,
            KotlinSundayOptions(
              "io.test.service",
              "http://example.com/",
              listOf("application/json"),
              "API",
              preserveUnknownFields = preserve,
            ),
          ).generateServiceTypes()
        }
        val result = compileTypesResult(registry.buildTypes())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val model = result.classLoader.loadClass("io.test.PatternRecord")
        patternModelValid.forEach { wire ->
          val decoded = mapper.readValue(wire, model)
          if (preserve) {
            val input = mapper.readTree(wire)
            val output = mapper.readTree(mapper.writeValueAsString(decoded))
            input.properties().forEach { (key, value) -> assertEquals(value, output[key], key) }
          }
        }
        patternModelInvalid.forEach { wire ->
          assertThrows(JsonMappingException::class.java, { mapper.readValue(wire, model) }, wire)
        }
        mapper.readValue("""{"x-fixed":"a"}""", result.classLoader.loadClass("io.test.ClosedFieldParent"))
        mapper.readValue("""{"x-fixed":"ok"}""", result.classLoader.loadClass("io.test.PatternFieldParent"))
        patternModelRegressions.forEach { (name, values) ->
          val regressionModel = result.classLoader.loadClass("io.test.$name")
          values.first.forEach { wire -> mapper.readValue(wire, regressionModel) }
          values.second.forEach { wire ->
            assertThrows(JsonMappingException::class.java, { mapper.readValue(wire, regressionModel) }, "$name: $wire")
          }
        }
        val inherited = result.classLoader.loadClass("io.test.PatternInherited")
        mapper.readValue("""{"x-valid":"ok"}""", inherited)
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"x-invalid":"a"}""", inherited) }
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"extra":1}""", inherited) }
        mapper.readValue("""{"extra":1,"x-valid":"ok"}""", result.classLoader.loadClass("io.test.OpenPattern"))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"extra":"wrong"}""", result.classLoader.loadClass("io.test.OpenPattern"))
        }
        mapper.readValue("""{"x-valid":"ok"}""", result.classLoader.loadClass("io.test.PatternOnly"))
      }
    }
  }
}
