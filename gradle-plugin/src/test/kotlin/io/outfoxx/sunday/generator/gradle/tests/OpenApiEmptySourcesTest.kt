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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiEmptySourcesTest {
  @Test
  fun `parallel generations skip empty sources and recover after deleting the last specification`(
    @TempDir directory: Path,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'empty-sources'")
    directory.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.10'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      sundayGenerations {
        ['first', 'second'].each { generation ->
          create(generation) {
            source.set(fileTree('specs') { include '**/*.yaml' })
            framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.Sunday)
            mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
            pkgName.set('test.' + generation)
            generateService.set(false)
            disableJacksonAnnotations.set(true)
            disableValidationConstraints.set(true)
          }
        }
      }
      """.trimIndent(),
    )
    val sources = Files.createDirectories(directory.resolve("specs"))

    fun run() =
      GradleRunner
        .create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments("sundayGenerateAll", "compileKotlin", "--parallel", "--build-cache", "--stacktrace")
        .build()
    val empty = run()
    listOf("first", "second").forEach { generation ->
      assertEquals(TaskOutcome.NO_SOURCE, empty.task(":sundayDiscoverIncludes_$generation")?.outcome)
      assertEquals(TaskOutcome.NO_SOURCE, empty.task(":sundayGenerate_$generation")?.outcome)
    }
    val source = sources.resolve("api.yaml")
    val specification =
      """
      openapi: 3.1.0
      info: {title: Empty sources, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            required: [id]
            properties: {id: {type: string}}
      """.trimIndent()
    source.writeText(specification)
    val generated = run()
    assertTrue(generated.task(":compileKotlin")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
    Files.delete(source)
    val removed = run()
    listOf("first", "second").forEach { generation ->
      assertEquals(TaskOutcome.SUCCESS, removed.task(":sundayDiscoverIncludes_$generation")?.outcome)
      assertEquals(TaskOutcome.SUCCESS, removed.task(":sundayGenerate_$generation")?.outcome)
    }
    val output = directory.resolve("build/generated/sunday")
    if (Files.exists(output)) {
      Files.walk(output).use { paths -> assertFalse(paths.anyMatch { it.toString().endsWith(".kt") }) }
    }
    val stillEmpty = run()
    listOf("first", "second").forEach { generation ->
      assertEquals(TaskOutcome.NO_SOURCE, stillEmpty.task(":sundayDiscoverIncludes_$generation")?.outcome)
      assertEquals(TaskOutcome.NO_SOURCE, stillEmpty.task(":sundayGenerate_$generation")?.outcome)
    }
    source.writeText(specification)
    val restored = run()
    listOf("first", "second").forEach { generation ->
      assertEquals(TaskOutcome.FROM_CACHE, restored.task(":sundayGenerate_$generation")?.outcome)
    }
    assertEquals(TaskOutcome.FROM_CACHE, restored.task(":compileKotlin")?.outcome)
  }
}
