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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.PlacementIdentityFixture
import io.outfoxx.sunday.generator.tools.PlacementNativeFixture
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.PlacementScopeFixture
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@KotlinTest
class KotlinPlacementOwnershipTest {
  @TempDir lateinit var directory: Path

  @Test
  fun `distinct service payloads cannot silently share an emitted class`() {
    javaClass.getResourceAsStream("/sunday.raml")!!.use {
      Files.copy(it, directory.resolve("sunday.raml"))
    }
    val source = directory.resolve("api.raml")
    Files.writeString(
      source,
      """
      #%RAML 1.0
      title: Payloads
      uses: {sunday: sunday.raml}
      (sunday.placement):
        kotlin:
          package: example.services
          models: {package: example.models}
      """.trimIndent() + "\n" +
        listOf("first", "second").joinToString("\n") { name ->
          """
          /$name:
            (sunday.group): $name
            get:
              displayName: getItem
              responses:
                200:
                  body:
                    application/json:
                      type: object
                      properties:
                        $name: string
          """.trimIndent()
        },
    )
    val api = GeneratedApiIrExporter().export(source.toUri())
    val payloads = api.models.filter { it.properties.isNotEmpty() }
    assertEquals(2, payloads.size)
    for (backend in listOf("sunday", "client", "server")) {
      val failure = assertThrows(GenerationException::class.java) { generate(api, backend) }
      payloads.forEach { assertTrue(failure.message!!.contains(it.declarationUri!!), failure.message) }
      val configuration =
        GeneratedPlacementConfiguration(
          relocations =
            payloads.map { model ->
              GeneratedPlacementConfiguration.Relocation(
                model.declarationUri!!,
                placement =
                  GeneratedPlacement(
                    kotlin = GeneratedPlacement.Scope(packageName = "example.${model.properties.single().name}"),
                  ),
              )
            },
        )
      val compiled =
        compileTypesResult(generate(api.copy(placementConfiguration = configuration), backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      for (model in payloads) {
        val name = model.properties.single().name
        assertEquals(
          String::class.java,
          compiled.classLoader
            .loadClass("example.$name.${model.name.toUpperCamelCase()}")
            .getDeclaredField(name)
            .type,
        )
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `declaration relocation keeps owned payload classes together`(kind: String) {
    val api = PlacementScopeFixture.inheritedRelocation(directory, kind)
    val parent = api.models.single { model -> model.properties.any { it.name == "child" } }
    for (backend in listOf("sunday", "client", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      assertEquals(
        "example.selected",
        compiled.classLoader
          .loadClass(
            "example.selected.${parent.name.toUpperCamelCase()}",
          ).getDeclaredField("child")
          .type.packageName,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `native problem relocations compile with direct placement`(kind: String) {
    val api = PlacementScopeFixture.problems(directory, kind)
    for (backend in listOf("sunday", "client", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      assertEquals("FailureProblem", compiled.classLoader.loadClass("example.problems.FailureProblem").simpleName)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native relocation scopes compile with service defaults and exact model overrides`(kind: String) {
    val api = PlacementScopeFixture(directory, kind).api
    val child = api.models.single { model -> model.properties.any { it.name == "value" } }
    for (backend in listOf("sunday", "client", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      val pkg = if (backend == "server") "example.serverexact" else "example.exact"
      assertEquals(
        String::class.java,
        compiled.classLoader
          .loadClass("$pkg.${child.name.toUpperCamelCase()}")
          .getDeclaredField("value")
          .type,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `recomposed IR preserves contribution reference identities`(kind: String) {
    val api = PlacementIdentityFixture(directory, kind).recomposed()
    for (backend in listOf("sunday", "client", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      for (owner in listOf("first", "second")) {
        val wrapper = compiled.classLoader.loadClass("example.$owner.Wrapper")
        assertEquals("example.first.Foo", wrapper.getDeclaredField("foo").type.name)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `composed nested declarations inherit distinct parent placement`(kind: String) {
    val api = PlacementIdentityFixture(directory, kind).nested()
    val compiled = compileTypesResult(generate(api, "sunday").buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    for (owner in listOf("first", "second")) {
      val parent = compiled.classLoader.loadClass("example.$owner.Parent")
      assertEquals("example.$owner.Parent${'$'}Child", parent.getDeclaredField("child").type.name)
    }
  }

  @Test
  fun `native imported nesting binds the library parent`() {
    val api = PlacementNativeFixture(directory).importedNesting()
    val compiled = compileTypesResult(generate(api, "sunday").buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val holder = compiled.classLoader.loadClass("example.second.Holder")
    assertEquals("example.first.Parent${'$'}Child", holder.getDeclaredField("child").type.name)
    assertEquals("example.first.Parent", holder.getDeclaredField("parent").type.name)
    assertEquals("example.second.Parent", holder.getDeclaredField("local").type.name)
  }

  @Test
  fun `native explicit nested names retain spelling in their identity`() {
    val compiled =
      compileTypesResult(
        generate(PlacementNativeFixture(directory).distinctEnclosures(sameParent = true), "sunday").buildTypes(),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    assertEquals("Nested_Child", compiled.classLoader.loadClass("example.second.Parent${'$'}Nested_Child").simpleName)
    assertEquals("NestedChild", compiled.classLoader.loadClass("example.second.Parent${'$'}NestedChild").simpleName)
  }

  @Test
  fun `native distinct enclosures share a package without deduplicating children`() {
    val compiled =
      compileTypesResult(generate(PlacementNativeFixture(directory).distinctEnclosures(), "sunday").buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    for (name in listOf("First", "Second")) {
      val parent = compiled.classLoader.loadClass("example.second.$name")
      assertEquals("example.second.$name${'$'}Child", parent.getDeclaredField("child").type.name)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate operations preserve behavior through composition`(kind: String) {
    val api = PlacementNativeFixture(directory).duplicateOperations(kind, payloads = true)
    for (backend in listOf("sunday", "client", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    }
  }

  private fun generate(
    api: GeneratedApi,
    backend: String,
    quarkus: Boolean = false,
  ): KotlinTypeRegistry {
    val registry =
      KotlinTypeRegistry(
        null,
        if (backend == "server") GenerationMode.Server else GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.ValidationConstraints),
        problemLibrary = if (quarkus) KotlinProblemLibrary.QUARKUS else KotlinProblemLibrary.SUNDAY,
      )
    if (backend == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
        ),
      ).generateServiceTypes()
    } else {
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
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = quarkus,
        ),
      ).generateServiceTypes()
    }
    return registry
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `source qualified aliases retain independent validation views`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).aliasServices()
    for (backend in listOf("sunday", "jaxrs")) {
      val registry = generate(api, backend)
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      for ((owner, invalid) in listOf("first" to "x", "second" to "valid")) {
        val view = result.classLoader.loadClass("example.$owner.IdentifierValidation")
        val value = view.constructors.single().newInstance(invalid)
        assertTrue(nativeConstraintPaths("javax", value, "Response").isNotEmpty())
        val valid = view.constructors.single().newInstance("long-enough")
        assertTrue(nativeConstraintPaths("javax", valid, "Response").isEmpty())
      }
      // Each compiled service must use its alias view, not generic validation of a String.
      if (kind !in setOf("asyncapi", "asyncapi2")) {
        val sources =
          registry.buildTypes().keys.filter { it.enclosingClassName() == null }.map {
            CompiledGeneratedSources.source(
              GeneratedCodeLanguage.Kotlin,
              "${it.packageName.replace('.', '/')}/${it.simpleName}.kt",
            )
          }
        assertEquals(2, sources.count { it.contains("IdentifierValidation::class") })
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `collection helpers use each service package without document defaults`(kind: String) {
    val api =
      PlacementRegressionFixture(
        directory,
        kind,
      ).serviceContainers(true, collectionPayload = true, documentPlacement = false)
    for (backend in listOf("sunday", "jaxrs", "server")) {
      val registry = generate(api, backend)
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      val helpers =
        registry.buildTypes().keys.filter {
          it.simpleName.startsWith("Payload") &&
            it.simpleName.endsWith("Validation")
        }
      assertEquals(setOf("example.first", "example.second"), helpers.map { it.packageName }.toSet())
      for (helper in helpers) {
        assertEquals(
          List::class.java,
          result.classLoader
            .loadClass(helper.canonicalName)
            .constructors
            .single()
            .parameterTypes
            .single(),
        )
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `composed models inherit only their declaring document`(kind: String) {
    for (reverse in listOf(false, true)) {
      val api = PlacementRegressionFixture(directory, kind).distinctDefaults(reverse)
      val result = compileTypesResult(generate(api, "jaxrs").buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      assertEquals(
        String::class.java,
        result.classLoader
          .loadClass("example.first.First")
          .getDeclaredField("value")
          .type,
      )
      assertEquals(
        String::class.java,
        result.classLoader
          .loadClass("example.defaults.Second")
          .getDeclaredField("value")
          .type,
      )
    }
  }

  @Test
  fun `nested RAML containers place their complete service`() {
    val api = PlacementRegressionFixture(directory, "raml").nestedService()
    for (backend in listOf("sunday", "jaxrs")) {
      val registry = generate(api, backend)
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
      assertTrue(registry.buildTypes().keys.all { it.packageName == "example.nested" })
    }
  }

  @Test
  fun `composed typed server variables retain their own enum identity`() {
    val api = PlacementRegressionFixture(directory, "raml").typedServers()
    val registry = generate(api, "sunday")
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    for (owner in listOf("first", "second")) {
      val config =
        registry.buildTypes().keys.single {
          it.packageName == "example.$owner" &&
            it.simpleName.endsWith("Config")
        }
      assertEquals(
        "example.$owner.Environment",
        result.classLoader
          .loadClass(config.canonicalName)
          .getDeclaredField("environment")
          .type.name,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `foreign problems do not inherit the root document placement`(kind: String) {
    val original = PlacementRegressionFixture(directory, kind).distinctDefaults()
    val owner = original.models.single { it.name == "Second" }.source
    val problem =
      io.outfoxx.sunday.generator.ir.GeneratedProblem(
        name = "ForeignProblem",
        source = owner,
        typeUri = "https://example.com/foreign",
        status = 400,
        title = "Foreign",
        detail = "Invalid value",
      )
    val api =
      io.outfoxx.sunday.generator.ir.GeneratedApiYaml.readString(
        io.outfoxx.sunday.generator.ir.GeneratedApiYaml
          .writeString(original.copy(problems = listOf(problem))),
      )
    val result = compileTypesResult(generate(api, "sunday").buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertEquals("ForeignProblem", result.classLoader.loadClass("example.defaults.ForeignProblem").simpleName)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced foreign declarations require their own Kotlin package`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).distinctDefaults()
    for (backend in listOf("sunday", "jaxrs", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      val failure =
        assertThrows(GenerationException::class.java) {
          generate(api.copy(placementConfiguration = GeneratedPlacementConfiguration()), backend)
        }
      assertTrue(failure.message!!.contains("No model package"))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `optional property selectors retain source spelling`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).optionalProperty()
    for (backend in listOf("sunday", "jaxrs", "server")) {
      val compiled = compileTypesResult(generate(api, backend).buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      val holder = compiled.classLoader.loadClass("example.models.Holder")
      assertEquals("example.detail", holder.getDeclaredField("detail").type.packageName)
      assertEquals("example.explicit", holder.getDeclaredField("explicit").type.packageName)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `nested validation uses only applicable winning relocations`(kind: String) {
    for (overridden in listOf(false, true)) {
      val api = PlacementRegressionFixture(directory, kind).filteredNested(overridden)
      val compiled = compileTypesResult(generate(api, "sunday").buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      assertEquals(
        "example.first.First",
        compiled.classLoader
          .loadClass("example.first.First${'$'}Child")
          .enclosingClass.name,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `Zanzibar support uses the resolved service package`(kind: String) {
    val parsed =
      PlacementRegressionFixture(directory, kind).service(
        "{kotlin: {services: {package: example.service}}}",
        documentPlacement = false,
        zanzibar = true,
      )
    // AsyncAPI has no native Zanzibar annotation contract; its HTTP service IR uses the
    // same server authentication metadata as the HTTP frontends.
    val api =
      if (kind.startsWith("asyncapi")) {
        parsed.copy(
          auth =
            io.outfoxx.sunday.generator.ir.GeneratedAuth(
              zanzibarUserSource =
                io.outfoxx.sunday.generator.ir.GeneratedZanzibarUserSource(
                  jwt =
                    io.outfoxx.sunday.generator.ir
                      .GeneratedZanzibarJwtUserSource(claims = listOf("sub")),
                ),
            ),
        )
      } else {
        parsed
      }
    val registry = generate(api, "server", quarkus = true)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val extractor = compiled.classLoader.loadClass("example.service.ZanzibarJwtUserExtractor")
    assertEquals("io.quarkiverse.zanzibar.UserExtractor", extractor.interfaces.single().name)
    assertEquals(1, registry.buildTypes().keys.count { it.simpleName == "ZanzibarJwtUserExtractor" })
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `invocation Zanzibar support requires an unambiguous shared package`(kind: String) {
    val parsed = PlacementRegressionFixture(directory, kind).serviceContainers(true, documentPlacement = false)
    val api =
      parsed.copy(
        auth =
          io.outfoxx.sunday.generator.ir.GeneratedAuth(
            zanzibarUserSource =
              io.outfoxx.sunday.generator.ir.GeneratedZanzibarUserSource(
                jwt =
                  io.outfoxx.sunday.generator.ir
                    .GeneratedZanzibarJwtUserSource(claims = listOf("sub")),
              ),
          ),
      )
    val configured =
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            defaults =
              GeneratedPlacement(
                kotlin =
                  GeneratedPlacement
                    .Scope(packageName = "example.support"),
              ),
          ),
      )
    val registry = generate(configured, "server", quarkus = true)
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    assertEquals(
      "io.quarkiverse.zanzibar.UserExtractor",
      compiled.classLoader
        .loadClass("example.support.ZanzibarJwtUserExtractor")
        .interfaces
        .single()
        .name,
    )
    assertEquals(1, registry.buildTypes().keys.count { it.simpleName == "ZanzibarJwtUserExtractor" })
    val failure =
      assertThrows(GenerationException::class.java) {
        generate(api, "server", quarkus = true)
      }
    assertTrue(failure.message!!.contains("multiple service packages"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `excluding any container removes nested descendants and companions`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).nestedSelection()
    val registry = generate(api, "sunday")
    for (excluded in listOf<String?>(null, "Root", "Middle")) {
      val owners =
        api.models
          .filter { it.name != excluded }
          .map { java.net.URI(it.source.location) }
          .toSet()
      val selection =
        io.outfoxx.sunday.generator
          .GeneratedOutputSelection(ownerUris = owners)
      val compiled = compileTypesResult(registry.buildTypes(selection))
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      assertEquals(
        String::class.java,
        compiled.classLoader
          .loadClass("example.models.Survivor")
          .getDeclaredField("value")
          .type,
      )
      if (excluded == null) {
        assertEquals("Leaf", compiled.classLoader.loadClass("example.models.Root${'$'}Middle${'$'}Leaf").simpleName)
      } else {
        assertThrows(
          ClassNotFoundException::class.java,
        ) { compiled.classLoader.loadClass("example.models.Root${'$'}Middle") }
        assertThrows(ClassNotFoundException::class.java) {
          compiled.classLoader.loadClass("example.models.Root${'$'}Middle${'$'}Leaf")
        }
        if (excluded ==
          "Root"
        ) {
          assertThrows(ClassNotFoundException::class.java) { compiled.classLoader.loadClass("example.models.Root") }
        }
      }
    }
  }
}
