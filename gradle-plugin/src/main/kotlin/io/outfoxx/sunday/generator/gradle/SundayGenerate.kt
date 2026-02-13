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
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator.Options.BaseUriMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.EnumSet
import javax.inject.Inject

@CacheableTask
open class SundayGenerate
@Inject constructor(
  objects: ObjectFactory,
) : SourceTask() {

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  override fun getSource(): FileTree = super.getSource()

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  @Optional
  val includes: Property<FileCollection> = objects.property(FileCollection::class.java)

  @Input
  val framework: Property<TargetFramework> = objects.property(TargetFramework::class.java)

  @Input
  val mode: Property<GenerationMode> = objects.property(GenerationMode::class.java)

  @Input
  @Optional
  val generateModel: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  @Input
  @Optional
  val generateService: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  @Input
  @Optional
  val pkgName: Property<String> = objects.property(String::class.java).convention("com.example")

  @Input
  @Optional
  val modelPkgName: Property<String> = objects.property(String::class.java).convention(pkgName)

  @Input
  @Optional
  val servicePkgName: Property<String> = objects.property(String::class.java).convention(pkgName)

  @Input
  @Optional
  val serviceSuffix: Property<String> = objects.property(String::class.java).convention("API")

  @Input
  @Optional
  val problemBaseUri: Property<String> = objects.property(String::class.java).convention("http://example.com/")

  @Input
  @Optional
  val disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val disableContainerElementValid: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val coroutines: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val flowCoroutines: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val reactiveResponseType: Property<String> = objects.property(String::class.java).convention(null as String?)

  @Input
  @Optional
  val explicitSecurityParameters: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val baseUriMode: Property<BaseUriMode> = objects.property(BaseUriMode::class.java).convention(null as BaseUriMode?)

  @Input
  @Optional
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf())

  @Input
  @Optional
  val generatedAnnotation: Property<String> = objects.property(String::class.java).convention(null as String?)

  @Input
  @Optional
  val alwaysUseResponseReturn: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val useResultResponseReturn: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val useJakartaPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @Input
  @Optional
  val quarkus: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  @OutputDirectory
  val outputDir: Property<Directory> = objects.directoryProperty()
    .convention(project.layout.buildDirectory.dir("generated/sources/sunday/$name"))

  private val apiProcessor = APIProcessor()

  @TaskAction
  fun generate() {
    logger.debug("Source: {}", source.files.joinToString())
    logger.debug("Includes: {}", includes.orNull?.files?.joinToString() ?: "none")

    val mode = this.mode.get()
    val outputDirFile = outputDir.get().asFile

    val categories = EnumSet.noneOf(GeneratedTypeCategory::class.java)
    if (generateModel.get()) {
      categories.add(GeneratedTypeCategory.Model)
    }
    if (generateService.get()) {
      categories.add(GeneratedTypeCategory.Service)
    }

    val options = EnumSet.allOf(KotlinTypeRegistry.Option::class.java)
    if (disableJacksonAnnotations.get()) {
      options.remove(JacksonAnnotations)
    }
    if (disableModelImplementations.get()) {
      options.remove(ImplementModel)
    }
    if (disableValidationConstraints.get()) {
      options.remove(ValidationConstraints)
    }
    if (disableContainerElementValid.get()) {
      options.remove(KotlinTypeRegistry.Option.ContainerElementValid)
    }
    if (!useJakartaPackages.get()) {
      options.remove(KotlinTypeRegistry.Option.UseJakartaPackages)
    }

    val typeRegistry = KotlinTypeRegistry(modelPkgName.get(), generatedAnnotation.orNull, mode, options)

    source
      .filter { it.extension == "raml" && it.isFile }
      .forEach { file ->
        processFile(file, typeRegistry)
      }

    typeRegistry.generateFiles(categories, outputDirFile.toPath())
  }

  private fun processFile(file: File, typeRegistry: KotlinTypeRegistry) {

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
      when (framework.get()) {
        TargetFramework.JAXRS ->
          KotlinJAXRSGenerator(
            processed.document,
            processed.shapeIndex,
            typeRegistry,
            KotlinJAXRSGenerator.Options(
              flowCoroutines.get(),
              coroutines.get(),
              reactiveResponseType.orNull,
              explicitSecurityParameters.get(),
              baseUriMode.orNull,
              alwaysUseResponseReturn.get(),
              servicePkgName.get(),
              problemBaseUri.get(),
              defaultMediaTypes.get(),
              serviceSuffix.get(),
              quarkus.get(),
            ),
          )

        TargetFramework.Sunday ->
          KotlinSundayGenerator(
            processed.document,
            processed.shapeIndex,
            typeRegistry,
            KotlinSundayGenerator.Options(
              useResultResponseReturn.orNull ?: false,
              servicePkgName.get(),
              problemBaseUri.get(),
              defaultMediaTypes.get(),
              serviceSuffix.get(),
            ),
          )
      }

    try {

      generator.generateServiceTypes()
    } catch (x: GenerationException) {
      logger.error("${x.file}:${x.line}: ${x.message}")
      throw InvalidUserDataException("$file is invalid")
    }
  }
}
