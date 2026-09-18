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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.InvocationTargetException

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinContentTypeTest {

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `server headers and enum converters compile across frontends and resource forms`(frontend: String) {
    val paths =
      when (frontend) {
        "raml" -> listOf("raml/ir/content-type.raml")
        "composed" -> listOf("openapi/ir/content-type.yaml", "asyncapi/ir/content-type.yaml")
        else -> listOf("$frontend/ir/content-type.yaml")
      }
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(paths.map { javaClass.getResource("/$it")!!.toURI() })
    // Singleton headers can carry constant metadata, but still need a server argument.
    val withConstants =
      api.copy(
        services =
          api.services.map { service ->
            service.copy(
              operations =
                service.operations.map { operation ->
                  if (operation.id == "putConstant") {
                    operation.copy(parameters = operation.parameters.map { it.copy(constantValue = "image/png") })
                  } else {
                    operation
                  }
                },
            )
          },
      )
    for (target in listOf("javax", "jakarta", "quarkus")) {
      for (adapters in listOf(false, true)) {
        val registry =
          KotlinTypeRegistry(
            "io.test",
            null,
            GenerationMode.Server,
            buildSet {
              add(KotlinTypeRegistry.Option.ImplementModel)
              add(KotlinTypeRegistry.Option.ValidationConstraints)
              if (target != "javax") add(KotlinTypeRegistry.Option.UseJakartaPackages)
              if (adapters) add(KotlinTypeRegistry.Option.JacksonAnnotations)
            },
          )
        KotlinJAXRSIrGenerator(
          withConstants,
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
            listOf("application/json"),
            "API",
            target == "quarkus",
            resourceAdapters = adapters,
          ),
        ).generateServiceTypes()
        val result = compileTypesResult(registry.buildTypes())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val sources =
          registry.buildTypes().keys.filter { it == it.topLevelClassName() }.map { type ->
            CompiledGeneratedSources.source(
              GeneratedCodeLanguage.Kotlin,
              "${type.packageName.replace('.', '/')}/${type.simpleName}.kt",
            )
          }
        val generated = sources.joinToString("\n")
        val headerAnnotation = if (target == "quarkus") "RestHeader" else "HeaderParam"
        assertTrue(generated.contains("@$headerAnnotation(value = \"Content-Type\")"), generated)
        assertTrue(generated.contains("@Consumes(value = [\"image/*\"])"), generated)
        if (frontend != "asyncapi") {
          assertTrue(generated.contains("@Consumes(value = [\"*/*\"])"), generated)
          assertTrue(generated.contains("@Consumes(value = [\"application/octet-stream\"])"), generated)
          assertTrue(generated.contains("@$headerAnnotation(value = \"cOnTeNt-TyPe\")"), generated)
          assertTrue(
            Regex("@DefaultValue\\(value =\\s*\"application/octet-stream\"\\)").containsMatchIn(generated),
            generated,
          )
          val enumType = result.classLoader.loadClass("io.test.AvatarContentType")
          val fromString = enumType.getMethod("fromString", String::class.java)
          for (value in listOf("image/png", "image/jpeg")) {
            assertEquals(value, fromString.invoke(null, value).toString())
          }
          for (value in listOf("image/webp", "IMAGE/PNG", "image/png; profile=test")) {
            assertThrows(InvocationTargetException::class.java) { fromString.invoke(null, value) }
          }
          val tolerant = result.classLoader.loadClass("io.test.TolerantContentType")
          for (value in listOf("image/png", "image/avif")) {
            val parsed = tolerant.getMethod("fromString", String::class.java).invoke(null, value)
            assertEquals(value, tolerant.getMethod("getWireValue").invoke(parsed))
          }
          if (adapters) {
            assertTrue(generated.contains("this.delegate.putEnum(contentType, body)"), generated)
            assertTrue(generated.contains("this.delegate.putConstant(contentType, body)"), generated)
          }
        }
        if (frontend == "asyncapi" || frontend == "composed") {
          val mediaType = result.classLoader.loadClass("io.test.EventMediaType")
          val fromString = mediaType.getMethod("fromString", String::class.java)
          val png = fromString.invoke(null, "image/png")
          val jpeg = fromString.invoke(null, "image/jpeg")
          val envelope = result.classLoader.loadClass("io.test.PngEnvelope")
          assertTrue(envelope.constructors.any { it.parameterTypes.toList() == listOf(mediaType) }, generated)
          val constructor = envelope.getConstructor(mediaType)
          constructor.newInstance(png)
          assertThrows(InvocationTargetException::class.java) { constructor.newInstance(jpeg) }
          if (adapters) {
            val mapper = jacksonObjectMapper()
            mapper.readValue("""{"contentType":"image/png"}""", envelope)
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
              mapper.readValue("""{"contentType":"image/jpeg"}""", envelope)
            }
          }
        }
        if (!adapters) assertFalse(generated.contains("@JsonCreator"), generated)
      }
    }
  }
}
