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
import io.outfoxx.sunday.generator.gradle.TargetFramework
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SundayGenerateBrokerOptionsTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `broker service generation defaults follow the target framework`() {
    val project = ProjectBuilder.builder().build()
    val sundayTask = project.tasks.register("sundayBrokerOptions", SundayGenerate::class.java).get()
    sundayTask.framework.set(TargetFramework.Sunday)
    assertTrue(sundayTask.generateBrokerServices.get())

    val jaxRsTask = project.tasks.register("jaxRsBrokerOptions", SundayGenerate::class.java).get()
    jaxRsTask.framework.set(TargetFramework.JAXRS)
    assertFalse(jaxRsTask.generateBrokerServices.get())
  }

  @Test
  fun `explicit broker service generation overrides the framework default`() {
    val project = ProjectBuilder.builder().build()
    val task = project.tasks.register("disabledBrokerOptions", SundayGenerate::class.java).get()
    task.framework.set(TargetFramework.Sunday)
    task.generateBrokerServices.set(false)

    assertFalse(task.generateBrokerServices.get())
  }

  @Test
  fun `Gradle DSL disables broker facades without removing message models`() {
    writeTestProject(TargetFramework.Sunday, generateBrokerServices = false)

    val result = runGeneration()
    assertEquals(TaskOutcome.SUCCESS, result.task(":sundayGenerate_client")?.outcome)

    val output = projectDir.resolve("build/generated/sources/sunday/sundayGenerate_client")
    assertFalse(output.walkTopDown().any { file -> file.name == "EventsBroker.kt" })
    assertTrue(output.walkTopDown().any { file -> file.name == "PlatformEvent.kt" })
  }

  @Test
  fun `Gradle DSL preserves Sunday broker generation by default`() {
    writeTestProject(TargetFramework.Sunday, generateBrokerServices = null)

    val result = runGeneration()
    assertEquals(TaskOutcome.SUCCESS, result.task(":sundayGenerate_client")?.outcome)

    val output = projectDir.resolve("build/generated/sources/sunday/sundayGenerate_client")
    assertTrue(output.walkTopDown().any { file -> file.name == "EventsBroker.kt" })
  }

  @Test
  fun `Gradle DSL rejects broker generation for JAX-RS`() {
    writeTestProject(TargetFramework.JAXRS, generateBrokerServices = true)

    val result =
      GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("sundayGenerate_client", "--stacktrace")
        .buildAndFail()

    assertTrue(result.output.contains("Broker service generation is not supported for Kotlin/JAX-RS"), result.output)
  }

  private fun runGeneration() =
    GradleRunner
      .create()
      .withProjectDir(projectDir)
      .withPluginClasspath()
      .withArguments("sundayGenerate_client", "--stacktrace")
      .build()

  private fun writeTestProject(
    framework: TargetFramework,
    generateBrokerServices: Boolean?,
  ) {
    projectDir.resolve("settings.gradle").writeText("rootProject.name = 'broker-options-test'\n")
    projectDir.resolve("build.gradle").writeText(
      """
      import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
      import static io.outfoxx.sunday.generator.GenerationMode.*

      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.10'
        id 'io.outfoxx.sunday-generator'
      }

      repositories {
        mavenCentral()
      }

      sundayGenerations {
        client {
          source.set(fileTree('src/main/sunday') { it.include('**/*.yaml') })
          framework.set(${framework.name})
          mode.set(Client)
          pkgName.set('io.test')
          modelPkgName.set('io.test.model')
          servicePkgName.set('io.test.service')
          ${generateBrokerServices?.let { value -> "generateBrokerServices.set($value)" }.orEmpty()}
        }
      }
      """.trimIndent(),
    )
    projectDir.resolve("src/main/sunday").mkdirs()
    projectDir.resolve("src/main/sunday/events.yaml").writeText(
      """
      asyncapi: 3.1.0
      info:
        title: Broker Events API
        version: 1.0.0
      channels:
        platformEvents:
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
              exchange:
                name: platform.events
      components:
        schemas:
          PlatformEvent:
            type: object
            properties:
              id:
                type: string
      """.trimIndent(),
    )
  }
}
