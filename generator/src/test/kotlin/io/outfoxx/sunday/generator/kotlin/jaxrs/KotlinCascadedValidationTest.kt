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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.AnnotatedType
import java.nio.file.Path
import kotlin.io.path.writeText

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinCascadedValidationTest {
  @Test
  fun `container element validation retains the legacy snapshot`() {
    val api =
      GeneratedApiIrExporter().export(
        listOf(
          javaClass
            .getResource(
              "/raml/type-gen/validation/constraints-container-valid.raml",
            )!!
            .toURI(),
        ),
      )
    val result = compileTypesResult(registry(api, jakarta = false, implement = false, enabled = true).buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val model = result.classLoader.loadClass("io.test.Test")
    assertEquals(Map::class.java, model.getMethod("getChildMap").returnType)
    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-container-element-validation-annotations.output.kt",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/Test.kt"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml", "asyncapi", "composed"])
  fun `properties cascade across namespaces model forms and source formats`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val openapi = javaClass.getResource("/openapi/ir/cascaded-validation.yaml")!!.readText()
    val source = directory.resolve(if (frontend == "raml") "validation.raml" else "validation.yaml")
    source.writeText(
      when (frontend) {
        "raml" ->
          """
          #%RAML 1.0
          title: Cascaded validation
          types:
            Status: {type: string, enum: [ready, done]}
            Child:
              properties:
                name: {type: string, pattern: '^[a-z]+${'$'}'}
            Cat:
              properties: {catName: string}
            Dog:
              properties: {dogName: string}
            Choice: Cat | Dog
            Wrapper:
              properties:
                child?: Child
                choice?: Choice
                childMap?:
                  type: object
                  properties:
                    /.*/: Child
                choiceMap?:
                  type: object
                  properties:
                    /.*/: Choice
                statuses?:
                  type: object
                  properties:
                    /.*/: Status
                groups?:
                  type: object
                  properties:
                    /.*/: Child[]
                children?: Child[]
                choices?: Choice[]
                names?: string[]
          """.trimIndent()
        "asyncapi" ->
          """
          asyncapi: 3.0.0
          info: {title: Cascaded validation, version: 1.0.0}
          channels:
            wrapper:
              address: /w
              messages:
                wrapper: {payload: {${'$'}ref: '#/components/schemas/Wrapper'}}
          operations:
            receiveWrapper:
              action: receive
              channel: {${'$'}ref: '#/channels/wrapper'}
              messages: [{${'$'}ref: '#/channels/wrapper/messages/wrapper'}]
          """.trimIndent() + "\ncomponents:\n" + openapi.substringAfter("components:\n")
        else -> openapi
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val extra = directory.resolve("events.yaml")
      extra.writeText(
        """
        asyncapi: 3.0.0
        info: {title: Cascaded validation, version: 1.0.0}
        channels:
          validated:
            address: /validated
            messages:
              validated: {payload: {${'$'}ref: '#/components/schemas/ValidationEvent'}}
        operations:
          receiveValidationEvent:
            action: receive
            channel: {${'$'}ref: '#/channels/validated'}
            messages: [{${'$'}ref: '#/channels/validated/messages/validated'}]
        components:
          schemas:
            ValidationEvent:
              type: object
              properties:
                wrapper: {${'$'}ref: './validation.yaml#/components/schemas/Wrapper'}
                children:
                  type: array
                  items: {${'$'}ref: './validation.yaml#/components/schemas/Child'}
        """.trimIndent(),
      )
      sources += extra.toUri()
    }
    val api = GeneratedApiIrExporter().export(sources)
    for (jakarta in listOf(false, true)) {
      for (implement in listOf(false, true)) {
        val result = compileTypesResult(registry(api, jakarta, implement, enabled = true).buildTypes())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val model = result.classLoader.loadClass("io.test.Wrapper")
        val annotation = if (jakarta) "jakarta.validation.Valid" else "javax.validation.Valid"
        for (field in listOf("Child", "Choice")) {
          assertTrue(model.getMethod("get$field").annotations.any { it.annotationClass.java.name == annotation }, field)
        }
        for (field in listOf("ChildMap", "ChoiceMap", "Statuses", "Groups", "Children", "Choices", "Names")) {
          assertFalse(
            model.getMethod("get$field").annotations.any { it.annotationClass.java.name == annotation },
            field,
          )
        }
        if (frontend == "composed") {
          val event = result.classLoader.loadClass("io.test.ValidationEvent")
          val wrapper = event.getMethod("getWrapper")
          assertEquals(model, wrapper.returnType)
          assertTrue(wrapper.annotations.any { it.annotationClass.java.name == annotation })
          assertTrue(
            event
              .getMethod("getChildren")
              .annotatedReturnType
              .argument(0)
              .hasAnnotation(annotation),
          )
        }
        for (field in listOf("Children", "Choices")) {
          assertTrue(
            model
              .getMethod("get$field")
              .annotatedReturnType
              .argument(0)
              .hasAnnotation(annotation),
            field,
          )
        }
        for (field in listOf("ChildMap", "ChoiceMap")) {
          val type = model.getMethod("get$field").annotatedReturnType
          assertFalse(type.argument(0).hasAnnotation(annotation), field)
          assertTrue(type.argument(1).hasAnnotation(annotation), field)
        }
        assertFalse(
          model
            .getMethod("getStatuses")
            .annotatedReturnType
            .argument(1)
            .hasAnnotation(annotation),
        )
        assertFalse(
          model
            .getMethod("getNames")
            .annotatedReturnType
            .argument(0)
            .hasAnnotation(annotation),
        )
        assertTrue(
          model
            .getMethod("getGroups")
            .annotatedReturnType
            .argument(1)
            .argument(0)
            .hasAnnotation(annotation),
        )
      }
    }
    val disabled = compileTypesResult(registry(api, jakarta = false, implement = true, enabled = false).buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, disabled.exitCode, disabled.messages)
    assertFalse(CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/Wrapper.kt").contains("@Valid"))
    if (frontend == "composed") {
      val compiledEvent = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/ValidationEvent.kt")
      assertFalse(compiledEvent.contains("@Valid"))
    }
  }

  private fun AnnotatedType.argument(index: Int): AnnotatedType =
    (this as AnnotatedParameterizedType).annotatedActualTypeArguments[index]

  private fun AnnotatedType.hasAnnotation(name: String): Boolean =
    annotations.any {
      it.annotationClass.java.name ==
        name
    }

  private fun registry(
    api: GeneratedApi,
    jakarta: Boolean,
    implement: Boolean,
    enabled: Boolean,
  ): KotlinTypeRegistry {
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        buildSet {
          if (jakarta) add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (implement) add(KotlinTypeRegistry.Option.ImplementModel)
          if (enabled) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
      )
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
    return registry
  }
}
