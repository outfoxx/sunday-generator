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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
class KotlinModelValueEqualityTest {
  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs", "server", "quarkus"])
  fun `equality return values stay attached at class and property wrapping boundaries`(
    framework: String,
    @TempDir directory: Path,
  ) {
    val reportedName = "BackgroundProcessingTaskCompletedEvent"
    val declarations =
      listOf(reportedName.dropLast(1), reportedName, "${reportedName}s")
        .associateWith { listOf("message") } +
        ("LongProperties" to (16..32).map { "message" + "X".repeat(it - 7) })
    val source = directory.resolve("events.yaml")
    source.writeText(
      buildString {
        appendLine(
          """
          openapi: 3.1.0
          info: {title: Equality formatting reproduction, version: 1.0.0}
          paths:
            /events:
              get:
                operationId: getEvent
                responses:
                  '200':
                    description: An event
                    content:
                      application/json:
                        schema: {${'$'}ref: '#/components/schemas/$reportedName'}
          components:
            schemas:
          """.trimIndent(),
        )
        declarations.forEach { (name, fields) ->
          appendLine("    $name:")
          appendLine("      type: object")
          appendLine("      required: [${fields.joinToString()}]")
          appendLine("      properties:")
          fields.forEach { appendLine("        $it: {type: string}") }
        }
      },
    )
    val registry = generate(GeneratedApiIrExporter().export(source.toUri()), framework)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val mapper = jacksonObjectMapper()
    val instances =
      declarations.map { (name, fields) ->
        val type = compiled.classLoader.loadClass("io.test.$name")
        val values = fields.associateWith { "value" }

        fun decode(values: Map<String, String>) = mapper.readValue(mapper.writeValueAsString(values), type)
        val first = decode(values)
        assertEqualValues(first, first)
        assertEqualValues(first, decode(values))
        assertFalse(first.equals(null))
        assertFalse(first.equals("value"))
        fields.forEach { field -> assertNotEquals(first, decode(values + (field to "changed"))) }
        first
      }
    assertNotEquals(instances[0], instances[1])
    assertNotEquals(instances[1], instances[0])
  }

