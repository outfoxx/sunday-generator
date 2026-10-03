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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.patchableApi
import io.outfoxx.sunday.json.patch.PatchOp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
@Tag("models")
@Tag("validation")
@Tag("requests")
class KotlinPatchableTypesTest {

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @CsvSource(
    "client,openapi",
    "server,openapi",
    "resource,openapi",
    "sunday,openapi",
    "sunday,collisions",
    "client,raml",
    "server,raml",
    "resource,raml",
    "sunday,raml",
    "client,raml-auto",
    "server,raml-auto",
    "resource,raml-auto",
    "sunday,raml-auto",
    "client,composed",
    "server,composed",
    "resource,composed",
    "sunday,composed",
    "client,composed-collisions",
    "server,composed-collisions",
    "resource,composed-collisions",
    "sunday,composed-collisions",
    "sunday,asyncapi",
    "client,reference",
    "server,reference",
    "resource,reference",
    "sunday,reference",
    "split,openapi",
    "split,composed",
  )
  fun `PATCH signatures and codecs preserve omitted set and deletion states`(
    target: String,
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory)
    val mode = if (target in setOf("server", "resource", "split")) GenerationMode.Server else GenerationMode.Client
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        mode,
        setOf(
          KotlinTypeRegistry.Option.ImplementModel,
          KotlinTypeRegistry.Option.JacksonAnnotations,
          KotlinTypeRegistry.Option.ValidationConstraints,
          KotlinTypeRegistry.Option.UseJakartaPackages,
        ),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )

    fun generate(registry: KotlinTypeRegistry) {
      if (target == "sunday") {
        KotlinSundayIrGenerator(
          api,
          registry,
          KotlinSundayOptions("io.test", "https://test/", listOf("application/json"), "API"),
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
            defaultServicePackageName = "io.test",
            defaultProblemBaseUri = "https://test/",
            defaultMediaTypes = listOf("application/json"),
            serviceSuffix = "API",
            quarkus = true,
            resourceAdapters = target in setOf("resource", "split"),
          ),
        ).generateServiceTypes()
      }
    }
    generate(registry)
    val types =
      if (target == "split") {
        val facadeRegistry =
          KotlinTypeRegistry(
            "io.test",
            null,
            mode,
            registry.options - KotlinTypeRegistry.Option.ImplementModel,
            problemLibrary = KotlinProblemLibrary.SUNDAY,
          )
        generate(facadeRegistry)
        registry.buildTypes().filterValues { it.tag(GeneratedTypeCategory::class) == GeneratedTypeCategory.Model } +
          facadeRegistry.buildTypes().filterValues {
            it.tag(
              GeneratedTypeCategory::class,
            ) == GeneratedTypeCategory.Service
          }
      } else {
        registry.buildTypes()
      }
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val patch =
      compiled.classLoader.loadClass(
        "io.test." + if (frontend == "composed-collisions") "SomeRequestPatch2" else "SomeRequestPatch",
      )
    val ordinary = compiled.classLoader.loadClass("io.test.SomeRequest")
    val methods =
      types.keys
        .filter { it == it.topLevelClassName() }
        .flatMap {
          compiled.classLoader
            .loadClass(it.canonicalName)
            .declaredMethods
            .toList()
        }
    if (frontend != "asyncapi") assertTrue(methods.any { it.name == "updateRequest" })
    methods.filter { it.name == "updateRequest" }.forEach { assertTrue(patch in it.parameterTypes) }
    methods.filter { it.name in setOf("createRequest", "plainRequest") }.forEach {
      assertTrue(
        ordinary in it.parameterTypes,
      )
    }
    val mapper = jacksonObjectMapper()
    for (json in listOf(
      "{}",
      "{\"title\":\"new title\"}",
      "{\"description\":null}",
      """{"title":null,"display-name":null,"optional-alias":null}""",
      """{"required-nullable":"new","required-alias":"new"}""",
      "{\"description\":\"text\",\"count\":2,\"state\":\"active\",\"display-name\":\"Name\"}",
    )) {
      val value = mapper.readValue(json, patch)
      assertEquals(mapper.readTree(json), mapper.valueToTree(value))
    }
    assertEquals(io.outfoxx.sunday.json.patch.UpdateOp::class.java, patch.getMethod("getCount").returnType)
    assertEquals(io.outfoxx.sunday.json.patch.UpdateOp::class.java, patch.getMethod("getRequiredNullable").returnType)
    assertEquals(io.outfoxx.sunday.json.patch.UpdateOp::class.java, patch.getMethod("getRequiredAlias").returnType)
    assertEquals(PatchOp::class.java, patch.getMethod("getTitle").returnType)
    val empty = patch.getConstructor().newInstance()
    assertTrue(patch.getMethod("getTitle").invoke(empty) is PatchOp.None<*>)
    assertEquals(mapper.readTree("{}"), mapper.valueToTree(empty))
    assertEquals(emptySet<String>(), nativeConstraintPaths("jakarta", empty, "Request"))
    val unknown = mapper.readValue("""{"state":"future"}""", patch)
    assertEquals(emptySet<String>(), nativeConstraintPaths("jakarta", unknown, "Response"))
    assertTrue(nativeConstraintPaths("jakarta", unknown, "Request").any { it.startsWith("state") })
    patch.getMethod("setTitle", io.outfoxx.sunday.json.patch.PatchOp::class.java).invoke(empty, PatchOp.set("x"))
    assertTrue(nativeConstraintPaths("jakarta", empty, "Request").any { it.startsWith("title") })
    patch.getMethod("setTitle", io.outfoxx.sunday.json.patch.PatchOp::class.java).invoke(empty, PatchOp.none<String>())
    assertEquals(emptySet<String>(), nativeConstraintPaths("jakarta", empty, "Request"))
    for (json in listOf(
      """{"required-nullable":null}""",
      """{"required-alias":null}""",
      """{"labels":{"bad":"x"}}""",
      "{\"title\":\"x\"}",
      "{\"count\":0}",
      "{\"count\":null}",
    )) {
      assertThrows(Exception::class.java, { mapper.readValue(json, patch) }, json)
    }
    assertThrows(Exception::class.java) { mapper.readValue("{}", ordinary) }
    val value = mapper.readValue("""{"count":2,"required-nullable":null,"required-alias":null}""", ordinary)
    assertEquals(if (frontend.startsWith("raml")) null else "initial", ordinary.getMethod("getTitle").invoke(value))
    val mapperType = com.fasterxml.jackson.databind.ObjectMapper::class.java
    val snapshot = ordinary.getMethod(if (frontend == "collisions") "patch_" else "patch", mapperType)
    val difference = ordinary.getMethod(if (frontend == "collisions") "patch_" else "patch", ordinary, mapperType)
    val merge = ordinary.getMethod(if (frontend == "collisions") "merge_" else "merge", patch, mapperType)
    val snapshotError =
      assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
        snapshot.invoke(value, mapper)
      }.cause as com.fasterxml.jackson.databind.JsonMappingException
    assertTrue(snapshotError.path.any { it.fieldName in setOf("required-nullable", "required-alias") })
    assertEquals(mapper.readTree("{}"), mapper.valueToTree(difference.invoke(value, value, mapper)))
    val changed =
      mapper.readValue(
        """{"count":3,"required-nullable":"valid","required-alias":null,"title":"Changed"}""",
        ordinary,
      )
    val changes = difference.invoke(changed, value, mapper)
    assertEquals(
      mapper.readTree("""{"count":3,"required-nullable":"valid","title":"Changed"}"""),
      mapper.valueToTree(changes),
    )
    val restored = merge.invoke(value, changes, mapper)
    assertEquals(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(changed), mapper.valueToTree(restored))
    assertEquals(2, ordinary.getMethod("getCount").invoke(value))
    val deleted = merge.invoke(changed, mapper.readValue("""{"title":null}""", patch), mapper)
    assertEquals(null, ordinary.getMethod("getTitle").invoke(deleted))
    val nonNull = mapper.readValue("""{"count":3,"required-nullable":"valid","required-alias":"valid"}""", ordinary)
    assertEquals(
      mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(nonNull),
      mapper.valueToTree(snapshot.invoke(nonNull, mapper)),
    )
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) { difference.invoke(value, nonNull, mapper) }
    assertEquals(emptySet<String>(), nativeConstraintPaths("jakarta", restored, "Response"))
    val nestedBase =
      mapper.readValue(
        """{"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Original","note":"Remove","child":{"name":"Child","note":"Keep"}},"numbers":[1,null,2],"labels":{"keep":"yes","remove":"old"}}""",
        ordinary,
      )
    val nestedUpdated =
      mapper.readValue(
        """{"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Changed","note":"Remove","child":{"name":"Updated child","note":"Keep"}},"numbers":[3,null],"labels":{"keep":"yes","remove":"old"}}""",
        ordinary,
      )
    val nestedDiff = difference.invoke(nestedUpdated, nestedBase, mapper)
    assertEquals(
      mapper.readTree("""{"details":{"name":"Changed","child":{"name":"Updated child"}},"numbers":[3,null]}"""),
      mapper.valueToTree(nestedDiff),
    )
    val nestedMerged = merge.invoke(nestedBase, nestedDiff, mapper)
    assertEquals(
      mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(nestedUpdated),
      mapper.valueToTree(nestedMerged),
    )
    val nestedDeleted =
      merge.invoke(
        nestedBase,
        mapper.readValue("""{"details":{"note":null},"labels":{"remove":null}}""", patch),
        mapper,
      )
    val nestedTree = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(nestedDeleted)
    assertEquals("Original", nestedTree.path("details").path("name").textValue())
    assertFalse(nestedTree.path("details").has("note"))
    assertFalse(nestedTree.path("labels").has("remove"))
    assertTrue(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(nestedBase).path("details").has("note"))
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
      merge.invoke(value, mapper.readValue("""{"details":{"note":"new"}}""", patch), mapper)
    }
  }
}
