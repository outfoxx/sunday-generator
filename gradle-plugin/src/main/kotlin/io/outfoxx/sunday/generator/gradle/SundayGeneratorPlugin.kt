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

package io.outfoxx.sunday.generator.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.io.File
import java.util.concurrent.Callable

class SundayGeneratorPlugin : Plugin<Project> {

  override fun apply(project: Project) {

    val allTask =
      project.tasks.register("sundayGenerateAll") {
        it.group = "code-generation"
      }

    val generationsContainer =
      project.objects.domainObjectContainer(SundayGeneration::class.java) { name ->
        SundayGeneration(
          name,
          project.objects,
          project,
        )
      }
    project.extensions.add("sundayGenerations", generationsContainer)

    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

    generationsContainer.configureEach { gen ->

      val includesIndex = project.layout.buildDirectory.file("generated/sunday/includes/${gen.name}.txt")
      val discoveredIncludes =
        project.files(
          Callable {
            val indexFile = includesIndex.get().asFile
            if (!indexFile.exists()) {
              emptyList<File>()
            } else {
              indexFile
                .readLines()
                .filter { it.isNotBlank() }
                .map { File(it) }
            }
          },
        )

      val discoverTask =
        project.tasks.register("sundayDiscoverIncludes_${gen.name}", SundayDiscoverIncludes::class.java) { task ->
          task.group = "code-generation"
          task.source(gen.source)
          task.includesIndexFile.set(includesIndex)
          task.bootstrapIncludes.set(discoveredIncludes)
        }

      val genTask =
        project.tasks.register("sundayGenerate_${gen.name}", SundayGenerate::class.java) { genTask ->
          genTask.group = "code-generation"
          genTask.source(gen.source)
          genTask.includes.set(discoveredIncludes)
          genTask.dependsOn(discoverTask)
          gen.framework.takeIf { it.isPresent }?.let { genTask.framework.set(it) }
          gen.mode.takeIf { it.isPresent }?.let { genTask.mode.set(it) }
          gen.generateModel.takeIf { it.isPresent }?.let { genTask.generateModel.set(it) }
          gen.generateService.takeIf { it.isPresent }?.let { genTask.generateService.set(it) }
          gen.pkgName.takeIf { it.isPresent }?.let { genTask.pkgName.set(it) }
          gen.servicePkgName.takeIf { it.isPresent }?.let { genTask.servicePkgName.set(it) }
          gen.serviceSuffix.takeIf { it.isPresent }?.let { genTask.serviceSuffix.set(it) }
          gen.modelPkgName.takeIf { it.isPresent }?.let { genTask.modelPkgName.set(it) }
          gen.disableValidationConstraints.takeIf { it.isPresent }?.let { genTask.disableValidationConstraints.set(it) }
          gen.disableContainerElementValid.takeIf { it.isPresent }?.let { genTask.disableContainerElementValid.set(it) }
          gen.disableJacksonAnnotations.takeIf { it.isPresent }?.let { genTask.disableJacksonAnnotations.set(it) }
          gen.disableModelImplementations.takeIf { it.isPresent }?.let { genTask.disableModelImplementations.set(it) }
          gen.coroutines.takeIf { it.isPresent }?.let { genTask.coroutines.set(it) }
          gen.flowCoroutines.takeIf { it.isPresent }?.let { genTask.flowCoroutines.set(it) }
          gen.reactiveResponseType.takeIf { it.isPresent }?.let { genTask.reactiveResponseType.set(it) }
          gen.explicitSecurityParameters.takeIf { it.isPresent }?.let { genTask.explicitSecurityParameters.set(it) }
          gen.baseUriMode.takeIf { it.isPresent }?.let { genTask.baseUriMode.set(it) }
          gen.defaultMediaTypes.takeIf { it.isPresent }?.let { genTask.defaultMediaTypes.set(it) }
          gen.generatedAnnotation.takeIf { it.isPresent }?.let { genTask.generatedAnnotation.set(it) }
          gen.generationTimestamp.takeIf { it.isPresent }?.let { genTask.generationTimestamp.set(it) }
          gen.alwaysUseResponseReturn.takeIf { it.isPresent }?.let { genTask.alwaysUseResponseReturn.set(it) }
          gen.useResultResponseReturn.takeIf { it.isPresent }?.let { genTask.useResultResponseReturn.set(it) }
          gen.useJakartaPackages.takeIf { it.isPresent }?.let { genTask.useJakartaPackages.set(it) }
          gen.quarkus.takeIf { it.isPresent }?.let { genTask.quarkus.set(it) }
          gen.outputDir.takeIf { it.isPresent }?.let { genTask.outputDir.set(it) }
        }

      allTask.configure { it.dependsOn(genTask) }

      val sourceSetName = gen.targetSourceSet.get()
      sourceSets.getByName(sourceSetName).java.srcDir(genTask)
    }
  }
}
