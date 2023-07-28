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

class SundayGeneratorPlugin : Plugin<Project> {

  override fun apply(project: Project) {

    val allTask = project.tasks.register("sundayGenerateAll") {
      it.group = "code-generation"
    }

    val generationsContainer =
      project.container(SundayGeneration::class.java) { name -> SundayGeneration(name, project.objects, project) }
    project.extensions.add("sundayGenerations", generationsContainer)

    generationsContainer.configureEach { gen ->

      val genTask = project.tasks.register("sundayGenerate_${gen.name}", SundayGenerate::class.java) { genTask ->
        genTask.group = "code-generation"
        gen.source.takeIf { it.isPresent }?.let { genTask.source(gen.source) }
        gen.includes.takeIf { it.isPresent }?.let { genTask.includes.set(it) }
        gen.framework.takeIf { it.isPresent }?.let { genTask.framework.set(it) }
        gen.mode.takeIf { it.isPresent }?.let { genTask.mode.set(it) }
        gen.generateModel.takeIf { it.isPresent }?.let { genTask.generateModel.set(it) }
        gen.generateService.takeIf { it.isPresent }?.let { genTask.generateService.set(it) }
        gen.pkgName.takeIf { it.isPresent }?.let { genTask.pkgName.set(it) }
        gen.servicePkgName.takeIf { it.isPresent }?.let { genTask.servicePkgName.set(it) }
        gen.serviceSuffix.takeIf { it.isPresent }?.let { genTask.serviceSuffix.set(it) }
        gen.modelPkgName.takeIf { it.isPresent }?.let { genTask.modelPkgName.set(it) }
        gen.disableValidationConstraints.takeIf { it.isPresent }?.let { genTask.disableValidationConstraints.set(it) }
        gen.disableJacksonAnnotations.takeIf { it.isPresent }?.let { genTask.disableJacksonAnnotations.set(it) }
        gen.disableModelImplementations.takeIf { it.isPresent }?.let { genTask.disableModelImplementations.set(it) }
        gen.coroutines.takeIf { it.isPresent }?.let { genTask.coroutines.set(it) }
        gen.reactiveResponseType.takeIf { it.isPresent }?.let { genTask.reactiveResponseType.set(it) }
        gen.explicitSecurityParameters.takeIf { it.isPresent }?.let { genTask.explicitSecurityParameters.set(it) }
        gen.baseUriMode.takeIf { it.isPresent }?.let { genTask.baseUriMode.set(it) }
        gen.defaultMediaTypes.takeIf { it.isPresent }?.let { genTask.defaultMediaTypes.set(it) }
        gen.generatedAnnotation.takeIf { it.isPresent }?.let { genTask.generatedAnnotation.set(it) }
        gen.alwaysUseResponseReturn.takeIf { it.isPresent }?.let { genTask.alwaysUseResponseReturn.set(it) }
        gen.useResultResponseReturn.takeIf { it.isPresent }?.let { genTask.useResultResponseReturn.set(it) }
        gen.useJakartaPackages.takeIf { it.isPresent }?.let { genTask.useJakartaPackages.set(it) }
        gen.outputDir.takeIf { it.isPresent }?.let { genTask.outputDir.set(it) }
      }

      allTask.configure { it.dependsOn(genTask) }
    }

    project.afterEvaluate {

      generationsContainer.all { gen ->

        val genTask = project.tasks.named("sundayGenerate_${gen.name}", SundayGenerate::class.java)

        val sourceSetName = gen.targetSourceSet.get()

        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        sourceSets.getByName(sourceSetName).java.srcDir(genTask)
      }
    }
  }
}
