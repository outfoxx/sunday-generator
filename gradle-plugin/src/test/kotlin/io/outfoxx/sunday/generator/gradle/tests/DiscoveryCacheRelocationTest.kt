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

package io.outfoxx.sunday.generator.gradle.tests

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.security.MessageDigest

/** Exercises checkout-local discovery with a build cache shared by independent projects. */
class DiscoveryCacheRelocationTest {

  @TempDir
  lateinit var directory: File

  @ParameterizedTest(name = "old checkout remains accessible: {0}")
  @ValueSource(booleans = [false, true])
  fun `relocated broker builds rediscover local inputs and reuse downstream outputs`(keepOriginal: Boolean) {
    val original = writeProject("original")
    val relocated = writeProject("relocated")
    val control = writeProject("control")
    val seeded = build(original)
    assertEquals(TaskOutcome.SUCCESS, seeded.task(":broker:sundayDiscoverIncludes_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, seeded.task(":broker:compileKotlin")?.outcome)

    if (keepOriginal) {
      original.resolve("shared/identifier.yaml").writeText("type: integer\nformat: int32\n")
    } else {
      assertTrue(original.deleteRecursively())
    }

    val restored = build(relocated)
    assertEquals(TaskOutcome.SUCCESS, restored.task(":broker:sundayDiscoverIncludes_events")?.outcome)
    assertEquals(TaskOutcome.FROM_CACHE, restored.task(":broker:sundayGenerate_events")?.outcome)
    assertEquals(TaskOutcome.FROM_CACHE, restored.task(":broker:compileKotlin")?.outcome)
    assertLocalIndexes(relocated)

    val uncached = build(control, "--no-build-cache")
    assertEquals(TaskOutcome.SUCCESS, uncached.task(":broker:sundayGenerate_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, uncached.task(":broker:compileKotlin")?.outcome)
    assertEquals(compiledClasses(control), compiledClasses(relocated))

    // Force a local generation and compile so cached classes cannot mask a stale source reader.
    val regenerated = build(relocated, "clean", "--no-build-cache")
    assertEquals(TaskOutcome.SUCCESS, regenerated.task(":broker:sundayGenerate_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, regenerated.task(":broker:compileKotlin")?.outcome)
    assertEquals(compiledClasses(control), compiledClasses(relocated))
  }

  @Test
  fun `shared transitive edits invalidate discovery in existing and fresh checkouts`() {
    val original = writeProject("original")
    build(original)
    build(original)
    val unchanged = build(original)
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":broker:sundayDiscoverIncludes_events")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":broker:sundayGenerate_events")?.outcome)

    original.resolve("shared/identifier.yaml").writeText("type: integer\nformat: int32\n")
    writeConsumer(original, "Int")
    val changed = build(original)
    assertEquals(TaskOutcome.SUCCESS, changed.task(":broker:sundayDiscoverIncludes_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, changed.task(":broker:sundayGenerate_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, changed.task(":broker:compileTestKotlin")?.outcome)
    assertLocalIndexes(original)

    val fresh = writeProject("fresh")
    fresh.resolve("shared/identifier.yaml").writeText("type: boolean\n")
    writeConsumer(fresh, "Boolean")
    val freshBuild = build(fresh)
    assertEquals(TaskOutcome.SUCCESS, freshBuild.task(":broker:sundayDiscoverIncludes_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, freshBuild.task(":broker:sundayGenerate_events")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, freshBuild.task(":broker:compileTestKotlin")?.outcome)
    assertLocalIndexes(fresh)
  }

  private fun build(
    project: File,
    vararg arguments: String,
  ): BuildResult =
    GradleRunner
      .create()
      .withProjectDir(project)
      .withPluginClasspath()
      .withArguments(
        arguments.filter { it == "clean" } +
          listOf(":broker:compileTestKotlin", "--build-cache", "--max-workers=2", "--stacktrace") +
          arguments.filter { it != "clean" },
      ).build()

  private fun assertLocalIndexes(project: File) {
    val indexes = project.resolve("broker/build/generated/sunday")
    assertEquals(
      listOf(project.resolve("broker/api.yaml").canonicalPath),
      indexes.resolve("roots/events.txt").readLines(),
    )
    assertEquals(
      setOf("broker/api.yaml", "shared/event.yaml", "shared/identifier.yaml")
        .map { project.resolve(it).canonicalPath }
        .toSet(),
      indexes.resolve("all-sources/events.txt").readLines().toSet(),
    )
  }

  // Compare compiler outputs only after TestKit has successfully compiled each complete project.
  private fun compiledClasses(project: File): Map<String, String> {
    val root = project.resolve("broker/build/classes/kotlin")
    assertTrue(root.resolve("main/io/test/service/EventsBroker.class").isFile)
    assertTrue(root.resolve("main/io/test/model/PlatformEvent.class").isFile)
    assertTrue(root.resolve("test/ConsumerKt.class").isFile)
    return root.walkTopDown().filter { it.isFile && it.extension == "class" }.associate {
      it.relativeTo(root).invariantSeparatorsPath to
        MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
    }
  }

  private fun writeProject(name: String): File {
    val project = directory.resolve(name).also { it.mkdirs() }
    project.resolve("settings.gradle").writeText(
      """
      rootProject.name = 'discovery-cache-test'
      include 'broker'
      buildCache { local { directory = file('../build-cache') } }
      """.trimIndent(),
    )
    project.resolve("gradle.properties").writeText("kotlin.compiler.execution.strategy=in-process\n")
    project.resolve("broker").mkdirs()
    project.resolve("broker/build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.10'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      dependencies {
        implementation 'io.outfoxx.sunday:sunday-broker:${System.getProperty("sunday.kotlin.version")}'
      }
      sundayGenerations {
        events {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.Sunday)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          placementConfig.set(file('placement.yaml'))
          generateClientConfig.set(false)
          disableJacksonAnnotations.set(true)
          disableValidationConstraints.set(true)
        }
      }
      """.trimIndent(),
    )
    project.resolve("broker/placement.yaml").writeText(
      """
      defaults:
        kotlin:
          package: io.test
          models: {package: io.test.model}
          services: {package: io.test.service}
      """.trimIndent(),
    )
    project.resolve("broker/api.yaml").writeText(
      """
      asyncapi: 3.1.0
      info: {title: Broker Events API, version: 1.0.0}
      channels:
        events:
          address: platform.events
          x-sunday-service: Events
          publish:
            operationId: publishPlatformEvent
            message:
              name: PlatformEvent
              contentType: application/json
              payload:
                ${'$'}ref: '#/components/schemas/PlatformEvent'
          bindings:
            amqp:
              exchange: {name: platform.events}
      components:
        schemas:
          PlatformEvent:
            ${'$'}ref: '../shared/event.yaml'
      """.trimIndent(),
    )
    project.resolve("shared").mkdirs()
    project.resolve("shared/event.yaml").writeText(
      """
      type: object
      required: [id]
      properties:
        id:
          ${'$'}ref: './identifier.yaml'
      """.trimIndent(),
    )
    project.resolve("shared/identifier.yaml").writeText("type: string\n")
    writeConsumer(project, "String")
    return project
  }

  private fun writeConsumer(
    project: File,
    identifierType: String,
  ) {
    project.resolve("broker/src/test/kotlin").mkdirs()
    project.resolve("broker/src/test/kotlin/Consumer.kt").writeText(
      """
      import io.test.model.PlatformEvent
      import io.test.service.EventsBroker

      fun identifier(event: PlatformEvent): $identifierType = event.id
      fun broker(value: EventsBroker): EventsBroker = value
      """.trimIndent(),
    )
  }
}
