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

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.ir.GeneratedPlacementResolver
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@KotlinTest
class KotlinPlacementRegressionTest {
  @TempDir lateinit var directory: Path

  private fun compile(
    api: GeneratedApi,
    selection: GeneratedOutputSelection? = null,
  ): com.tschuchort.compiletesting.JvmCompilationResult {
    val registry = KotlinTypeRegistry(null, GenerationMode.Client, setOf(KotlinTypeRegistry.Option.ImplementModel))
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
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
        quarkus = false,
      ),
    ).generateServiceTypes()
    val output = directory.resolve("selected")
    if (selection != null) registry.generateFiles(selection, output)
    return (
      if (selection ==
        null
      ) {
        compileTypesResult(registry.buildTypes())
      } else {
        compileTypesResult(emptyMap(), sourceDirectory = output)
      }
    ).also {
      assertEquals(KotlinCompilation.ExitCode.OK, it.exitCode, it.messages)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `same named patch pairs retain source bindings`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).duplicates()
    val result = compile(api)
    for (owner in listOf("first", "second")) {
      val original = result.classLoader.loadClass("example.$owner.Record")
      val patch = result.classLoader.loadClass("example.$owner.RecordPatch")
      assertEquals("example.$owner.Detail", original.getDeclaredField("detail").type.name)
      assertTrue(original.methods.any { it.name == "patch" && it.returnType == patch })
      assertTrue(
        original.methods.any {
          it.name == "merge" && it.returnType == original && it.parameterTypes.firstOrNull() == patch
        },
      )
      assertTrue(
        patch
          .getDeclaredField("detail")
          .genericType.typeName
          .contains("example.$owner.DetailPatch"),
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `inline payload descendants keep lexical placement and exact selectors`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).inline()
    compile(api)
    val resolved = GeneratedPlacementResolver(generationLanguage = "kotlin").resolve(api)
    val objects = resolved.models.filter { it.properties.isNotEmpty() }
    assertEquals(2, objects.size, objects.toString())
    assertTrue(objects.all { it.targets["kotlinClient"]?.modelPackageName == "example.operation" }, objects.toString())
    assertTrue(objects.all { it.declarationUri != null })
    val child = objects.single { it.properties.any { field -> field.name == "value" } }
    val moved =
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            relocations =
              listOf(
                GeneratedPlacementConfiguration.Relocation(
                  child.declarationUri!!,
                  placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.child")),
                ),
              ),
          ),
      )
    val result = compile(moved)
    assertTrue(
      result.classLoader
        .loadClass("example.child.${child.name}")
        .declaredFields
        .any { it.name == "value" },
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `inline declaration placement overrides operation defaults`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).inline(declarationOverride = true)
    compile(api)
    val resolved = GeneratedPlacementResolver(generationLanguage = "kotlin").resolve(api)
    val child = resolved.models.single { it.properties.any { field -> field.name == "value" } }
    val parent = resolved.models.single { it.properties.any { field -> field.name == "child" } }
    assertEquals("example.declaration", child.targets["kotlinClient"]?.modelPackageName)
    assertEquals("example.operation", parent.targets["kotlinClient"]?.modelPackageName)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "composed"])
  fun `service defaults reach anonymous descendants`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).inline()
    val moved =
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            relocations =
              listOf(
                GeneratedPlacementConfiguration.Relocation(
                  api.source.location,
                  kind = "service",
                  placement =
                    GeneratedPlacement(
                      kotlin =
                        GeneratedPlacement.Scope(
                          models = GeneratedPlacement.Scope(packageName = "example.local"),
                        ),
                    ),
                ),
              ),
          ),
      )
    compile(moved)
    val resolved =
      GeneratedPlacementResolver(
        moved.placementConfiguration,
        generationLanguage = "kotlin",
      ).resolve(moved)
    assertTrue(
      resolved.models.filter { it.properties.isNotEmpty() }.all {
        it.targets["kotlinClient"]?.modelPackageName ==
          "example.local"
      },
      resolved.models.toString(),
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inline union preserves declaration placement and relocation`(composed: Boolean) {
    val source = directory.resolve("api.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      x-sunday-apiId: regression
      info: {title: Union, version: '1'}
      x-sunday-placement: {kotlin: {package: example.document}}
      paths:
        /items:
          get:
            operationId: getItems
            responses:
              '200':
                description: item
                content:
                  application/json:
                    schema:
                      x-sunday-placement: {kotlin: {package: example.union}}
                      oneOf:
                        - type: object
                          required: [value]
                          properties:
                            value: {type: string}
                        - type: object
                          required: [count]
                          properties:
                            count: {type: integer}
      """.trimIndent(),
    )
    val parsed = exportSource(source, composed)
    val api = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(parsed))
    compile(api)
    val union = api.models.single { it.kind == io.outfoxx.sunday.generator.ir.GeneratedModel.Kind.UNION }
    assertEquals(
      source.toRealPath().toUri().toString() + "#/paths/~1items/get/responses/200/content/application~1json/schema",
      union.declarationUri,
    )
    val relocated =
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            relocations =
              listOf(
                GeneratedPlacementConfiguration.Relocation(
                  union.declarationUri!!,
                  placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.relocated")),
                ),
              ),
          ),
      )
    compile(relocated)
    assertEquals(
      "example.relocated",
      GeneratedPlacementResolver(relocated.placementConfiguration, generationLanguage = "kotlin")
        .resolve(relocated)
        .models
        .single {
          it.name ==
            union.name
        }.targets["kotlinClient"]
        ?.modelPackageName,
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `external path operations retain original selector locations`(composed: Boolean) {
    val source = directory.resolve("api.yaml")
    val paths = directory.resolve("paths.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      x-sunday-apiId: regression
      info: {title: External, version: '1'}
      x-sunday-placement: {kotlin: {package: example.document}}
      paths:
        /items: {${'$'}ref: './paths.yaml#/items'}
      """.trimIndent(),
    )
    Files.writeString(
      paths,
      """
      items:
        get:
          operationId: getItems
          responses:
            '200':
              description: item
              content:
                application/json:
                  schema:
                    type: object
                    properties:
                      value: {type: string}
      """.trimIndent(),
    )
    val original = exportSource(source, composed)
    val api = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(original))
    val target = paths.toRealPath().toUri().toString() + "#/items/get"
    assertEquals(
      target,
      api.services
        .single()
        .operations
        .single()
        .declarationUri,
    )
    val moved =
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            relocations =
              listOf(
                GeneratedPlacementConfiguration.Relocation(
                  target,
                  placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.external")),
                ),
              ),
          ),
      )
    compile(moved)
    val resolved =
      GeneratedPlacementResolver(
        moved.placementConfiguration,
        generationLanguage = "kotlin",
      ).resolve(moved)
    assertTrue(
      resolved.models.filter { it.properties.isNotEmpty() }.all {
        it.targets["kotlinClient"]?.modelPackageName ==
          "example.external"
      },
    )
    assertEquals(
      "example.external",
      resolved.services
        .single()
        .targets["kotlinClient"]
        ?.packageName,
    )
  }

  private fun exportSource(
    source: Path,
    composed: Boolean,
  ): GeneratedApi {
    val inputs = mutableListOf(source.toUri())
    if (composed) {
      val events = directory.resolve("events.yaml")
      Files.writeString(
        events,
        """
        asyncapi: 3.0.0
        x-sunday-apiId: regression
        info: {title: Events, version: '1'}
        channels: {}
        x-sunday-placement: {kotlin: {package: example.events}}
        components:
          schemas:
            Event: {type: string, enum: [created]}
        """.trimIndent(),
      )
      inputs.add(events.toUri())
    }
    return GeneratedApiIrExporter().export(inputs)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `local and imported declarations keep separate identities`(kind: String) {
    val result = compile(PlacementRegressionFixture(directory, kind).localAndImported())
    val holder = result.classLoader.loadClass("example.second.Holder")
    assertEquals("example.second.Record", holder.getDeclaredField("local").type.name)
    assertEquals(
      "example.first.${if (kind == "raml") "Record" else "Record2"}",
      holder.getDeclaredField("imported").type.name,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `model selection includes hoisted anonymous payload descendants`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).inline()
    val result = compile(api, GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)))
    val child = api.models.single { it.properties.any { property -> property.name == "value" } }
    assertEquals(
      String::class.java,
      result.classLoader
        .loadClass("example.operation.${child.name}")
        .getDeclaredField("value")
        .type,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `container service defaults also place client configuration helpers`(kind: String) {
    val api =
      PlacementRegressionFixture(directory, kind).service(
        "{kotlin: {services: {package: example.service}}}",
        documentPlacement = false,
      )
    val registry =
      KotlinTypeRegistry(null, GenerationMode.Client, emptySet(), problemLibrary = KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions(
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
      ),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertTrue(registry.buildTypes().keys.all { it.packageName == "example.service" })
    assertTrue(registry.buildTypes().keys.any { it.simpleName.endsWith("Config") })
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `service relocation places client configuration without a document package`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).service(documentPlacement = false)
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              api.source.location,
              kind = "service",
              placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.service")),
            ),
          ),
      )
    val registry =
      KotlinTypeRegistry(null, GenerationMode.Client, emptySet(), problemLibrary = KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api.copy(placementConfiguration = config),
      registry,
      KotlinSundayOptions(
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
      ),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertTrue(registry.buildTypes().keys.all { it.packageName == "example.service" })
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `disjoint container defaults require whole service agreement`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).serviceContainers(split = false)
    val failure =
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacementResolver(generationLanguage = "kotlin").resolve(api)
      }
    assertTrue(failure.message!!.contains("Conflicting"))
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              api.source.location,
              kind = "service",
              placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.unified")),
            ),
          ),
      )
    compile(api.copy(placementConfiguration = config))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `shared configuration plans have helpers in each service and aggregate package`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).serviceContainers(split = true)
    val registry =
      KotlinTypeRegistry(null, GenerationMode.Client, emptySet(), problemLibrary = KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions(
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
        aggregateServices = true,
      ),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertEquals(
      setOf("example.first", "example.second", "example.document"),
      registry
        .buildTypes()
        .keys
        .filter { it.simpleName.endsWith("Config") }
        .map { it.packageName }
        .toSet(),
    )
  }
}
