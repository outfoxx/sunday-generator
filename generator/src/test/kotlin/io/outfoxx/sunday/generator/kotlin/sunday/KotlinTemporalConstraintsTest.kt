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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.json.patch.PatchOp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.InvocationTargetException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

@KotlinTest
class KotlinTemporalConstraintsTest {

  @OptIn(ExperimentalCompilerApi::class)
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `temporal restrictions validate ISO wire values without changing storage`(jaxrs: Boolean) {
    val cases =
      listOf(
        Temporal(
          "Timestamp",
          "date-time",
          listOf("2026-09-14T00:00:00Z", "2026-09-14T12:34:56Z", "2026-09-14T00:00:00.123456789+05:30"),
          "2026-09-14T01:00:00+01:00",
          OffsetDateTime::parse,
        ),
        Temporal(
          "Local",
          "datetime-only",
          listOf("2026-09-14T00:00:00", "2026-09-14T12:34:56", "2026-09-14T12:34:56.123456789"),
          "2026-09-15T00:00:00",
          LocalDateTime::parse,
        ),
        Temporal("Time", "time", listOf("00:00:00", "12:34:56", "12:34:56.123456789"), "00:00:01", LocalTime::parse),
        Temporal("Date", "date", listOf("2026-09-14", "2026-12-31"), "2026-09-15", LocalDate::parse),
      )
    val fields =
      cases.flatMap { temporal ->
        listOf(
          GeneratedModelProperty(
            temporal.name.lowercase(),
            GeneratedTypeRef.named("${temporal.name}Alias"),
            required = true,
            allowedValues = temporal.values,
          ),
          GeneratedModelProperty(
            "${temporal.name.lowercase()}Text",
            GeneratedTypeRef.named("${temporal.name}Alias", nullable = true),
            validation =
              mapOf(
                "minLength" to temporal.values.minOf { it.length }.toString(),
                "maxLength" to temporal.values.maxOf { it.length }.toString(),
                "pattern" to temporal.values.joinToString("|", "^(?:", ")$") { Regex.escape(it) },
              ),
          ),
        )
      }
    val base =
      GeneratedModel(
        "TemporalBase",
        GeneratedModel.Kind.OBJECT,
        properties = fields.map { it.copy(allowedValues = null, validation = emptyMap()) },
      )
    val api =
      GeneratedApi(
        name = "Temporal constraints",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          cases.flatMap { temporal ->
            listOf(
              GeneratedModel(
                temporal.name,
                GeneratedModel.Kind.SCALAR_ALIAS,
                aliases = listOf(GeneratedTypeRef.scalar("string", format = temporal.format)),
              ),
              GeneratedModel(
                "${temporal.name}Alias",
                GeneratedModel.Kind.SCALAR_ALIAS,
                aliases = listOf(GeneratedTypeRef.named(temporal.name)),
              ),
            )
          } +
            listOf(
              base,
              base.copy(name = "TemporalOrdinary", properties = fields),
              base.copy(
                name = "TemporalChild",
                properties = fields,
                inherits = listOf(GeneratedTypeRef.named(base.name)),
              ),
            ) +
            if (jaxrs) {
              emptyList()
            } else {
              listOf(
                base.copy(
                  name = "TemporalPatch",
                  properties =
                    fields.map {
                      it.copy(defaultValue = "unused")
                    },
                  patchable = true,
                ),
              )
            },
      )
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
    if (jaxrs) {
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
          listOf("application/json"),
          "API",
          false,
        ),
      ).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test", "https://example.test/", listOf("application/json"), "API"),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val mapper =
      jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(
          SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
          SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE,
        ).disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    val parent = result.classLoader.loadClass("io.test.TemporalBase")
    for (name in listOf("TemporalOrdinary", "TemporalChild") + if (jaxrs) emptyList() else listOf("TemporalPatch")) {
      val model = result.classLoader.loadClass("io.test.$name")
      val patch = name == "TemporalPatch"
      if (name == "TemporalChild") assertTrue(parent.isAssignableFrom(model))
      val constructor = model.constructors.single { it.parameterCount == fields.size }
      val initial = fields.mapIndexed { index, field -> field.name to cases[index / 2].values.first() }.toMap()

      fun arguments(payload: Map<String, String>): Array<Any> =
        fields
          .mapIndexed { index, field ->
            val value = cases[index / 2].parse(payload.getValue(field.name))
            if (patch) PatchOp.set(value) else value
          }.toTypedArray()
      for ((index, temporal) in cases.withIndex()) {
        for (wire in temporal.values) {
          val payload = initial + (fields[index * 2].name to wire) + (fields[index * 2 + 1].name to wire)
          for (value in listOf(
            constructor.newInstance(*arguments(payload)),
            mapper.readValue(mapper.writeValueAsBytes(payload), model),
          )) {
            assertEquals(mapper.valueToTree(payload), mapper.readTree(mapper.writeValueAsBytes(value)))
          }
        }
        // Ordinary validation annotations retain their existing boundary; child and patch checks run in constructors.
        val checked = if (name == "TemporalOrdinary") listOf(index * 2) else listOf(index * 2, index * 2 + 1)
        for (fieldIndex in checked) {
          val payload = initial + (fields[fieldIndex].name to temporal.invalid)
          assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(payload, model) }
          assertTrue(
            assertThrows(InvocationTargetException::class.java) {
              constructor.newInstance(*arguments(payload))
            }.cause is IllegalArgumentException,
          )
          mapper.convertValue(payload, parent)
        }
      }
      if (patch) {
        val empty = mapper.convertValue(emptyMap<String, Any>(), model)
        assertEquals(mapper.createObjectNode(), mapper.valueToTree(empty))
        assertEquals(
          mapper.createObjectNode(),
          mapper.valueToTree(
            constructor.newInstance(
              *Array<Any>(fields.size) {
                PatchOp.none<Any>()
              },
            ),
          ),
        )
        assertTrue(model.getMethod("getTimestamp").invoke(empty) is PatchOp.None<*>)
        val deleted = mapper.convertValue(mapOf("timestampText" to null), model)
        assertTrue(model.getMethod("getTimestampText").invoke(deleted) is PatchOp.Delete<*>)
        assertEquals(mapper.valueToTree(mapOf("timestampText" to null)), mapper.valueToTree(deleted))
        assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf("timestamp" to null), model) }
      }
    }
  }

  private data class Temporal(
    val name: String,
    val format: String,
    val values: List<String>,
    val invalid: String,
    val parse: (String) -> Any,
  )
}
