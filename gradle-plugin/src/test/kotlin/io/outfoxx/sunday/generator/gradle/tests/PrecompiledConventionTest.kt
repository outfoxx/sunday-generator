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
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File

/** The unshaded composite plugin must expose every type used by its public Kotlin DSL. */
class PrecompiledConventionTest {
  @TempDir
  lateinit var directory: File

  @Test
  fun `precompiled Kotlin convention compiles against a source-substituted plugin`() {
    val source = File(System.getProperty("sunday.generator.source-root"))
    val upstream = directory.resolve("upstream").also { it.mkdirs() }
    // Isolate nested builds from the running test build's project locks and generated outputs.
    for (path in listOf("build-logic", "gradle", "generator/src/main", "gradle-plugin/src/main")) {
      copySources(source.resolve(path), upstream.resolve(path))
    }
    for (path in listOf("gradle.properties", "generator/build.gradle.kts", "gradle-plugin/build.gradle.kts")) {
      source.resolve(path).copyTo(upstream.resolve(path).also { it.parentFile.mkdirs() })
    }
    upstream.resolve("settings.gradle.kts").writeText(
      """
      pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
      dependencyResolutionManagement {
        repositories {
          mavenCentral()
          maven("https://repository.mulesoft.org/nexus/content/repositories/public/")
        }
      }
      includeBuild("build-logic") { name = "generator-build-logic" }
      rootProject.name = "sunday-generator"
      include("generator", "gradle-plugin")
      """.trimIndent(),
    )
    upstream.resolve("build.gradle.kts").writeText(
      """
      plugins {
        alias(libs.plugins.kotlin.jvm) apply false
        alias(libs.plugins.vanniktech.maven.publish) apply false
      }
      allprojects {
        configurations.configureEach {
          resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.everit-org.json-schema:org.everit.json.schema"))
              .using(module("com.github.erosb:everit-json-schema:1.12.2"))
          }
        }
      }
      """.trimIndent(),
    )
    val conventions = directory.resolve("conventions").also { it.mkdirs() }
    conventions.resolve("settings.gradle.kts").writeText(
      """
      rootProject.name = "consumer-conventions"
      includeBuild("../upstream")
      """.trimIndent(),
    )
    conventions.resolve("build.gradle.kts").writeText(
      """
      plugins { `kotlin-dsl` }
      repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repository.mulesoft.org/nexus/content/repositories/public/")
      }
      dependencies { implementation("io.outfoxx.sunday:gradle-plugin:2.0.0-SNAPSHOT") }
      configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
          substitute(module("com.github.everit-org.json-schema:org.everit.json.schema"))
            .using(module("com.github.erosb:everit-json-schema:1.12.2"))
        }
      }
      """.trimIndent(),
    )
    conventions.resolve("src/main/kotlin/contract.gradle.kts").also {
      it.parentFile.mkdirs()
      it.writeText(
        """
        import io.outfoxx.sunday.generator.GenerationMode
        import io.outfoxx.sunday.generator.gradle.TargetFramework
        plugins {
          java
          id("io.outfoxx.sunday-generator")
        }
        sundayGenerations {
          create("models") {
            framework.set(TargetFramework.JAXRS)
            mode.set(GenerationMode.Server)
            generateModel.set(true)
            generateService.set(false)
            preserveUnknownFields.set(true)
          }
        }
        """.trimIndent(),
      )
    }
    val result =
      GradleRunner
        .create()
        .withProjectDir(conventions)
        .withArguments("compileKotlin", "--max-workers=2", "--stacktrace")
        .build()
    expectThat(result.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  private fun copySources(
    source: File,
    destination: File,
  ) {
    source.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".kotlin") }.forEach { file ->
      val target = destination.resolve(file.relativeTo(source).path)
      if (file.isDirectory) target.mkdirs() else file.copyTo(target)
    }
  }
}
