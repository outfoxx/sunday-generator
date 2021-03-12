@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.gradle

import io.outfoxx.sunday.generator.GenerationMode
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

class SundayGeneration(
  val name: String,
  objects: ObjectFactory,
  project: Project
) {

  private val sourceDef = project.fileTree("src/main/sunday") { it.include("**/*.raml") }
  private val outputDirDef = project.layout.buildDirectory.dir("generated/sources/sunday/$name")

  val source: Property<FileCollection> = objects.property(FileCollection::class.java).convention(sourceDef)
  val includes: Property<FileCollection> = objects.property(FileCollection::class.java)
  var framework: Property<TargetFramework> = objects.property(TargetFramework::class.java)
  var mode: Property<GenerationMode> = objects.property(GenerationMode::class.java)
  var generateModel: Property<Boolean> = objects.property(Boolean::class.java)
  var generateService: Property<Boolean> = objects.property(Boolean::class.java)
  var pkgName: Property<String> = objects.property(String::class.java)
  var servicePkgName: Property<String> = objects.property(String::class.java)
  var modelPkgName: Property<String> = objects.property(String::class.java)
  var disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java)
  var disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
  var disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java)
  var reactiveResponseType: Property<String> = objects.property(String::class.java)
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java)
  val outputDir: Property<Directory> = objects.directoryProperty().convention(outputDirDef)

}
