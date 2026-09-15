/*
 * Copyright 2020 Outfox, Inc.
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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.arrayMultiplesFixture
import io.outfoxx.sunday.generator.tools.inheritedConstraintsFixture
import io.outfoxx.sunday.json.patch.PatchOp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@KotlinTest
class KotlinInheritedConstraintsTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `parent constraints and multiples validate constructors and decoding`(jaxrs: Boolean) {
    val api = inheritedConstraintsFixture()
    val result = compileFixture(api, jaxrs)
    val mapper = jacksonObjectMapper()
    for (name in listOf("Child", "Reversed")) {
      val model = result.classLoader.loadClass("io.test.$name")
      for (number in listOf(0, 2, -2, 6)) {
        val payload = mapOf("text" to "abc", "count" to number, "amount" to 0.3)
        val decoded = mapper.convertValue(payload, model)
        assertEquals(mapper.valueToTree(payload), mapper.readTree(mapper.writeValueAsBytes(decoded)))
        val constructor = model.constructors.single { it.parameterCount == 3 }
        val constructed = constructor.newInstance("abc", number, 0.3)
        assertEquals(mapper.valueToTree(payload), mapper.valueToTree(constructed))
      }
      for (payload in listOf(
        mapOf("text" to "a"),
        mapOf("text" to "abcd"),
        mapOf("count" to 3),
        mapOf("count" to -3),
        mapOf("amount" to 0.31),
      )) {
        assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(payload, model) }
      }
      val omitted = mapper.readValue("{}", model)
      assertEquals(2, model.getMethod("getCount").invoke(omitted))
    }
    if (!jaxrs) {
      val patch = result.classLoader.loadClass("io.test.MultiplePatch")
      val ctor = patch.constructors.single { it.parameterCount == 1 }
      for (operation in listOf(PatchOp.none<Int>(), PatchOp.delete<Int>(), PatchOp.set(2))) ctor.newInstance(operation)
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) { ctor.newInstance(PatchOp.set(3)) }
      assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf("count" to 3), patch) }
      assertTrue(patch.getMethod("getCount").invoke(mapper.readValue("{}", patch)) is PatchOp.None<*>)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `array multiples validate elements without changing collections`(jaxrs: Boolean) {
    val api = arrayMultiplesFixture()
    assertEquals(
      "2",
      api.models
        .single { it.name == "ArrayMultiples" }
        .properties
        .single()
        .validation["multipleOf"],
    )
    val result = compileFixture(api, jaxrs)
    val mapper = jacksonObjectMapper()
    for (name in listOf("ArrayMultiples", "AliasedMultiples", "NullableMultiples", "ArrayChild", "SetMultiples")) {
      val type = result.classLoader.loadClass("io.test.$name")
      val ctor = type.constructors.single { it.parameterCount == 1 }
      for (values in listOf(emptyList(), listOf(2, 4), listOf(0, -2))) {
        val argument = if (ctor.parameterTypes.single() == Set::class.java) values.toSet() else values
        val constructed = ctor.newInstance(argument)
        val expected = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf("values" to values))
        assertEquals(expected, mapper.valueToTree(constructed))
        assertEquals(expected, mapper.valueToTree(mapper.convertValue(mapOf("values" to values), type)))
      }
      assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
        mapper.readValue("{\"values\":[2,3]}", type)
      }
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
        ctor.newInstance(if (ctor.parameterTypes.single() == Set::class.java) setOf(3) else listOf(3))
      }
      if (name != "ArrayMultiples") mapper.readValue("{}", type)
    }
    val nullable = result.classLoader.loadClass("io.test.NullableMultiples")
    mapper.readValue("{\"values\":[null,2]}", nullable)
    val child = result.classLoader.loadClass("io.test.ArrayChild")
    for (value in listOf(-4, 6)) {
      assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
        mapper.readValue("{\"values\":[$value]}", child)
      }
    }
    val fractional = result.classLoader.loadClass("io.test.FractionMultiples")
    mapper.readValue("{\"values\":[0,-0.25,0.5]}", fractional)
    assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
      mapper.readValue("{\"values\":[0.3]}", fractional)
    }
    val sized = result.classLoader.loadClass("io.test.SizedMultiples")
    mapper.readValue("{\"values\":[2,4]}", sized)
    for (array in listOf(
      "[]",
      "[2,2]",
      "[2,4,6]",
    )) {
      assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
        mapper.readValue("{\"values\":$array}", sized)
      }
    }
    if (!jaxrs) {
      val patch = result.classLoader.loadClass("io.test.ArrayPatch")
      val ctor = patch.constructors.single { it.parameterCount == 1 }
      for (operation in listOf(
        PatchOp.none<List<Int>>(),
        PatchOp.delete<List<Int>>(),
        PatchOp.set(emptyList<Int>()),
        PatchOp.set(listOf(2, 4)),
      )) {
        ctor.newInstance(operation)
      }
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) { ctor.newInstance(PatchOp.set(listOf(3))) }
      for (json in listOf("{}", "{\"values\":null}", "{\"values\":[2,4]}")) mapper.readValue(json, patch)
      assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
        mapper.readValue("{\"values\":[3]}", patch)
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  private fun compileFixture(
    api: io.outfoxx.sunday.generator.ir.GeneratedApi,
    jaxrs: Boolean,
  ): JvmCompilationResult {
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
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return result
  }
}
