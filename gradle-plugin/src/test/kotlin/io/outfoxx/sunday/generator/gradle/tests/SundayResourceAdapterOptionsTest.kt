/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.gradle.tests

import io.outfoxx.sunday.generator.gradle.SundayGenerate
import io.outfoxx.sunday.generator.gradle.SundayGeneration
import io.outfoxx.sunday.generator.gradle.SundayGeneratorPlugin
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SundayResourceAdapterOptionsTest {
  @Test
  fun `resource adapters default to disabled`() {
    val project = ProjectBuilder.builder().build()
    val task = project.tasks.register("adapters", SundayGenerate::class.java).get()
    assertFalse(task.resourceAdapters.get())
    assertFalse(task.enforceSecuritySchemes.get())
  }

  @Test
  fun `Gradle generation DSL wires the resource adapter option into its task`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("java")
    project.pluginManager.apply(SundayGeneratorPlugin::class.java)
    @Suppress("UNCHECKED_CAST")
    val generations = project.extensions.getByName("sundayGenerations") as NamedDomainObjectContainer<SundayGeneration>
    generations.create("server") {
      it.resourceAdapters.set(true)
      it.enforceSecuritySchemes.set(true)
    }
    val task = project.tasks.named("sundayGenerate_server", SundayGenerate::class.java).get()
    assertTrue(task.resourceAdapters.get())
    assertTrue(task.enforceSecuritySchemes.get())
  }
}
