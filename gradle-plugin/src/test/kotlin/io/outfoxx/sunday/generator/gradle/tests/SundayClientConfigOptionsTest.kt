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
import io.outfoxx.sunday.generator.gradle.SundayGenerations
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SundayClientConfigOptionsTest {
  @Test
  fun `task and DSL default to client configuration generation and allow disabling it`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("java")
    project.pluginManager.apply("io.outfoxx.sunday-generator")
    @Suppress("UNCHECKED_CAST")
    val generations = project.extensions.getByName("sundayGenerations") as SundayGenerations
    val generation = generations.create("client")
    assertTrue(generation.generateClientConfig.get())
    val task = project.tasks.named("sundayGenerate_client", SundayGenerate::class.java).get()
    assertTrue(task.generateClientConfig.get())
    generation.generateClientConfig.set(false)
    assertFalse(task.generateClientConfig.get())
  }
}
