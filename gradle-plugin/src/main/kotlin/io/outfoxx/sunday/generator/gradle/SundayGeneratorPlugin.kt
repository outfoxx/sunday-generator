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
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

class SundayGeneratorPlugin : Plugin<Project> {

  override fun apply(project: Project) {

    val allTask = project.task("sundayGenerateAll")

    val generationsContainer =
      project.container(SundayGeneration::class.java) { name -> SundayGeneration(name, project.objects, project) }
    project.extensions.add("sundayGenerations", generationsContainer)

    generationsContainer.all { gen ->

      val genTask = project.tasks.register("sundayGenerate_${gen.name}", SundayGenerate::class.java) { genTask ->
        genTask.source.set(gen.source)
        genTask.includes.set(gen.includes)
        genTask.framework.set(gen.framework)
        genTask.mode.set(gen.mode)
        genTask.generateModel.set(gen.generateModel)
        genTask.generateService.set(gen.generateService)
        genTask.pkgName.set(gen.pkgName)
        genTask.servicePkgName.set(gen.servicePkgName)
        genTask.modelPkgName.set(gen.modelPkgName)
        genTask.disableValidationConstraints.set(gen.disableValidationConstraints)
        genTask.disableJacksonAnnotations.set(gen.disableJacksonAnnotations)
        genTask.disableModelImplementations.set(gen.disableModelImplementations)
        genTask.reactiveResponseType.set(gen.reactiveResponseType)
        genTask.defaultMediaTypes.set(gen.defaultMediaTypes)
        genTask.outputDir.set(gen.outputDir)
      }

      project.convention.findPlugin(JavaPluginConvention::class.java)
        ?.sourceSets?.findByName(MAIN_SOURCE_SET_NAME)?.java?.srcDir(genTask.get().outputDir)

      allTask.dependsOn(genTask)
    }
  }
}
