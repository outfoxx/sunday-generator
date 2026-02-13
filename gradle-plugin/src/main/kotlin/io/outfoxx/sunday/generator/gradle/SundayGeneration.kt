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
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator.Options.BaseUriMode
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

class SundayGeneration(
  val name: String,
  objects: ObjectFactory,
  project: Project,
) {

  val source: Property<FileCollection> =
    objects.property(FileCollection::class.java)
      .convention(project.fileTree("src/main/sunday") { it.include("**/*.raml") })

  val includes: Property<FileCollection> = objects.property(FileCollection::class.java)
  val framework: Property<TargetFramework> = objects.property(TargetFramework::class.java)
  val mode: Property<GenerationMode> = objects.property(GenerationMode::class.java)
  val generateModel: Property<Boolean> = objects.property(Boolean::class.java)
  val generateService: Property<Boolean> = objects.property(Boolean::class.java)
  val pkgName: Property<String> = objects.property(String::class.java)
  val servicePkgName: Property<String> = objects.property(String::class.java)
  val serviceSuffix: Property<String> = objects.property(String::class.java)
  val modelPkgName: Property<String> = objects.property(String::class.java)
  val disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java)
  val disableContainerElementValid: Property<Boolean> = objects.property(Boolean::class.java)
  val disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
  val disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java)
  val coroutines: Property<Boolean> = objects.property(Boolean::class.java)
  val flowCoroutines: Property<Boolean> = objects.property(Boolean::class.java)
  val reactiveResponseType: Property<String> = objects.property(String::class.java)
  val explicitSecurityParameters: Property<Boolean> = objects.property(Boolean::class.java)
  val baseUriMode: Property<BaseUriMode> = objects.property(BaseUriMode::class.java)
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java)
  val generatedAnnotation: Property<String> = objects.property(String::class.java)
  val alwaysUseResponseReturn: Property<Boolean> = objects.property(Boolean::class.java)
  val useResultResponseReturn: Property<Boolean> = objects.property(Boolean::class.java)
  val useJakartaPackages: Property<Boolean> = objects.property(Boolean::class.java)
  val quarkus: Property<Boolean> = objects.property(Boolean::class.java)
  val outputDir: Property<Directory> = objects.directoryProperty()

  val targetSourceSet: Property<String> =
    objects.property(String::class.java)
      .convention(MAIN_SOURCE_SET_NAME)
}
