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

@file:Suppress("UnstableApiUsage", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")

package io.outfoxx.sunday.generator.gradle

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.kotlin.KotlinGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator.Options.BaseUriMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.nio.file.Files
import java.util.EnumSet
import javax.inject.Inject

open class SundayGenerate
@Inject constructor(
  objects: ObjectFactory
) : DefaultTask() {

  @InputFiles
  val source: Property<FileCollection> = objects.property(FileCollection::class.java)

  @InputFiles
  @Optional
  val includes: Property<FileCollection> = objects.property(FileCollection::class.java)

  @Input
  val framework: Property<TargetFramework> = objects.property(TargetFramework::class.java)

  @Input
  val mode: Property<GenerationMode> = objects.property(GenerationMode::class.java)

  @Input
  @Optional
  val generateModel: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val generateService: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val pkgName: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val modelPkgName: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val servicePkgName: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val serviceSuffix: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val problemBaseUri: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val coroutines: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val reactiveResponseType: Property<String> = objects.property(String::class.java)

  @Input
  @Optional
  val explicitSecurityParameters: Property<Boolean> = objects.property(Boolean::class.java)

  @Input
  @Optional
  val baseUriMode: Property<BaseUriMode> = objects.property(BaseUriMode::class.java)

  @Input
  @Optional
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java)

  @OutputDirectory
  val outputDir: Property<Directory> = objects.directoryProperty()

  @TaskAction
  fun generate() {

    val defaultOutputDir = project.layout.buildDirectory.get()

    val framework = this.framework.get()
    val mode = this.mode.get()
    val pkgName = this.pkgName.orNull
    val modelPkgName = require(this.modelPkgName.orNull ?: pkgName, "modelPkgName or pkgName required")
    val servicePkgName = require(this.servicePkgName.orNull ?: pkgName, "servicePkgName or pkgName required")
    val serviceSuffix = this.serviceSuffix.orNull ?: "API"
    val problemBaseUri = this.problemBaseUri.orNull ?: "http://example.com/"
    val outputDir = this.outputDir.getOrElse(defaultOutputDir)
    val outputDirFile = outputDir.asFile
    val generateModel = this.generateModel.getOrElse(true)
    val generateService = this.generateService.getOrElse(true)

    val categories = EnumSet.noneOf(GeneratedTypeCategory::class.java)
    if (generateModel) {
      categories.add(GeneratedTypeCategory.Model)
    }
    if (generateService) {
      categories.add(GeneratedTypeCategory.Service)
    }

    Files.createDirectories(outputDir.asFile.toPath())

    if (shouldClean(outputDirFile)) {
      val filter = PatternSet().include("**/*.kt")
      project.delete(outputDir.asFileTree.matching(filter))
    }

    val options = EnumSet.allOf(KotlinTypeRegistry.Option::class.java)
    if (disableJacksonAnnotations.getOrElse(false)) {
      options.remove(JacksonAnnotations)
    }
    if (disableModelImplementations.getOrElse(false)) {
      options.remove(ImplementModel)
    }
    if (disableValidationConstraints.getOrElse(false)) {
      options.remove(ValidationConstraints)
    }

    val typeRegistry = KotlinTypeRegistry(modelPkgName, mode, options)

    val apiProcessor = APIProcessor()

    source.get().forEach { file ->

      if (file.isDirectory || file.extension == "git") {
        return
      }

      val processed = apiProcessor.process(file.toURI())

      processed.validationLog.forEach {
        val level =
          when (it.level) {
            APIProcessor.Result.Level.Error -> LogLevel.ERROR
            APIProcessor.Result.Level.Warning -> LogLevel.WARN
            APIProcessor.Result.Level.Info -> LogLevel.INFO
          }
        val errorFile = File(it.file).relativeToOrSelf(project.rootDir)
        logger.log(level, "$errorFile:${it.line}: ${it.message}")
      }

      if (!processed.isValid) {
        throw InvalidUserDataException("$file is invalid")
      }

      val generator =
        when (framework) {
          TargetFramework.JAXRS ->
            KotlinJAXRSGenerator(
              processed.document,
              typeRegistry,
              KotlinJAXRSGenerator.Options(
                coroutines.orNull ?: false,
                reactiveResponseType.orNull,
                explicitSecurityParameters.orNull ?: false,
                baseUriMode.orNull,
                servicePkgName,
                problemBaseUri,
                defaultMediaTypes.get(),
                serviceSuffix
              )
            )

          TargetFramework.Sunday ->
            KotlinSundayGenerator(
              processed.document,
              typeRegistry,
              KotlinGenerator.Options(
                servicePkgName,
                problemBaseUri,
                defaultMediaTypes.get(),
                serviceSuffix
              )
            )
        }

      try {
        generator.generateServiceTypes()
      } catch (x: GenerationException) {
        logger.error("${x.file}:${x.line}: ${x.message}")
        throw InvalidUserDataException("$file is invalid")
      }
    }

    typeRegistry.generateFiles(categories, outputDirFile.toPath())
  }

  private fun <T> require(value: T?, message: String): T {
    if (value == null) {
      throw InvalidUserDataException(message)
    }
    return value
  }

  private fun shouldClean(dir: File): Boolean {
    if (dir == project.projectDir || dir == project.rootDir || dir.absolutePath == "/") {
      return false
    }
    return true
  }
}
