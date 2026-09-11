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

package io.outfoxx.sunday.generator.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.writeText

class OpenApiAliasAnnotationsTest {
  @Test
  fun `annotated aliases leave original and direct target uses unchanged`(
    @TempDir directory: Path,
  ) {
    val converter = OpenApiToGeneratedApi()
    for (explicitFalse in listOf(false, true)) {
      val original = api(directory.resolve("original-$explicitFalse.yaml"), explicitFalse, emptyList())
      val baseline =
        converter
          .convert(original.toUri())
          .models
          .single {
            it.name == "Container"
          }.properties
          .associateBy { it.name }
      val aliases =
        listOf(
          """
          Alias:
            ${'$'}ref: 'https://schemas.example.test/container#node'
            description: Alias description
            example: {value: alias}
            deprecated: true
            readOnly: true
            writeOnly: true
          Chain: {${'$'}ref: '#/components/schemas/Alias'}
          """.trimIndent(),
          """
          Other:
            ${'$'}ref: '#/components/schemas/Container/properties/node'
            description: Other description
            example: {value: other}
            deprecated: false
            readOnly: false
            writeOnly: false
          """.trimIndent(),
        )
      val paths =
        listOf(false, true).map { reverse ->
          api(
            directory.resolve("aliases-$explicitFalse-$reverse.yaml"),
            explicitFalse,
            if (reverse) aliases.reversed() else aliases,
          )
        }
      Executors.newFixedThreadPool(4).use { executor ->
        val results =
          executor.invokeAll(
            (paths + paths).map { source ->
              Callable { converter.convert(source.toUri()) }
            },
          )
        for (future in results) {
          val result = future.get()
          val properties =
            result.models
              .single { it.name == "Container" }
              .properties
              .associateBy { it.name }
          for (name in listOf("node", "copy", "pointer")) {
            assertEquals(baseline.getValue(name), properties.getValue(name))
          }
          val uses =
            result.models
              .single { it.name == "Uses" }
              .properties
              .associateBy { it.name }
          for (name in listOf("alias", "chain")) {
            val use = uses.getValue(name)
            assertEquals("Alias description", use.documentation?.description)
            assertEquals(mapOf("value" to "alias"), use.examples.single().value)
            assertTrue(use.deprecated)
            assertTrue(use.readOnly)
            assertTrue(use.writeOnly)
          }
          assertEquals("Other description", uses.getValue("other").documentation?.description)
          assertFalse(uses.getValue("other").readOnly)
          assertFalse(uses.getValue("other").writeOnly)
          assertFalse(uses.getValue("other").deprecated)
          val target = properties.getValue("node").type
          assertEquals("Node2", target.name)
          assertEquals(
            target,
            result.models
              .single { it.name == target.name }
              .properties
              .single { it.name == "child" }
              .type,
          )
        }
      }
    }
  }

  private fun api(
    source: Path,
    explicitFalse: Boolean,
    aliases: List<String>,
  ): Path =
    source.apply {
      val original =
        """
        Node: {type: string}
        Container:
          ${'$'}id: https://schemas.example.test/container
          type: object
          properties:
            node:
              ${'$'}anchor: node
              type: object
              description: Original description
              example: {value: original}
              FLAGS
              properties:
                value: {type: string}
                child: {${'$'}ref: '#node'}
            copy: {${'$'}ref: '#node'}
            pointer: {${'$'}ref: '#/properties/node'}
        """.trimIndent().replace(
          "FLAGS",
          if (explicitFalse) "readOnly: false\n      writeOnly: false\n      deprecated: false" else "",
        )
      val uses =
        if (aliases.isEmpty()) {
          ""
        } else {
          """
          Uses:
            type: object
            properties:
              alias: {${'$'}ref: '#/components/schemas/Alias'}
              chain: {${'$'}ref: '#/components/schemas/Chain'}
              other: {${'$'}ref: '#/components/schemas/Other'}
          """.trimIndent()
        }
      writeText(
        "openapi: 3.1.0\ninfo: {title: Annotations, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          (listOf(original) + aliases + uses).joinToString("\n").prependIndent("    "),
      )
    }
}
