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

import io.outfoxx.sunday.generator.RequestTolerance
import io.outfoxx.sunday.generator.gradle.SundayGenerate
import io.outfoxx.sunday.generator.gradle.SundayGenerations
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SundayEnvironmentOptionsTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `native validation metadata survives either Kotlin plugin application order`(kotlinFirst: Boolean) {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("java")
    if (kotlinFirst) project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.outfoxx.sunday-generator")
    if (!kotlinFirst) project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    val compiler = project.tasks.named("compileKotlin", KotlinCompile::class.java).get()
    assertEquals(
      1,
      compiler.compilerOptions.freeCompilerArgs
        .get()
        .count { it == "-Xemit-jvm-type-annotations" },
    )
  }

  @Test
  fun `task and DSL share strict request defaults and environment options`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("java")
    project.pluginManager.apply("io.outfoxx.sunday-generator")
    @Suppress("UNCHECKED_CAST")
    val generations = project.extensions.getByName("sundayGenerations") as SundayGenerations
    val generation = generations.create("client")
    assertEquals(RequestTolerance.Strict, generation.requestTolerance.get())
    assertFalse(generation.profile.isPresent)
    val task = project.tasks.named("sundayGenerate_client", SundayGenerate::class.java).get()
    assertEquals(RequestTolerance.Strict, task.requestTolerance.get())
    assertFalse(task.profile.isPresent)
    generation.profile.set("external")
    generation.requestTolerance.set(RequestTolerance.Tolerant)
    assertEquals("external", task.profile.get())
    assertEquals(RequestTolerance.Tolerant, task.requestTolerance.get())
  }
}
