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

package io.outfoxx.sunday.generator.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

/** Combines SPI registration from independently generated clients in one source set. */
@CacheableTask
abstract class SundayMergeServiceProviders : DefaultTask() {
  /** Generated META-INF/services descriptors, retaining their generation task dependencies. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val descriptors: ConfigurableFileCollection

  /** Dedicated resource directory consumed by the source set's processResources task. */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  /** Preserves every provider and removes registrations from deleted generation inputs. */
  @TaskAction
  fun merge() {
    val services = sortedMapOf<String, MutableSet<String>>()
    val archives = descriptors.files.filter { it.isFile && it.name == "beans.xml" }
    val archive = outputDirectory.get().asFile.resolve("META-INF/beans.xml")
    if (archives.isNotEmpty()) {
      val contents = archives.map { it.readText().trim() }.distinct()
      require(contents.size == 1) { "Conflicting generated CDI bean archive descriptors" }
      archive.parentFile.mkdirs()
      archive.writeText(contents.single() + "\n")
    } else {
      Files.deleteIfExists(archive.toPath())
    }
    descriptors.files.filter { it.isFile && it.name != "beans.xml" }.forEach { descriptor ->
      val providers = services.getOrPut(descriptor.name) { sortedSetOf() }
      descriptor
        .readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .forEach(providers::add)
    }
    val output = outputDirectory.get().asFile.resolve("META-INF/services")
    output.mkdirs()
    output
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.name !in services }
      .forEach { Files.delete(it.toPath()) }
    services.forEach { (service, providers) ->
      output.resolve(service).writeText(providers.joinToString("\n", postfix = "\n"))
    }
  }
}
