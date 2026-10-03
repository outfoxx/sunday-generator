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

import io.outfoxx.sunday.generator.gradle.SundayGenerate
import io.outfoxx.sunday.generator.gradle.SundayGenerations
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SundayAutoPatchableOptionsTest {
  @Test
  fun `task and DSL default to automatic promotion and allow disabling it`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("java")
    project.pluginManager.apply("io.outfoxx.sunday-generator")
    @Suppress("UNCHECKED_CAST")
    val generations = project.extensions.getByName("sundayGenerations") as SundayGenerations
    val generation = generations.create("client")
    val task = project.tasks.named("sundayGenerate_client", SundayGenerate::class.java).get()
    val standalone = project.tasks.register("standalone", SundayGenerate::class.java).get()
    assertTrue(generation.autoPatchable.get())
    assertTrue(task.autoPatchable.get())
    assertTrue(standalone.autoPatchable.get())
    generation.autoPatchable.set(false)
    assertFalse(task.autoPatchable.get())
  }

  @Test
  fun `toggling automatic promotion recompiles output while explicit annotations remain effective`(
    @TempDir directory: File,
  ) {
    val dependencies =
      System.getProperty("sunday.kotlin.classpath")?.let { classpath ->
        val files = classpath.split(File.pathSeparator).joinToString(", ") { "'" + it.replace("'", "\\'") + "'" }
        "implementation files($files)"
      } ?: "implementation 'io.outfoxx.sunday:sunday-core:${System.getProperty("sunday.kotlin.version")}'"
    directory.resolve("settings.gradle").writeText("rootProject.name = 'auto-patchable'")
    directory.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.20'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      dependencies { $dependencies }
      sundayGenerations {
        client {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          pkgName.set('test.model')
          generateService.set(false)
          disableJacksonAnnotations.set(true)
          disableValidationConstraints.set(true)
          autoPatchable.set(providers.gradleProperty('autoPatchable').map { it.toBoolean() }.orElse(true))
        }
      }
      """.trimIndent(),
    )
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Items, version: '1'}
      paths:
        /items:
          post:
            operationId: updateItem
            requestBody:
              content:
                application/merge-patch+json:
                  schema: {${'$'}ref: '#/components/schemas/Item'}
            responses: {'204': {description: Updated}}
      components:
        schemas:
          Item:
            type: object
            x-sunday-patchable: false
            properties: {name: {type: string}}
      """.trimIndent(),
    )

    fun build(vararg flags: String) =
      GradleRunner
        .create()
        .withProjectDir(directory)
        .withPluginClasspath()
        .withArguments(
          listOf("classes", "--configuration-cache", "--no-build-cache", "--max-workers=2", "--stacktrace") + flags,
        ).build()

    val patchClass = directory.resolve("build/classes/kotlin/main/test/model/ItemPatch.class")
    val ordinaryClass = directory.resolve("build/classes/kotlin/main/test/model/Item.class")
    assertEquals(TaskOutcome.SUCCESS, build().task(":sundayGenerate_client")?.outcome)
    assertTrue(patchClass.isFile)
    assertTrue(ordinaryClass.isFile)
    assertEquals(TaskOutcome.SUCCESS, build("-PautoPatchable=false").task(":sundayGenerate_client")?.outcome)
    assertFalse(patchClass.exists())
    assertTrue(ordinaryClass.isFile)
    val repeated = build("-PautoPatchable=false")
    assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":sundayGenerate_client")?.outcome)
    assertTrue(repeated.output.contains("Reusing configuration cache."), repeated.output)
    source.writeText(source.readText().replace("x-sunday-patchable: false", "x-sunday-patchable: true"))
    assertEquals(TaskOutcome.SUCCESS, build("-PautoPatchable=false").task(":sundayGenerate_client")?.outcome)
    assertTrue(patchClass.isFile)
    assertTrue(ordinaryClass.isFile)
  }
}
