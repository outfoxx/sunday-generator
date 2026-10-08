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

import io.outfoxx.sunday.generator.utils.GeneratedProperties
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
import java.util.Properties

/** Combines SPI registrations and compatible application defaults in one source set. */
@CacheableTask
abstract class SundayMergeServiceProviders : DefaultTask() {
  /** Generated META-INF/services descriptors, retaining their generation task dependencies. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val descriptors: ConfigurableFileCollection

  /** Owned generated roots containing application properties at output-relative paths. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val resourceRoots: ConfigurableFileCollection

  /** Dedicated resource directory consumed by the source set's processResources task. */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  /** Preserves every provider and removes registrations from deleted generation inputs. */
  @TaskAction
  fun merge() {
    val resources = sortedMapOf<String, MutableMap<String, String>>()
    val owners = sortedMapOf<String, MutableSet<String>>()
    val artifactValues = sortedMapOf<String, String>()
    resourceRoots.files.sortedBy { it.path }.forEach { root ->
      root.walkTopDown().filter { it.isFile && it.extension == "properties" }.forEach { file ->
        val path = file.relativeTo(root).invariantSeparatorsPath
        val values =
          Properties().apply { file.reader().use { load(it) } }.entries.associate {
            it.key.toString() to
              it.value.toString()
          }
        GeneratedProperties.merge(
          artifactValues,
          values,
          "$path from ${owners.values.flatten() + file.path}",
        )
        val sources = owners.getOrPut(path) { sortedSetOf() }
        GeneratedProperties.merge(
          resources.getOrPut(path) {
            sortedMapOf()
          },
          values,
          "$path from ${sources + file.path}",
        )
        sources.add(file.path)
      }
    }
    val root = outputDirectory.get().asFile
    root.walkTopDown().filter { it.isFile && it.extension == "properties" }.forEach { Files.delete(it.toPath()) }
    resources.forEach { (path, values) ->
      GeneratedProperties
        .write(root.toPath(), path, values)
    }
    val services = sortedMapOf<String, MutableSet<String>>()
    // Remove only the legacy descriptor in this task's dedicated output directory.
    Files.deleteIfExists(
      outputDirectory
        .get()
        .asFile
        .resolve("META-INF/beans.xml")
        .toPath(),
    )
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
