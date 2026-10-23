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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.discriminatedPatchApi
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@KotlinTest
@Tag("models")
@Tag("validation")
@Tag("requests")
class DiscriminatedPatchTest {
  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @CsvSource(
    "server,openapi",
    "resource,openapi",
    "client,openapi",
    "sunday,openapi",
    "server,explicit",
    "server,raml",
    "server,asyncapi",
    "server,composed",
    "server,renamed",
    "server,tolerant",
    "sunday,tolerant",
  )
  fun `discriminated patch codecs preserve required tag and partial fields`(
    target: String,
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = discriminatedPatchApi(frontend, directory)
    val registry =
      KotlinTypeRegistry(
        null,
        if (target in setOf("server", "resource")) GenerationMode.Server else GenerationMode.Client,
        setOf(
          KotlinTypeRegistry.Option.ImplementModel,
          KotlinTypeRegistry.Option.JacksonAnnotations,
          KotlinTypeRegistry.Option.ValidationConstraints,
          KotlinTypeRegistry.Option.UseJakartaPackages,
        ),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test", "io.test"),
          "https://test/",
          listOf("application/json"),
          "API",
        ),
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
          placement = kotlinPlacement("io.test", "io.test"),
          defaultProblemBaseUri = "https://test/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = true,
          resourceAdapters = target == "resource",
        ),
      ).generateServiceTypes()
    }
    val compiled = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val patch = compiled.classLoader.loadClass("io.test.AnimalPatch")
    val mapper = jacksonObjectMapper()
    val tag = if (frontend == "renamed") "animal-kind" else "kind"
    for (json in listOf(
      """{"$tag":"cat"}""",
      """{"$tag":"cat","nickname":null}""",
      """{"$tag":"cat","name":"Misty"}""",
      """{"$tag":"cat","custom":"extension"}""",
    )) {
      val value = mapper.readValue(json, patch)
      assertEquals("CatPatch", value.javaClass.simpleName)
      assertEquals(mapper.readTree(json), mapper.valueToTree(value))
    }
    for (json in listOf(
      "{}",
      """{"$tag":null}""",
      """{"$tag":"lizard"}""",
      """{"$tag":"cat","name":null}""",
      """{"$tag":"cat","detail":null}""",
    )) {
      assertThrows(Exception::class.java) { mapper.readValue(json, patch) }
    }

    val ordinary = compiled.classLoader.loadClass("io.test.Animal")
    val mapperType = com.fasterxml.jackson.databind.ObjectMapper::class.java
    val base =
      mapper.readValue(
        """{"$tag":"cat","name":"Misty","detail":null,"nickname":"Kit","indoor":true}""",
        ordinary,
      )
    assertThrows(Exception::class.java) { mapper.readValue("{}", ordinary) }
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
      ordinary.getMethod("patch", mapperType).invoke(base, mapper)
    }
    val complete = mapper.readValue("""{"$tag":"cat","name":"Misty","detail":"info"}""", ordinary)
    assertEquals(
      mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(complete),
      mapper.valueToTree(ordinary.getMethod("patch", mapperType).invoke(complete, mapper)),
    )
    val merge = ordinary.getMethod("merge", patch, mapperType)
    val diff = ordinary.getMethod("patch", ordinary, mapperType)
    assertEquals(mapper.readTree("""{"$tag":"cat"}"""), mapper.valueToTree(diff.invoke(base, base, mapper)))
    val updated = merge.invoke(base, mapper.readValue("""{"$tag":"cat","nickname":null}""", patch), mapper)
    assertEquals(
      mapper.readTree("""{"$tag":"cat","name":"Misty","detail":null,"indoor":true}"""),
      mapper.valueToTree(updated),
    )
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
      merge.invoke(base, mapper.readValue("""{"$tag":"dog"}""", patch), mapper)
    }
    val dog = mapper.readValue("""{"$tag":"dog","name":"Rex","detail":null}""", ordinary)
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) { diff.invoke(dog, base, mapper) }
    for (value in listOf("true", "null", "false")) {
      assertThrows(Exception::class.java) { mapper.readValue("""{"$tag":"cat","trained":$value}""", patch) }
    }
  }
}
