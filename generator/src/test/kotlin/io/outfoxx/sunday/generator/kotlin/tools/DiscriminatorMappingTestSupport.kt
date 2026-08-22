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

package io.outfoxx.sunday.generator.kotlin.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.JvmCompilationResult
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals

internal fun reusableDiscriminatorMappingApi(): GeneratedApi =
  GeneratedApi(
    name = "Events API",
    source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
    models =
      listOf(
        GeneratedModel(
          name = "EventType",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("event.one", "unrecognized"),
          unknownValue = "unrecognized",
        ),
        GeneratedModel(
          name = "EventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty("type", GeneratedTypeRef.named("EventType"), required = true),
            ),
          discriminator = "type",
          discriminatorMappings = mapOf("event.one" to GeneratedTypeRef.named("EventOne")),
        ),
        GeneratedModel(
          name = "EventOne",
          kind = GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("EventEnvelope")),
          discriminatorValue = "event.one",
          properties =
            listOf(
              GeneratedModelProperty("data", GeneratedTypeRef.scalar("string"), required = true),
            ),
        ),
        GeneratedModel(
          name = "NotificationEventType",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("event.one", "unrecognized"),
          unknownValue = "unrecognized",
        ),
        GeneratedModel(
          name = "NotificationEventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "type",
                GeneratedTypeRef.named("NotificationEventType"),
                required = true,
              ),
            ),
          discriminator = "type",
          discriminatorMappings = mapOf("event.one" to GeneratedTypeRef.named("EventOne")),
        ),
        GeneratedModel(
          name = "Notification",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "event",
                GeneratedTypeRef.named("NotificationEventEnvelope"),
                required = true,
              ),
            ),
        ),
        GeneratedModel(
          name = "CrossPackageMappedEvent",
          kind = GeneratedModel.Kind.OBJECT,
          targets =
            mapOf(
              "kotlin" to GeneratedTarget(modelPackageName = "io.test.events"),
              "kotlinClient" to GeneratedTarget(modelPackageName = "io.test.events"),
            ),
          properties =
            listOf(
              GeneratedModelProperty("type", GeneratedTypeRef.named("NotificationEventType"), required = true),
            ),
        ),
        GeneratedModel(
          name = "CrossPackageNotificationEventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "type",
                GeneratedTypeRef.named("NotificationEventType"),
                required = true,
              ),
            ),
          discriminator = "type",
          discriminatorMappings =
            mapOf("event.one" to GeneratedTypeRef.named("CrossPackageMappedEvent")),
        ),
      ),
  )

@OptIn(ExperimentalCompilerApi::class)
internal fun assertReusableDiscriminatorMappingRoundTrips(compilation: JvmCompilationResult) {
  val mapper = jacksonObjectMapper()
  compilation.classLoader.use { classLoader ->
    val notificationClass = classLoader.loadClass("io.test.Notification")
    val eventGetter = notificationClass.getMethod("getEvent")

    listOf(
      """{"event":{"type":"event.one","data":"known"}}""" to "io.test.EventOne",
      """{"event":{"type":"future.event","detail":"preserved"}}""" to
        "io.test.NotificationEventEnvelopeUnrecognized",
    ).forEach { (json, expectedEventClassName) ->
      val notification = mapper.readValue(json, notificationClass)
      val event = eventGetter.invoke(notification)

      assertEquals(expectedEventClassName, event.javaClass.name)
      assertEquals(mapper.readTree(json), mapper.valueToTree(notification))
    }
  }
}
