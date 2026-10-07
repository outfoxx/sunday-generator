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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SundayServiceProvidersTest {
  @Test
  fun `bean archives merge identically and reject conflicting descriptors`(
    @TempDir directory: Path,
  ) {
    val first = directory.resolve("one/META-INF").createDirectories().resolve("beans.xml")
    val second = directory.resolve("two/META-INF").createDirectories().resolve("beans.xml")
    val contents = "<beans bean-discovery-mode=\"annotated\"/>\n"
    first.writeText(contents)
    second.writeText(contents)
    val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
    val task = project.tasks.register("merge", SundayMergeServiceProviders::class.java).get()
    task.descriptors.from(first.toFile(), second.toFile())
    task.outputDirectory.set(directory.resolve("merged").toFile())
    task.merge()
    val archive = directory.resolve("merged/META-INF/beans.xml")
    assertEquals(contents, archive.readText())
    second.writeText("<beans bean-discovery-mode=\"all\"/>")
    assertThrows(IllegalArgumentException::class.java) { task.merge() }
    task.descriptors.setFrom(emptyList<Any>())
    task.merge()
    assertFalse(Files.exists(archive))
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
