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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.io.File

/** Changing generation inputs must not leave stale compiled classes on a consumer classpath. */
class GeneratedOutputReplacementTest {
  @TempDir
  lateinit var directory: File

  @ParameterizedTest
  @CsvSource("false, false", "true, false", "false, true", "true, true")
  fun `unowned handwritten output is rejected before execution or cache restoration`(
    cached: Boolean,
    alreadyOwned: Boolean,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'unowned-output'")
    directory.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Items, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            properties: {id: {type: string}}
      """.trimIndent(),
    )
    directory.resolve("build.gradle").writeText(
      """
      plugins { id 'java'; id 'io.outfoxx.sunday-generator' }
      tasks.register('generate', io.outfoxx.sunday.generator.gradle.SundayGenerate) {
        source(file('api.yaml'))
        framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
        mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
        outputDir.set(layout.projectDirectory.dir(providers.gradleProperty('targetOutput').get()))
        generateService.set(false)
      }
      """.trimIndent(),
    )

    fun runner(output: String) =
      GradleRunner.create().withProjectDir(directory).withPluginClasspath().withArguments(
        "generate",
        "-PtargetOutput=$output",
        if (cached) "--build-cache" else "--no-build-cache",
        "--stacktrace",
      )
    if (cached) runner("build/generated/safe").build()
    if (alreadyOwned) {
      val generated = runner("src/main/kotlin").build()
      if (cached) expectThat(generated.task(":generate")?.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
    }
    val handwritten = directory.resolve("src/main/kotlin/Handwritten.kt").also { it.parentFile.mkdirs() }
    val original = "class Handwritten"
    handwritten.writeText(original)
    val result = runner("src/main/kotlin").buildAndFail()
    expectThat(result.output).contains("Unsafe generated output directory")
    expectThat(handwritten.readText()).isEqualTo(original)
  }

  @ParameterizedTest
  @ValueSource(strings = ["project", "source", "included-source", "staging"])
  fun `unsafe output roots fail without removing inputs`(target: String) {
    val project = directory.resolve("project").also { it.mkdirs() }
    directory.resolve("external").mkdirs()
    project.resolve("settings.gradle").writeText("rootProject.name = 'safe-output'")
    val source = project.resolve("specs/api.yaml").also { it.parentFile.mkdirs() }
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Items, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            required: [id]
            properties: {id: {type: string}}
      """.trimIndent(),
    )
    val included = project.resolve("included/shared.yaml").also { it.parentFile.mkdirs() }
    included.writeText("type: string")
    val output =
      when (target) {
        "project" -> "."
        "source" -> "specs"
        "included-source" -> "included"
        else -> "build"
      }
    project.resolve("build.gradle").writeText(
      """
      plugins { id 'java'; id 'io.outfoxx.sunday-generator' }
      tasks.register('generate', io.outfoxx.sunday.generator.gradle.SundayGenerate) {
        source(file('specs/api.yaml'))
        allSources.set(files('specs/api.yaml', 'included/shared.yaml'))
        sourceBaseDirectory.set(layout.projectDirectory.dir('../external'))
        framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
        mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
        outputDir.set(layout.projectDirectory.dir(providers.gradleProperty('targetOutput').get()))
        generateService.set(false)
      }
      """.trimIndent(),
    )
    val sourceBefore = source.readText()
    val includedBefore = included.readText()

    fun runner(outputDirectory: String) =
      GradleRunner
        .create()
        .withProjectDir(project)
        .withPluginClasspath()
        .withArguments(
          "generate",
          "-PtargetOutput=$outputDirectory",
          "--build-cache",
          "--max-workers=2",
          "--stacktrace",
        )

    // Populate the cache first: output roots must be checked before a cached result can be restored.
    runner("generated").build()
    val result = runner(output).buildAndFail()

    expectThat(result.output).contains("Unsafe generated output directory")
    expectThat(source.readText()).isEqualTo(sourceBefore)
    expectThat(included.readText()).isEqualTo(includedBefore)
    expectThat(project.resolve("settings.gradle").exists()).isTrue()
  }

  @Test
  fun `renaming a package replaces only its task-owned output after successful generation`() {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'output-replacement'")
    val build = directory.resolve("build.gradle")
    build.writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.20'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      sundayGenerations {
        ['changing', 'independent'].each { generation ->
          create(generation) {
            source.set(files('api.yaml'))
            framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
            mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
            pkgName.set(generation == 'changing' ? 'test.before' : 'test.independent')
            generateService.set(false)
            disableJacksonAnnotations.set(true)
            disableValidationConstraints.set(true)
          }
        }
      }
      """.trimIndent(),
    )
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Items, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            required: [id]
            properties: {id: {type: string}}
      """.trimIndent(),
    )

    fun runner() =
      GradleRunner
        .create()
        .withProjectDir(directory)
        .withPluginClasspath()
        .withArguments("classes", "--max-workers=2", "--stacktrace")
    runner().build()
    build.writeText(build.readText().replace("test.before", "test.after"))
    runner().build()
    // Inspect generated output only after both revisions have compiled.
    val classes = directory.resolve("build/classes/kotlin/main")
    expectThat(classes.resolve("test/before/Item.class").exists()).isFalse()
    expectThat(classes.resolve("test/after/Item.class").exists()).isTrue()
    expectThat(classes.resolve("test/independent/Item.class").exists()).isTrue()
    val generated = directory.resolve("build/generated/sources/sunday/sundayGenerate_changing/test/after/Item.kt")
    val previous = generated.readText()
    build.appendText("\nsundayGenerations.changing.generateBrokerServices.set(true)\n")
    runner().buildAndFail()
    expectThat(generated.readText()).isEqualTo(previous)
  }
}
