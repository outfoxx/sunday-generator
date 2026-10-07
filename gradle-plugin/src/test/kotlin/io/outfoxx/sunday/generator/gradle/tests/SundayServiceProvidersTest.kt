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

import io.outfoxx.sunday.generator.gradle.SundayMergeServiceProviders
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SundayServiceProvidersTest {
  @ParameterizedTest
  @CsvSource("src/main/resources,false", "extra-resources,false", "task-generated,false", "task-generated,true")
  fun `packaged resources preserve application bean archives without generating a descriptor`(
    resourceDirectory: String,
    clean: Boolean,
    @TempDir directory: Path,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'bean-archives'")
    directory.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Test, version: 1.0.0}
      security: [{bearer: []}]
      components:
        securitySchemes:
          bearer:
            type: http
            scheme: bearer
            x-sunday-security:
              client:
                provider: service
                flow: clientCredentials
                tokenUrl: https://identity.example/token
                quarkus: {mode: acquire}
      paths:
        /items:
          get:
            operationId: fetchItems
            responses: {'204': {description: OK}}
      """.trimIndent(),
    )
    val resourceConfiguration =
      if (resourceDirectory == "task-generated") {
        """
        def applicationResources = tasks.register('zzzApplicationResources', Sync) {
          from('task-generated')
          into(layout.buildDirectory.dir('application-resources'))
        }
        sourceSets.main.resources.srcDir(applicationResources)
        """.trimIndent()
      } else {
        "sourceSets.main.resources.srcDir('extra-resources')"
      }
    directory.resolve("build.gradle").writeText(
      """
      plugins { id 'java'; id 'io.outfoxx.sunday-generator' }
      $resourceConfiguration
      sundayGenerations {
        client {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          quarkus.set(true)
          pkgName.set('io.test')
          clientConfigurationFileName.set('ClientDefaults.kt')
        }
      }
      tasks.register('resourceJar', Jar) {
        from(tasks.named('processResources'))
        archiveFileName.set('resources.jar')
      }
      """.trimIndent(),
    )

    fun build() =
      GradleRunner
        .create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments(
          listOfNotNull(if (clean) "clean" else null, "resourceJar", "--configuration-cache", "--stacktrace"),
        ).build()

    fun descriptor(): String? =
      JarFile(directory.resolve("build/libs/resources.jar").toFile()).use { jar ->
        val archives =
          jar
            .entries()
            .asSequence()
            .filter { it.name == "META-INF/beans.xml" }
            .toList()
        assertTrue(archives.size <= 1)
        val registration = jar.getEntry("META-INF/services/io.smallrye.config.ConfigSourceFactory")
        assertTrue(registration != null)
        assertEquals("io.test.ClientDefaults\n", jar.getInputStream(registration).bufferedReader().readText())
        archives.singleOrNull()?.let { jar.getInputStream(it).bufferedReader().readText() }
      }
    build()
    assertEquals(null, descriptor())
    val application =
      directory
        .resolve(resourceDirectory)
        .resolve("META-INF")
        .createDirectories()
        .resolve("beans.xml")
    val custom = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"all\" version=\"4.0\"/>"
    application.writeText(custom)
    build()
    assertEquals(custom, descriptor())
    assertTrue(build().output.contains("Reusing configuration cache"))
    application.writeText(custom + "\n<!-- application-owned -->\n")
    build()
    assertEquals(application.readText(), descriptor())
    Files.delete(application)
    build()
    assertEquals(null, descriptor())
    val script = directory.resolve("build.gradle")
    val enabled = script.readText()
    for (setting in listOf("generateClientConfiguration", "generateApplicationMetadata")) {
      script.writeText(enabled + "\nsundayGenerations.client.$setting.set(false)\n")
      build()
      JarFile(directory.resolve("build/libs/resources.jar").toFile()).use { jar ->
        assertEquals(null, jar.getEntry("META-INF/services/io.smallrye.config.ConfigSourceFactory"))
      }
      script.writeText(enabled)
      build()
      assertEquals(null, descriptor())
    }
  }

  @Test
  fun `merge removes its legacy bean descriptor without modifying application resources`(
    @TempDir directory: Path,
  ) {
    val application = directory.resolve("src/main/resources/META-INF").createDirectories().resolve("beans.xml")
    application.writeText("")
    val archive = directory.resolve("merged/META-INF").createDirectories().resolve("beans.xml")
    archive.writeText("<beans/>\n")
    val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
    val task = project.tasks.register("merge", SundayMergeServiceProviders::class.java).get()
    task.outputDirectory.set(directory.resolve("merged").toFile())
    task.merge()
    assertFalse(Files.exists(archive))
    assertEquals("", application.readText())
  }

  @Test
  fun `failed stale registration deletion fails the merge`(
    @TempDir directory: Path,
  ) {
    assumeTrue(directory.fileSystem.supportedFileAttributeViews().contains("posix"))
    val services = directory.resolve("merged/META-INF/services").createDirectories()
    val obsolete = services.resolve("removed.Service").apply { writeText("removed.Provider\n") }
    val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
    val task = project.tasks.register("merge", SundayMergeServiceProviders::class.java).get()
    task.outputDirectory.set(directory.resolve("merged").toFile())
    val permissions = Files.getPosixFilePermissions(services)
    try {
      Files.setPosixFilePermissions(services, PosixFilePermissions.fromString("r-xr-xr-x"))
      assumeFalse(Files.isWritable(services), "The test requires enforced directory permissions")
      assertThrows(IOException::class.java) { task.merge() }
      assertTrue(Files.exists(obsolete))
    } finally {
      Files.setPosixFilePermissions(services, permissions)
    }
  }

  @Test
  fun `multiple generators retain all native providers and remove stale registrations`(
    @TempDir directory: Path,
  ) {
    val first = directory.resolve("one/META-INF/services").createDirectories()
    val second = directory.resolve("two/META-INF/services").createDirectories()
    val one = first.resolve("io.smallrye.config.ConfigSourceFactory").apply { writeText("one.Config\nshared.Config\n") }
    val two =
      second.resolve("io.smallrye.config.ConfigSourceFactory").apply {
        writeText("two.Config\nshared.Config # duplicate\n")
      }
    val obsolete = first.resolve("removed.Service").apply { writeText("removed.Provider\n") }
    val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
    val task = project.tasks.register("merge", SundayMergeServiceProviders::class.java).get()
    task.descriptors.from(one.toFile(), two.toFile(), obsolete.toFile())
    task.outputDirectory.set(directory.resolve("merged").toFile())
    task.merge()
    val services = directory.resolve("merged/META-INF/services")
    assertEquals("one.Config\nshared.Config\ntwo.Config\n", services.resolve(one.fileName).readText())
    task.descriptors.setFrom(two.toFile())
    task.merge()
    assertEquals("shared.Config\ntwo.Config\n", services.resolve(two.fileName).readText())
    assertFalse(services.resolve(obsolete.fileName).toFile().exists())
  }
}
