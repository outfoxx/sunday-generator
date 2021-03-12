@file:Suppress("UnstableApiUsage", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")

package io.outfoxx.sunday.generator.gradle

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.parseAndValidate
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
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
  val reactiveResponseType: Property<String> = objects.property(String::class.java)

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
    val modelPkgName =
      this.modelPkgName.orNull ?: pkgName ?: throw InvalidUserDataException("modelPkgName or pkgName required")
    val servicePkgName =
      this.servicePkgName.orNull ?: pkgName ?: throw InvalidUserDataException("servicePkgName or pkgName required")
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

    source.get().forEach { file ->

      if (file.isDirectory || file.extension == "git") {
        return
      }

      val document = parseAndValidate(file.toURI())

      val generator =
        when (framework) {
          TargetFramework.JAXRS ->
            KotlinJAXRSGenerator(
              document,
              typeRegistry,
              reactiveResponseType.orNull,
              servicePkgName,
              problemBaseUri,
              defaultMediaTypes.get()
            )

          TargetFramework.Sunday ->
            KotlinSundayGenerator(
              document,
              typeRegistry,
              servicePkgName,
              problemBaseUri,
              defaultMediaTypes.get()
            )
        }

      generator.generateServiceTypes()
    }

    typeRegistry.generateFiles(categories, outputDirFile.toPath())
  }

  private fun shouldClean(dir: File): Boolean {
    if (dir == project.projectDir || dir == project.rootDir || dir.absolutePath == "/") {
      return false
    }
    return true
  }

}
