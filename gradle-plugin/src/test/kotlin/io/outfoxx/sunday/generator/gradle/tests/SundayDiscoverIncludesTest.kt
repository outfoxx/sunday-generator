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

import io.outfoxx.sunday.generator.gradle.SundayDiscoverIncludes
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SundayDiscoverIncludesTest {

  @Test
  fun `tracks encoded discriminator targets and their nested references`(
    @TempDir directory: File,
  ) {
    val root = directory.resolve("api.yaml")
    root.writeText(
      """
      openapi: 3.1.0
      info: {title: Discriminator references, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Base:
            discriminator:
              propertyName: kind
              mapping:
                variant: './types/variant%20type.yaml'
      """.trimIndent(),
    )
    val types = directory.resolve("types").also { it.mkdirs() }
    val variant = types.resolve("variant type.yaml")
    variant.writeText("${'$'}ref: '../nested.yaml'\n")
    val nested = directory.resolve("nested.yaml")
    nested.writeText("type: string\n")
    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    val task = project.tasks.register("discover", SundayDiscoverIncludes::class.java).get()
    task.source(root)
    val rootsIndex = directory.resolve("roots.txt")
    val allSourcesIndex = directory.resolve("all-sources.txt")
    task.rootsIndexFile.set(rootsIndex)
    task.allSourcesIndexFile.set(allSourcesIndex)
    task.discover()

    assertEquals(listOf(root.canonicalPath), rootsIndex.readLines())
    assertEquals(setOf(root, variant, nested).map { it.canonicalPath }.toSet(), allSourcesIndex.readLines().toSet())
  }
}
