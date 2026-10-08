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

import io.outfoxx.sunday.generator.gradle.GeneratedOutputOwnership
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SundayNativeSecurityTest {
  private val sundayDependency =
    System.getProperty("sunday.kotlin.quarkus.classpath")?.let { classpath ->
      val files = classpath.split(File.pathSeparator).joinToString(", ") { "'" + it.replace("'", "\\'") + "'" }
      "implementation files($files)"
    } ?: "implementation 'io.outfoxx.sunday:sunday-jaxrs-quarkus:${System.getProperty("sunday.kotlin.version")}'"

  @Test
  fun `native policies compile with default Gradle registry options`(
    @TempDir directory: Path,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'native-security'")
    directory.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Native OIDC, version: 1.0.0}
      security: [{bearerAuth: []}]
      paths:
        /ping:
          get:
            operationId: ping
            responses: {'204': {description: Success}}
      components:
        securitySchemes:
          bearerAuth:
            type: http
            scheme: bearer
            bearerFormat: JWT
            x-sunday-security:
              server:
                provider: bearerAuth
                quarkus: {mode: oidc}
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
        implementation 'io.quarkus:quarkus-rest'
        implementation 'io.quarkus:quarkus-oidc'
        $sundayDependency
      }
      kotlin {
        jvmToolchain(21)
        compilerOptions { allWarningsAsErrors = true }
      }
      sundayGenerations {
        server {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Server)
          quarkus.set(true)
          resourceAdapters.set(true)
          enforceSecuritySchemes.set(true)
          useJakartaPackages.set(true)
          coroutines.set(true)
          pkgName.set('example.nativeauth')
          outputDir.set(layout.buildDirectory.dir('generated/native'))
        }
      }
      """.trimIndent(),
    )
    val result =
      GradleRunner
        .create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments("compileKotlin", "--stacktrace")
        .build()
    assertEquals(TaskOutcome.SUCCESS, result.task(":sundayGenerate_server")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
    val output = directory.resolve("build/generated/native")
    output
      .resolve("example/nativeauth/LegacyMetadata.kt")
      .also { it.parent.createDirectories() }
      .writeText("package example.nativeauth\nclass LegacyMetadata\n")
    output
      .resolve("META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource")
      .also {
        it.parent.createDirectories()
      }.writeText("example.nativeauth.LegacyMetadata\n")

    java.nio.file.Files
      .delete(output.resolve(".sunday-generated-output.json"))
    GeneratedOutputOwnership
      .record(output.toFile(), ":sundayGenerate_server")

    fun jar(legacy: Boolean = false) =
      GradleRunner
        .create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments(
          if (legacy) {
            listOf(
              "compileKotlin",
              "-x",
              "sundayGenerate_server",
              "--stacktrace",
            )
          } else {
            listOf("jar", "--stacktrace")
          },
        ).build()
    jar(legacy = true)
    val artifact = directory.resolve("build/libs/native-security.jar").toFile()
    org.junit.jupiter.api.Assertions.assertTrue(
      directory.resolve("build/classes/kotlin/main/example/nativeauth/LegacyMetadata.class").toFile().isFile,
    )
    directory
      .resolve(
        "api.yaml",
      ).toFile()
      .appendText(
        "\nx-sunday-quarkus-config:\n  server:\n    properties:\n      quarkus.zanzibar.filter.timeout: 7S\n",
      )
    jar()
    JarFile(artifact).use {
      assertNull(it.getEntry("example/nativeauth/LegacyMetadata.class"))
      assertNull(it.getEntry("META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource"))
      assertNotNull(it.getEntry("META-INF/microprofile-config.properties"))
    }
  }
}
