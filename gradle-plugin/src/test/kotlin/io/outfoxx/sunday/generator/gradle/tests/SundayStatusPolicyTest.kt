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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SundayStatusPolicyTest {
  @Test
  fun `mixed status policies compile through Gradle profile selection`(
    @TempDir directory: Path,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'status-policies'")
    directory.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Status, version: '1'}
      x-sunday-placement:
        kotlin: {package: example.status}
      paths:
        /ping:
          get:
            operationId: ping
            x-sunday-policy:
              profiles:
                internal:
                  client:
                    retry: {retryOn: [java.io.IOException, 5XX], abortOn: [404]}
                    circuitBreaker: {skipOn: [401, 403, 404]}
            responses: {'204': {description: Success}}
      """.trimIndent(),
    )
    directory.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.10'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      dependencies {
        implementation platform('io.quarkus.platform:quarkus-bom:${System.getProperty("quarkus.version")}')
        implementation 'io.quarkus:quarkus-rest-client'
        implementation 'io.quarkus:quarkus-smallrye-fault-tolerance'
        implementation 'io.quarkiverse.resteasy-problem:quarkus-resteasy-problem:3.32.1'
        implementation 'io.outfoxx.sunday:sunday-jaxrs-quarkus:${System.getProperty("sunday.kotlin.version")}'
      }
      kotlin {
        jvmToolchain(21)
        compilerOptions { allWarningsAsErrors = true }
      }
      sundayGenerations {
        client {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          quarkus.set(true)
          useJakartaPackages.set(true)
          coroutines.set(true)
          profile.set('internal')
          outputDir.set(layout.buildDirectory.dir('generated/status'))
        }
      }
      """.trimIndent(),
    )

    fun build() =
      GradleRunner
        .create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments("jar", "--configuration-cache", "--no-watch-fs", "--stacktrace")
        .build()
    val result = build()
    assertEquals(TaskOutcome.SUCCESS, result.task(":sundayGenerate_client")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)

    fun descriptor(): String =
      JarFile(directory.resolve("build/libs/status-policies.jar").toFile()).use { jar ->
        val descriptors =
          jar
            .entries()
            .asSequence()
            .filter { it.name == "META-INF/beans.xml" }
            .toList()
        assertEquals(1, descriptors.size)
        assertTrue(jar.entries().asSequence().any { it.name.endsWith("API${'$'}Guards.class") })
        jar.getInputStream(descriptors.single()).bufferedReader().readText()
      }
    assertTrue(descriptor().contains("bean-discovery-mode=\"annotated\""))
    val custom = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"all\" version=\"4.0\"/>"
    val application = directory.resolve("src/main/resources/META-INF").createDirectories().resolve("beans.xml")
    application.writeText(custom)
    build()
    assertEquals(custom, descriptor())
    assertTrue(build().output.contains("Reusing configuration cache"))
    java.nio.file.Files
      .delete(application)
    build()
    assertTrue(descriptor().contains("bean-discovery-mode=\"annotated\""))
  }
}
