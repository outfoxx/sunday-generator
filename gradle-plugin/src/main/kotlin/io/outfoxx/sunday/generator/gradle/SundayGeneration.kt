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
  var serviceSuffix: Property<String> = objects.property(String::class.java)
  var modelPkgName: Property<String> = objects.property(String::class.java)
  var disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java)
  var disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
  var disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java)
  var reactiveResponseType: Property<String> = objects.property(String::class.java)
  val explicitSecurityParameters: Property<Boolean> = objects.property(Boolean::class.java)
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java)
  val outputDir: Property<Directory> = objects.directoryProperty().convention(outputDirDef)
}
