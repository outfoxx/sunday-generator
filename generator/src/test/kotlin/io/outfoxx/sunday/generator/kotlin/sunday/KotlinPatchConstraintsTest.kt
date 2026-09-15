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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.json.patch.PatchOp
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.net.URI

@KotlinTest
class KotlinPatchConstraintsTest {

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `byte patch restrictions compare set payloads using base64 wire values`() {
    val patch =
      GeneratedModel(
        "BytePatch",
        GeneratedModel.Kind.OBJECT,
        patchable = true,
        properties =
          listOf(
            GeneratedModelProperty(
              "data",
              GeneratedTypeRef.named("BytesAlias"),
              required = true,
              defaultValue = "Tm8=",
              allowedValues = listOf("SGk="),
            ),
            GeneratedModelProperty(
              "encoded",
              GeneratedTypeRef.named("BytesAlias", nullable = true),
              serializationName = "encoded-data",
              defaultValue = "SGk=",
              validation = mapOf("minLength" to "4", "maxLength" to "4", "pattern" to "^SG"),
            ),
            GeneratedModelProperty("raw", GeneratedTypeRef.scalar("string", format = "binary")),
            GeneratedModelProperty(
              "longData",
              GeneratedTypeRef.named("BytesAlias"),
              allowedValues = listOf("A".repeat(80)),
            ),
          ),
      )
    val api =
      GeneratedApi(
        name = "Byte patches",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            patch,
            GeneratedModel(
              "BytesAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("Bytes")),
            ),
            GeneratedModel(
              "Bytes",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string", format = "byte")),
            ),
          ),
      )
    val result = compile(api)
    val model = result.classLoader.loadClass("io.test.BytePatch")
    val mapper = jacksonObjectMapper()
    val empty = mapper.convertValue(emptyMap<String, Any>(), model)
    assertEquals(mapper.createObjectNode(), mapper.valueToTree(empty))
    for (name in listOf("Data", "Encoded", "Raw", "LongData")) {
      val method = model.getMethod("get$name")
      assertEquals(ByteArray::class.java, (method.genericReturnType as ParameterizedType).actualTypeArguments.single())
      assertTrue(method.invoke(empty) is PatchOp.None<*>)
    }
    val constructor = model.constructors.single { it.parameterCount == 4 }
    val payload = mapOf("data" to "SGk=", "encoded-data" to "SGk=", "raw" to "/wA=", "longData" to "A".repeat(80))
    for (value in listOf(
      mapper.readValue(mapper.writeValueAsBytes(payload), model),
      constructor.newInstance(
        PatchOp.set("Hi".toByteArray()),
        PatchOp.set("Hi".toByteArray()),
        PatchOp.set(byteArrayOf(-1, 0)),
        PatchOp.set(ByteArray(60)),
      ),
    )) {
      val set = model.getMethod("getData").invoke(value) as PatchOp.Set<*>
      assertArrayEquals("Hi".toByteArray(), set.value as ByteArray)
      assertEquals(mapper.valueToTree(payload), mapper.readTree(mapper.writeValueAsBytes(value)))
    }
    val deleted = mapper.convertValue(mapOf("encoded-data" to null), model)
    assertTrue(model.getMethod("getEncoded").invoke(deleted) is PatchOp.Delete<*>)
    assertEquals(mapper.valueToTree(mapOf("encoded-data" to null)), mapper.valueToTree(deleted))
    for ((name, invalid) in listOf(
      "data" to "Tm8=",
      "data" to null,
      "encoded-data" to "",
      "encoded-data" to "SGVsbG8=",
      "encoded-data" to "Tm8=",
    )) {
      assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf(name to invalid), model) }
    }
    for ((index, bytes) in listOf(
      0 to "No".toByteArray(),
      1 to byteArrayOf(),
      1 to "Hello".toByteArray(),
      1 to "No".toByteArray(),
    )) {
      val args = Array<Any>(4) { PatchOp.none<Any>() }
      args[index] = PatchOp.set(bytes)
      assertTrue(
        assertThrows(InvocationTargetException::class.java) {
          constructor.newInstance(*args)
        }.cause is IllegalArgumentException,
      )
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `patch restrictions validate set payloads and preserve operation semantics`() {
    val fields =
      listOf(
        GeneratedModelProperty(
          "status",
          GeneratedTypeRef.scalar("string"),
          required = true,
          allowedValues = listOf("active"),
        ),
        GeneratedModelProperty(
          "stateTag",
          GeneratedTypeRef.named("StateAlias"),
          serializationName = "state-tag",
          defaultValue = "inactive",
          allowedValues = listOf("active"),
        ),
        GeneratedModelProperty(
          "quantity",
          GeneratedTypeRef.scalar("integer"),
          defaultValue = "100",
          validation =
            mapOf("minimum" to "1", "maximum" to "5", "exclusiveMinimum" to "true", "exclusiveMaximum" to "true"),
        ),
        GeneratedModelProperty(
          "numericQuantity",
          GeneratedTypeRef.scalar("integer"),
          validation = mapOf("exclusiveMinimum" to "1", "exclusiveMaximum" to "5"),
        ),
        GeneratedModelProperty(
          "unboundedQuantity",
          GeneratedTypeRef.scalar("integer"),
          validation = mapOf("exclusiveMinimum" to "false", "exclusiveMaximum" to "false"),
        ),
        GeneratedModelProperty("zero", GeneratedTypeRef.scalar("any"), allowedValues = listOf(0)),
        GeneratedModelProperty("flag", GeneratedTypeRef.scalar("any"), allowedValues = listOf(false)),
        GeneratedModelProperty(
          "nullableStatus",
          GeneratedTypeRef.scalar("string", nullable = true),
          required = true,
          defaultValue = "active",
          allowedValues = listOf("active"),
        ),
      )
    val patch = GeneratedModel("RestrictedPatch", GeneratedModel.Kind.OBJECT, properties = fields, patchable = true)
    val parentCount = GeneratedModelProperty("count", GeneratedTypeRef.scalar("integer"), required = true)
    val api =
      GeneratedApi(
        name = "Patch restrictions",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "patches.raml"),
        models =
          listOf(
            GeneratedModel(
              "State",
              GeneratedModel.Kind.ENUM,
              values = listOf("active", "inactive", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              "StateAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("State")),
            ),
            patch,
            GeneratedModel("Ordinary", GeneratedModel.Kind.OBJECT, properties = listOf(fields.first())),
            GeneratedModel("OrdinaryBase", GeneratedModel.Kind.OBJECT, properties = listOf(parentCount)),
            patch.copy(
              name = "MixedPatch",
              properties = fields + parentCount.copy(validation = mapOf("minimum" to "1", "maximum" to "5")),
              inherits = listOf(GeneratedTypeRef.named("OrdinaryBase")),
            ),
          ),
      )
    val result = compile(api)

    val mapper = jacksonObjectMapper()
    val model = result.classLoader.loadClass("io.test.RestrictedPatch")
    val empty = mapper.convertValue(emptyMap<String, Any>(), model)
    assertEquals(mapper.createObjectNode(), mapper.valueToTree(empty))
    assertTrue(model.getMethod("getStatus").invoke(empty) is PatchOp.None<*>)
    assertTrue(model.getMethod("getStateTag").invoke(empty) is PatchOp.None<*>)
    assertTrue(model.getMethod("getQuantity").invoke(empty) is PatchOp.None<*>)
    assertTrue(model.getMethod("getNullableStatus").invoke(empty) is PatchOp.None<*>)

    val constructor = model.constructors.single { it.parameterCount == fields.size }
    val arguments = Array<Any>(fields.size) { PatchOp.none<Any>() }
    arguments[0] = PatchOp.set("active")
    constructor.newInstance(*arguments)
    arguments[0] = PatchOp.set("inactive")
    assertTrue(
      assertThrows(InvocationTargetException::class.java) {
        constructor.newInstance(*arguments)
      }.cause is IllegalArgumentException,
    )

    val payload =
      mapOf(
        "status" to "active",
        "state-tag" to "active",
        "quantity" to 2,
        "numericQuantity" to 2,
        "unboundedQuantity" to 0,
        "zero" to 0,
        "flag" to false,
        "nullableStatus" to "active",
      )
    val decoded = mapper.convertValue(payload, model)
    assertEquals(mapper.valueToTree(payload), mapper.valueToTree(decoded))
    val state = result.classLoader.loadClass("io.test.State")
    val stateGetter = model.getMethod("getStateTag")
    assertEquals(state, (stateGetter.genericReturnType as ParameterizedType).actualTypeArguments.single())
    assertTrue(state.isInstance((stateGetter.invoke(decoded) as PatchOp.Set<*>).value))
    val deleted = mapper.convertValue(mapOf("nullableStatus" to null), model)
    assertTrue(model.getMethod("getNullableStatus").invoke(deleted) is PatchOp.Delete<*>)
    assertEquals(mapper.valueToTree(mapOf("nullableStatus" to null)), mapper.valueToTree(deleted))
    for ((field, invalid) in listOf(
      "status" to "inactive",
      "status" to null,
      "state-tag" to "future",
      "state-tag" to "inactive",
      "quantity" to 0,
      "quantity" to 1,
      "quantity" to 5,
      "quantity" to 6,
      "numericQuantity" to 1,
      "numericQuantity" to 5,
      "zero" to false,
      "zero" to 1,
      "flag" to 0,
      "flag" to true,
      "nullableStatus" to "inactive",
    )) {
      assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf(field to invalid), model) }
    }

    val ordinary = result.classLoader.loadClass("io.test.Ordinary")
    mapper.convertValue(mapOf("status" to "active"), ordinary)
    for (invalid in listOf(emptyMap(), mapOf("status" to "inactive"), mapOf("status" to null))) {
      assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(invalid, ordinary) }
    }
    val mixed = result.classLoader.loadClass("io.test.MixedPatch")
    val mixedValue = mapper.convertValue(mapOf("count" to 2, "status" to "active"), mixed)
    assertEquals(2, mixed.getMethod("getCount").invoke(mixedValue))
    assertEquals(PatchOp.set("active"), mixed.getMethod("getStatus").invoke(mixedValue))
    assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf("count" to 6), mixed) }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `RAML patch inheritance preserves operation constructor types`(
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") source: URI,
  ) {
    val result = compile(RamlToGeneratedApi().convert(TestAPIProcessing.process(source)))
    val mapper = jacksonObjectMapper()
    val parent = result.classLoader.loadClass("io.test.Test")
    val child = result.classLoader.loadClass("io.test.Child")
    assertTrue(parent.isAssignableFrom(child))
    for (model in listOf(parent, child)) {
      val empty = mapper.convertValue(emptyMap<String, Any>(), model)
      assertEquals(mapper.createObjectNode(), mapper.valueToTree(empty))
      val payload = mapOf("string" to "value", "int" to 2, "nullable" to null)
      val decoded = mapper.convertValue(payload, model)
      assertEquals(PatchOp.set("value"), model.getMethod("getString").invoke(decoded))
      assertTrue(model.getMethod("getNullable").invoke(decoded) is PatchOp.Delete<*>)
      assertEquals(mapper.valueToTree(payload), mapper.valueToTree(decoded))
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  private fun compile(api: GeneratedApi): JvmCompilationResult {
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(
          KotlinTypeRegistry.Option.ImplementModel,
          KotlinTypeRegistry.Option.JacksonAnnotations,
          KotlinTypeRegistry.Option.ValidationConstraints,
        ),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions("io.test", "https://example.test/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    return result
  }
}
