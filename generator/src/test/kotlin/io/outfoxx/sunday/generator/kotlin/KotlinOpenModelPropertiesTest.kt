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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSourcesExtension
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import java.net.URI

@ExtendWith(ResourceExtension::class, CompiledGeneratedSourcesExtension::class)
@ResourceLock("Kotlin")
class KotlinOpenModelPropertiesTest {

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest(name = "{0} / {1}")
  @CsvSource(
    "openapi, sunday",
    "openapi, jaxrs",
    "asyncapi, sunday",
    "asyncapi, jaxrs",
    "raml, sunday",
    "raml, jaxrs",
    "composed, sunday",
    "composed, jaxrs",
  )
  fun `compiled open models preserve nested future fields for every source frontend`(
    frontend: String,
    framework: String,
    @ResourceUri("openapi/ir/open-model-fields.yaml") openApi: URI,
    @ResourceUri("asyncapi/ir/open-model-fields.yaml") asyncApi: URI,
    @ResourceUri("raml/type-gen/open-model-fields.raml") raml: URI,
  ) {
    val sources =
      when (frontend) {
        "openapi" -> listOf(openApi)
        "asyncapi" -> listOf(asyncApi)
        "raml" -> listOf(raml)
        "composed" -> listOf(openApi, asyncApi.resolve("open-model-broker.yaml"))
        else -> error("Unknown source frontend: $frontend")
      }
    val api = GeneratedApiIrExporter().export(sources)
    val registry = registry()
    if (framework == "jaxrs") {
      KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
    }
    val compiled = compileTypesResult(registry.buildTypes())
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    val mapper = jacksonObjectMapper()
    val original =
      mapper.readTree(
        """{"id":"one","data":{"name":"name","additionalProperties":"declared","future":null,"nested":{"unknown":[1,null,{"x":true}]}},"future":{"nested":null}}""",
      )
    val model = compiled.classLoader.loadClass("io.test.Notice")
    val decoded = mapper.treeToValue(original, model)
    expectThat(mapper.readTree(mapper.writeValueAsBytes(decoded))).isEqualTo(original)
    if (frontend == "composed") {
      val receipt = compiled.classLoader.loadClass("io.test.Receipt")
      val receiptOriginal = mapper.readTree("""{"noticeId":"one","future":null}""")
      expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(receiptOriginal, receipt))))
        .isEqualTo(receiptOriginal)
    }

    val inherited = compiled.classLoader.loadClass("io.test.ExtendedRecord")
    val inheritedOriginal = mapper.readTree("""{"id":"one","kind":"extended","future":null}""")
    expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(inheritedOriginal, inherited))))
      .isEqualTo(inheritedOriginal)

    val closed = compiled.classLoader.loadClass("io.test.ClosedRecord")
    expectThrows<Exception> {
      mapper.readValue("""{"id":"one","unexpected":true}""", closed)
    }
    val closedChild = compiled.classLoader.loadClass("io.test.ClosedChild")
    val closedOriginal = mapper.readTree("""{"id":"one","name":"name"}""")
    expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(closedOriginal, closedChild))))
      .isEqualTo(closedOriginal)
    expectThrows<Exception> {
      mapper.readValue("""{"id":"one","name":"name","unexpected":true}""", closedChild)
    }
    val namedChild = compiled.classLoader.loadClass("io.test.NamedChild")
    val namedOriginal = mapper.readTree("""{"id":"one","additionalProperties":"declared","future":null}""")
    expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(namedOriginal, namedChild))))
      .isEqualTo(namedOriginal)
    if (frontend != "raml") {
      val typed = compiled.classLoader.loadClass("io.test.TypedRecord")
      val typedOriginal = mapper.readTree("""{"id":"one","count":2}""")
      expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(typedOriginal, typed))))
        .isEqualTo(typedOriginal)
      expectThrows<Exception> { mapper.readValue("""{"id":"one","count":"invalid"}""", typed) }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `compiled known and fallback events retain envelope and subtype fields`(
    @ResourceUri("asyncapi/ir/tolerant-discriminator.yaml") uri: URI,
  ) {
    for (jaxrs in listOf(false, true)) {
      val api = GeneratedApiIrExporter().export(uri)
      val registry = registry()
      if (jaxrs) {
        KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
      } else {
        KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
      }
      val compiled = compileTypesResult(registry.buildTypes())
      expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
      val mapper = jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      val event = compiled.classLoader.loadClass("io.test.JobProgress")
      for (body in listOf(
        """{"phase":"started","jobId":"one","taskCount":2,"future":null,"nested":{"value":null}}""",
        """{"phase":"future-phase","jobId":"one","future":null,"nested":{"value":null}}""",
      )) {
        val original = mapper.readTree(body)
        expectThat(mapper.readTree(mapper.writeValueAsBytes(mapper.treeToValue(original, event)))).isEqualTo(original)
        val sequence = "[$body,$body]"
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, event)
        val nested = mapper.readValue<Any>(sequence, listType)
        expectThat(mapper.readTree(mapper.writeValueAsBytes(nested))).isEqualTo(mapper.readTree(sequence))
        expectThrows<Exception> { mapper.readValue(body + " {}", event) }
      }
    }
  }

  private fun registry() =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Client,
      setOf(
        KotlinTypeRegistry.Option.ImplementModel,
        KotlinTypeRegistry.Option.JacksonAnnotations,
        KotlinTypeRegistry.Option.PreserveUnknownFields,
      ),
      problemLibrary = KotlinProblemLibrary.SUNDAY,
    )
}
