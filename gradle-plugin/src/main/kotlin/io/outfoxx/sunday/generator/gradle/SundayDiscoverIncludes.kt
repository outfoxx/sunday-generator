/*
 * Copyright 2020 Outfox, Inc.
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

@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.gradle

import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.location
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import javax.inject.Inject

@CacheableTask
abstract class SundayDiscoverIncludes
  @Inject
  constructor(
    objects: ObjectFactory,
  ) : SourceTask() {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    val bootstrapIncludes: Property<FileCollection> = objects.property(FileCollection::class.java)

    @OutputFile
    val includesIndexFile: RegularFileProperty = objects.fileProperty()

    private val apiProcessor = APIProcessor()

    @TaskAction
    fun discover() {
      val roots = mutableSetOf<File>()
      roots.addAll(source.files)
      bootstrapIncludes.orNull?.files?.let { roots.addAll(it) }

      val includes = mutableSetOf<File>()

      roots
        .filter { it.isFile && it.extension == "raml" }
        .forEach { file ->
          val processed = apiProcessor.process(file.toURI())
          processed.validationLog.forEach {
            val level =
              when (it.level) {
                APIProcessor.Result.Level.Error -> LogLevel.ERROR
                APIProcessor.Result.Level.Warning -> LogLevel.WARN
                APIProcessor.Result.Level.Info -> LogLevel.INFO
              }
            logger.log(level, "${it.file}:${it.line}: ${it.message}")
          }

          processed
            .document
            .allUnits
            .mapNotNull { unit -> unit.location }
            .mapNotNull { location ->
              when {
                location.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(location)) }.getOrNull()
                location.isNotBlank() -> File(location)
                else -> null
              }
            }.filter { it.exists() }
            .forEach { includes.add(it.canonicalFile) }
        }

      val outputFile = includesIndexFile.get().asFile
      outputFile.parentFile.mkdirs()
      outputFile.writeText(
        includes
          .map { it.absolutePath }
          .sorted()
          .joinToString("\n"),
      )
    }
  }
