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

package io.outfoxx.sunday.generator.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.platform.commons.PreconditionViolationException
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TagFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request
import org.junit.platform.launcher.core.LauncherFactory
import java.nio.file.Files
import java.nio.file.Path

/** Verifies actual JUnit discovery, including meta-annotations, inheritance and method-level tags. */
class TestPartitionTest {

  @Test
  fun `tag expressions partition every discovered method exactly once`() {
    assertEquals("false", System.getProperty("junit.jupiter.execution.parallel.enabled"))
    val all = discover(null)
    val expressions =
      jacksonObjectMapper()
        .readTree(Path.of("../scripts/ci/partitions.json").toFile())
        .elements()
        .asSequence()
        .map { it.asText() }
        .toList()
    val partitions = expressions.associateWith(::discover)
    assertTrue(all.isNotEmpty())
    partitions.forEach { (expression, methods) -> assertTrue(methods.isNotEmpty(), expression) }
    val flattened = partitions.values.flatMap { it.keys }
    assertEquals(all.keys, flattened.toSet())
    assertEquals(flattened.size, flattened.toSet().size, "CI partitions overlap")
    all.forEach { (id, tags) ->
      if (id.contains("io.outfoxx.sunday.generator.swift.")) assertTrue("swift" in tags, id)
      if (id.contains("io.outfoxx.sunday.generator.python.")) assertTrue("python" in tags, id)
    }
    assertTrue(all.values.any { it.isEmpty() }, "Infrastructure tests remain selectable without language tags")
    val output = Path.of("build/diagnostics/test-partitions.json")
    Files.createDirectories(output.parent)
    jacksonObjectMapper().writeValue(output.toFile(), partitions.mapValues { it.value.keys.sorted() })
  }

  @Test
  fun `feature and language expressions combine rather than broadening selection`() {
    val swift = discover("swift")
    val validation = discover("validation")
    val combined = discover("swift & validation")
    assertEquals(swift.keys.intersect(validation.keys), combined.keys)
    assertEquals(swift.keys + validation.keys, discover("swift | validation").keys)
    assertTrue(combined.values.any { "models" in it }, "Multiple feature tags must survive discovery")
    assertFalse(discover("python & validation").isEmpty(), "Python inherits its language tag from the base class")
    assertThrows(PreconditionViolationException::class.java) { TagFilter.includeTags("swift & (") }
  }

  @Test
  fun `instrumentation exclusions cannot match production classes`() {
    val patterns =
      requireNotNull(System.getProperty("sunday.validation.instrumentation-exclusions"))
        .split(',')
        .map { Regex(Regex.escape(it).replace("*", "\\E.*\\Q")) }
    val roots = listOf(Path.of("build/classes/kotlin/main"), Path.of("build/classes/java/main"))
    val classes =
      roots.filter(Files::exists).flatMap { root ->
        Files.walk(root).use { paths ->
          paths
            .filter { it.toString().endsWith(".class") }
            .map {
              root
                .relativize(it)
                .toString()
                .removeSuffix(".class")
                .replace('/', '.')
            }.toList()
        }
      }
    assertTrue(classes.isNotEmpty())
    assertEquals(emptyList<String>(), classes.filter { name -> patterns.any { it.matches(name) } })
  }

  private fun discover(expression: String?): Map<String, Set<String>> {
    val builder =
      request().selectors(
        selectClasspathRoots(setOf(Path.of("build/classes/kotlin/test").toAbsolutePath())),
      )
    if (expression != null) builder.filters(TagFilter.includeTags(expression))
    val plan = LauncherFactory.create().discover(builder.build())
    return plan.roots
      .flatMap(plan::getDescendants)
      .filter { it.source.orElse(null) is MethodSource }
      .associate { it.uniqueId to it.tags.map { tag -> tag.name }.toSet() }
  }
}