  @ParameterizedTest
  @CsvSource(
    "raml, sunday",
    "raml, jaxrs",
    "openapi, sunday",
    "openapi, jaxrs",
    "asyncapi, sunday",
    "asyncapi, jaxrs",
    "composed, sunday",
    "composed, jaxrs",
  )
  fun `all frontends compare nested inherited and additional fields`(
    frontend: String,
    framework: String,
    @ResourceUri("openapi/ir/open-model-fields.yaml") openApi: URI,
    @ResourceUri("asyncapi/ir/open-model-fields.yaml") asyncApi: URI,
    @ResourceUri("raml/type-gen/open-model-fields.raml") raml: URI,
  ) {
    val sources =
      when (frontend) {
        "raml" -> listOf(raml)
        "openapi" -> listOf(openApi)
        "asyncapi" -> listOf(asyncApi)
        else -> listOf(openApi, asyncApi.resolve("open-model-broker.yaml"))
      }
    val registry = generate(GeneratedApiIrExporter().export(sources), framework)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val mapper = jacksonObjectMapper()

    fun decode(
      name: String,
      json: String,
    ): Any = mapper.readValue(json, compiled.classLoader.loadClass("io.test.$name"))
    val json =
      """{"id":"one","data":{"name":"name","additionalProperties":"declared","future":null},""" +
        """"extra":{"a":1,"b":[1,2]}}"""
    val first = decode("Notice", json)
    val second = decode("Notice", json)
    assertEqualValues(first, second)
    assertEqualValues(first, decode("Notice", json.replace("\"a\":1,\"b\":[1,2]", "\"b\":[1,2],\"a\":1")))
    for (changed in listOf(
      json.replace("one", "two"),
      json.replace("name\":\"name", "name\":\"changed"),
      json.replace("[1,2]", "[2,1]"),
      json.replace("\"future\":null", "\"future\":true"),
      json.replace(",\"future\":null", ""),
      json.replace("declared", "changed"),
    )) {
      assertNotEquals(first, decode("Notice", changed))
    }
    assertFalse(first.equals(null))
    assertFalse(first.equals("one"))
    val child = decode("NamedChild", """{"id":"one","additionalProperties":"value","extra":null}""")
    assertEqualValues(child, decode("NamedChild", """{"extra":null,"additionalProperties":"value","id":"one"}"""))
    assertNotEquals(child, decode("NamedChild", """{"id":"two","additionalProperties":"value","extra":null}"""))
    val base = decode("BaseRecord", """{"id":"one","additionalProperties":"value","extra":null}""")
    assertNotEquals(base, child)
    assertNotEquals(child, base)
    val extended = decode("ExtendedRecord", """{"id":"one","kind":"extended"}""")
    assertEqualValues(extended, decode("ExtendedRecord", """{"id":"one","kind":"extended"}"""))
    assertNotEquals(extended, decode("ExtendedRecord", """{"id":"two","kind":"extended"}"""))
    if (frontend == "composed") {
      assertEqualValues(decode("Receipt", """{"noticeId":"one"}"""), decode("Receipt", """{"noticeId":"one"}"""))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs", "server", "quarkus"])
  fun `classes and data classes share byte array null and mutation semantics`(
    framework: String,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("values.yaml")
    val fields =
      """
      type: object
      required: [name, bytes, labels]
      properties:
        name: {type: string}
        bytes: {type: string, format: byte}
        labels: {type: array, items: {type: string}}
        note: {type: [string, 'null']}
        nested: {${'$'}ref: '#/components/schemas/Nested'}
        chunks: {type: array, items: {type: string, format: byte}}
        lookup: {type: object, additionalProperties: {type: string, format: byte}}
      """.trimIndent()
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Values, version: '1'}
      paths:
        /records:
          get:
            operationId: getRecord
            responses:
              '200':
                description: Record
                content:
                  application/json:
                    schema: {${'$'}ref: '#/components/schemas/Record'}
          post:
            operationId: createRecord
            requestBody:
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/Input'}
            responses:
              '204': {description: Done}
      components:
        schemas:
          Nested:
            type: object
            required: [value]
            properties:
              value: {type: integer}
          Record:
      """.trimIndent() + "\n" + fields.prependIndent("      ") +
        "\n    Input:\n" + fields.prependIndent("      "),
    )
    val registry = generate(GeneratedApiIrExporter().export(source.toUri()), framework)
    val consumer =
      SourceFile.kotlin(
        "ValueCheck.kt",
        """
        package io.test
        object ValueCheck {
          @JvmStatic fun checkValues() {
            fun record() = Record("name", byteArrayOf(1, 2), listOf("a", "b"), nested = Nested(1))
            val first = record()
            val second = record()
            check(first == second)
            check(first.hashCode() == second.hashCode())
            val keys = hashSetOf(first)
            check(second in keys)
            check(keys.remove(first))
            first.setAdditionalProperty("extraBytes", byteArrayOf(3, 4))
            second.setAdditionalProperty("extraBytes", byteArrayOf(3, 4))
            first.setAdditionalProperty("nestedBytes", listOf(mapOf("part" to byteArrayOf(5))))
            second.setAdditionalProperty("nestedBytes", listOf(mapOf("part" to byteArrayOf(5))))
            check(first == second)
            check(first.hashCode() == second.hashCode())
            first.setAdditionalProperty("arrays", arrayOf<Any>(intArrayOf(1), arrayOf(byteArrayOf(2))))
            second.setAdditionalProperty("arrays", arrayOf<Any>(intArrayOf(1), arrayOf(byteArrayOf(2))))
            check(first == second)
            check(first.hashCode() == second.hashCode())
            second.setAdditionalProperty("arrays", arrayOf<Any>(intArrayOf(1), arrayOf(byteArrayOf(3))))
            check(first != second)
            second.setAdditionalProperty("arrays", arrayOf<Any>(intArrayOf(1), arrayOf(byteArrayOf(2))))
            second.bytes[0] = 9
            check(first != second)
            check(first.hashCode() != second.hashCode())
            second.bytes[0] = 1
            second.setAdditionalProperty("nestedBytes", listOf(mapOf("part" to byteArrayOf(6))))
            check(first != second)
            val input = Input("name", byteArrayOf(1, 2), listOf("a", "b"))
            val other = Input("name", byteArrayOf(1, 2), listOf("a", "b"))
            check(input == other)
            check(input.hashCode() == other.hashCode())
          }
        }
        """.trimIndent(),
      )
    val compiled = compileTypesResult(registry.buildTypes(), listOf(consumer))
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    compiled.classLoader
      .loadClass("io.test.ValueCheck")
      .getMethod("checkValues")
      .invoke(null)
    if (framework == "sunday") {
      assertTrue(
        compiled.classLoader
          .loadClass("io.test.Input")
          .declaredMethods
          .any { it.name == "copy" },
      )
      assertFalse(
        compiled.classLoader
          .loadClass("io.test.Record")
          .declaredMethods
          .any { it.name == "copy" },
      )
    }
    val mapper = jacksonObjectMapper()
    for (name in listOf("Record", "Input")) {
      val type = compiled.classLoader.loadClass("io.test.$name")
      val json =
        """{"name":"name","bytes":"AQI=","labels":["a","b"],"nested":{"value":1},""" +
          """"chunks":["AQI="],"lookup":{"one":"AwQ="}}"""
      val original = mapper.readValue(json, type)
      assertEqualValues(original, mapper.readValue(json, type))
      assertEqualValues(original, mapper.readValue(json.dropLast(1) + ",\"note\":null}", type))
      for (changed in listOf(
        json.replace("AQI=", "AgE="),
        json.replace("AwQ=", "BAU="),
        json.replace("[\"a\",\"b\"]", "[\"b\",\"a\"]"),
      )) {
        assertNotEquals(original, mapper.readValue(changed, type))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs"])
  fun `unknown discriminator bodies participate in equality`(
    framework: String,
    @TempDir directory: Path,
  ) {
    val registry = generate(directionalToleranceApi("openapi", directory), framework)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val mapper = jacksonObjectMapper()
    val type = compiled.classLoader.loadClass("io.test.Event")
    val json = """{"kind":"future","detail":{"attempt":2}}"""
    val first = mapper.readValue(json, type)
    assertEqualValues(first, mapper.readValue(json, type))
    assertNotEquals(first, mapper.readValue(json.replace("2", "3"), type))
    assertNotEquals(first, mapper.readValue(json.replace("future", "another"), type))
    val known = mapper.readValue("""{"kind":"created","count":2}""", type)
    assertEqualValues(known, mapper.readValue("""{"kind":"created","count":2}""", type))
    assertNotEquals(first, known)
    assertNotEquals(known, first)
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs"])
  fun `disabling value equality retains ordinary reference equality`(
    framework: String,
    @ResourceUri("openapi/ir/open-model-fields.yaml") source: URI,
  ) {
    val registry = generate(GeneratedApiIrExporter().export(source), framework, enabled = false)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val type = compiled.classLoader.loadClass("io.test.BaseRecord")
    val mapper = jacksonObjectMapper()
    assertNotEquals(mapper.readValue("""{"id":"one"}""", type), mapper.readValue("""{"id":"one"}""", type))
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs"])
  fun `problem exceptions keep their existing equality`(
    framework: String,
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
  ) {
    val registry = generate(GeneratedApiIrExporter().export(source), framework)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    for (name in listOf("Problem", "RebuildingProblem", "QueuedProblem")) {
      val type = compiled.classLoader.loadClass("io.test.inherited.$name")
      assertTrue(Throwable::class.java.isAssignableFrom(type))
      assertFalse(type.declaredMethods.any { it.name == "equals" || it.name == "hashCode" })
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs"])
  fun `patch operations retain presence and compare set byte contents`(framework: String) {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory")
    val api =
      GeneratedApi(
        name = "Byte patches",
        source = source,
        models =
          listOf(
            GeneratedModel(
              "BytePatch",
              GeneratedModel.Kind.OBJECT,
              patchable = true,
              properties = listOf(GeneratedModelProperty("data", GeneratedTypeRef.scalar("string", format = "byte"))),
              source = source,
            ),
          ),
      )
    val registry = generate(api, framework)
    val consumer =
      SourceFile.kotlin(
        "PatchCheck.kt",
        """
        package io.test
        import io.outfoxx.sunday.json.patch.PatchOp
        object PatchCheck {
          @JvmStatic fun checkValues() {
            val first = BytePatch(PatchOp.set(byteArrayOf(1, 2)))
            val second = BytePatch(PatchOp.set(byteArrayOf(1, 2)))
            check(first == second)
            check(first.hashCode() == second.hashCode())
            check(first != BytePatch(PatchOp.set(byteArrayOf(2, 1))))
            check(BytePatch() == BytePatch())
            check(BytePatch(PatchOp.delete()) == BytePatch(PatchOp.delete()))
            check(BytePatch() != BytePatch(PatchOp.delete()))
          }
        }
        """.trimIndent(),
      )
    val compiled = compileTypesResult(registry.buildTypes(), listOf(consumer))
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    compiled.classLoader
      .loadClass("io.test.PatchCheck")
      .getMethod("checkValues")
      .invoke(null)
  }

  @ParameterizedTest
  @ValueSource(strings = ["sunday", "jaxrs"])
  fun `mapped parent schema fields participate in child and descendant equality`(framework: String) {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory")
    val api =
      GeneratedApi(
        name = "MappedParent",
        source = source,
        models =
          listOf(
            GeneratedModel(
              "Base",
              GeneratedModel.Kind.OBJECT,
              source = source,
              targets = mapOf("kotlin" to GeneratedTarget(typeName = "io.example.Base")),
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "payload",
                    GeneratedTypeRef.scalar("string", format = "byte"),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              "Child",
              GeneratedModel.Kind.OBJECT,
              source = source,
              inherits = listOf(GeneratedTypeRef.named("Base")),
              properties = listOf(GeneratedModelProperty("label", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              "Descendant",
              GeneratedModel.Kind.OBJECT,
              source = source,
              inherits = listOf(GeneratedTypeRef.named("Child")),
            ),
          ),
      )
    val consumer =
      SourceFile.kotlin(
        "MappedParentCheck.kt",
        """
        package io.example
        import io.test.Child
        import io.test.Descendant
        open class Base(val id: String, val payload: ByteArray)
        object MappedParentCheck {
          @JvmStatic fun checkValues() {
            for (create in listOf(::Child, ::Descendant)) {
              val first = create("one", byteArrayOf(1, 2), "label")
              val same = create("one", byteArrayOf(1, 2), "label")
              val differentId = create("two", byteArrayOf(1, 2), "label")
              check(first == same)
              check(same == first)
              check(first.hashCode() == same.hashCode())
              check(first != differentId)
              check(first.hashCode() != differentId.hashCode())
              check(hashSetOf(first, same, differentId).size == 2)
              check(first != create("one", byteArrayOf(2, 1), "label"))
              check(first != create("one", byteArrayOf(1, 2), "changed"))
            }
            check(Child("one", byteArrayOf(1, 2), "label") != Descendant("one", byteArrayOf(1, 2), "label"))
          }
        }
        """.trimIndent(),
      )
    val compiled = compileTypesResult(generate(api, framework).buildTypes(), listOf(consumer))
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    compiled.classLoader
      .loadClass("io.example.MappedParentCheck")
      .getMethod("checkValues")
      .invoke(null)
  }

  @Test
  fun `unannotated models and empty classes avoid helper and standard type name collisions`() {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory")
    val api =
      GeneratedApi(
        name = "Names",
        source = source,
        models =
          listOf("List", "Map", "Array", "ByteArray", "ValueEquality", "Empty").map { name ->
            GeneratedModel(
              name,
              GeneratedModel.Kind.OBJECT,
              source = source,
              properties =
                if (name == "Empty") {
                  emptyList()
                } else {
                  listOf(
                    GeneratedModelProperty("other", GeneratedTypeRef.scalar("string"), required = true),
                  )
                },
            )
          },
      ).let { api ->
        api.copy(
          models =
            api.models +
              GeneratedModel(
                "NestedEquality",
                GeneratedModel.Kind.ENUM,
                source = source,
                values = listOf("value"),
                nested = GeneratedNestedType(enclosedIn = GeneratedTypeRef.named("List"), name = "ValueEquality"),
              ),
        )
      }
    val registry =
      KotlinTypeRegistry(
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.ModelValueEquality),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    for (name in listOf("List", "Map", "Array", "ByteArray", "ValueEquality", "Empty")) {
      val type = compiled.classLoader.loadClass("io.test.$name")
      val constructor = if (name == "Empty") type.getConstructor() else type.getConstructor(String::class.java)
      val args = if (name == "Empty") emptyArray() else arrayOf("value")
      assertEqualValues(constructor.newInstance(*args), constructor.newInstance(*args))
      if (name != "Empty") assertNotEquals(constructor.newInstance("first"), constructor.newInstance("second"))
    }
  }

  private fun generate(
    api: GeneratedApi,
    framework: String,
    enabled: Boolean = true,
  ): KotlinTypeRegistry {
    val registry =
      KotlinTypeRegistry(
        null,
        if (framework in setOf("server", "quarkus")) GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          if (enabled) add(KotlinTypeRegistry.Option.ModelValueEquality)
          if (framework in setOf("server", "quarkus")) add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (framework == "quarkus") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (framework == "sunday") {
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
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = framework == "quarkus",
          preserveUnknownFields = UnknownFieldPreservation.On,
        ),
      ).generateServiceTypes()
    }
    return registry
  }

  private fun assertEqualValues(
    first: Any,
    second: Any,
  ) {
    assertEquals(first, second)
    assertEquals(second, first)
    assertEquals(first.hashCode(), second.hashCode())
    assertEquals(1, hashSetOf(first, second).size)
  }
}
