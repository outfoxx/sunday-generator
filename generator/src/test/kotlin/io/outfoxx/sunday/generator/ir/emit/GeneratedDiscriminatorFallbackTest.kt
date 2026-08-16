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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeneratedDiscriminatorFallbackTest {

  @Test
  fun `resolves tolerant hierarchy fallback from every source frontend`() {
    val ramlUri = resourceUri("raml/ir/tolerant-discriminator.raml")
    val openApiUri = resourceUri("openapi/ir/tolerant-discriminator-3.1.yaml")
    val asyncApiUri = resourceUri("asyncapi/ir/tolerant-discriminator.yaml")
    val composedApi =
      GeneratedApi(
        name = "Jobs API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            tolerantPhase(),
            GeneratedModel(
              name = "JobProgress",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("jobId", GeneratedTypeRef.scalar("string"), required = true),
                ),
              discriminator = "phase",
              discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
            ),
            GeneratedModel(
              name = "JobStarted",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("JobProgress")),
              discriminatorValue = "started",
            ),
          ),
      )
    val apis =
      listOf(
        RamlToGeneratedApi().convert(TestAPIProcessing.process(ramlUri)),
        OpenApiToGeneratedApi().convert(openApiUri),
        AsyncApiToGeneratedApi().convertFragment(asyncApiUri).api,
        GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composedApi)),
      )

    apis.forEach { api ->
      val hierarchy = api.models.single { model -> model.name == "JobProgress" }
      val fallback = hierarchy.discriminatorFallbackOrNull(GeneratedApiIndex(api))
      assertEquals("JobProgressUnknown", fallback?.modelName)
      assertEquals(setOf("started"), fallback?.mappedValues)
      assertEquals(listOf("jobId"), fallback?.baseProperties?.map { property -> property.name })
    }
  }

  @Test
  fun `resolves tolerant enum hierarchy fallback`() {
    val phase = tolerantPhase()
    val hierarchy =
      GeneratedModel(
        name = "JobProgress",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
            GeneratedModelProperty("jobId", GeneratedTypeRef.scalar("string"), required = true),
          ),
        discriminator = "phase",
        discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
      )
    val started =
      GeneratedModel(
        name = "JobStarted",
        kind = GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named("JobProgress")),
      )
    val fallback = hierarchy.discriminatorFallbackOrNull(index(phase, hierarchy, started))!!

    assertEquals("Unknown", fallback.fallbackName)
    assertEquals("JobProgressUnknown", fallback.modelName)
    assertEquals("phase", fallback.discriminatorWireName)
    assertEquals(listOf("jobId"), fallback.baseProperties.map { property -> property.name })
  }

  @Test
  fun `strict enum hierarchy has no fallback`() {
    val phase = tolerantPhase().copy(unknownValue = null)
    val hierarchy =
      GeneratedModel(
        name = "JobProgress",
        kind = GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true)),
        discriminator = "phase",
      )

    assertNull(hierarchy.discriminatorFallbackOrNull(index(phase, hierarchy)))
  }

  @Test
  fun `rejects mapping the tolerant enum fallback value`() {
    val phase = tolerantPhase()
    val hierarchy =
      GeneratedModel(
        name = "JobProgress",
        kind = GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true)),
        discriminator = "phase",
        discriminatorMappings = mapOf("unknown" to GeneratedTypeRef.named("JobProgressUnknown")),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        hierarchy.discriminatorFallbackOrNull(index(phase, hierarchy))
      }

    assertEquals(true, error.message!!.contains("reserved for the generated catch-all variant"))
  }

  @Test
  fun `rejects fallback type name collisions`() {
    val phase = tolerantPhase()
    val hierarchy =
      GeneratedModel(
        name = "JobProgress",
        kind = GeneratedModel.Kind.OBJECT,
        properties = listOf(GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true)),
        discriminator = "phase",
      )
    val collidingModel = GeneratedModel(name = "JobProgressUnknown", kind = GeneratedModel.Kind.OBJECT)

    val error =
      assertThrows(GenerationException::class.java) {
        hierarchy.discriminatorFallbackOrNull(index(phase, hierarchy, collidingModel))
      }

    assertEquals(true, error.message!!.contains("collides with an existing model"))
  }

  private fun tolerantPhase() =
    GeneratedModel(
      name = "JobPhase",
      kind = GeneratedModel.Kind.ENUM,
      values = listOf("started", "unknown"),
      enumValueNames = listOf("Started", "Unknown"),
      unknownValue = "unknown",
    )

  private fun index(vararg models: GeneratedModel) =
    GeneratedApiIndex(
      GeneratedApi(
        name = "Test",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = models.toList(),
      ),
    )

  private fun resourceUri(path: String) = requireNotNull(javaClass.getResource("/$path")).toURI()
}
