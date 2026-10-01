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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SundayServiceProvidersTest {
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
