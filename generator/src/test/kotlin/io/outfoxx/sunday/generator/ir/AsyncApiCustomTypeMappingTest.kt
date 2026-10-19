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

package io.outfoxx.sunday.generator.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AsyncApiCustomTypeMappingTest {
  @Test
  fun `explicit empty operation messages do not inherit channel messages`(
    @TempDir directory: Path,
  ) {
    val fixture = Path.of("src/test/resources/asyncapi/ir/custom-types")
    directory.resolve("api.yaml").writeText(
      fixture.resolve("api-3.yaml").readText().replace("    action: send", "    action: send\n    messages: []"),
    )
    directory.resolve("identifier.yaml").writeText(fixture.resolve("identifier.yaml").readText())
    val api = AsyncApiToGeneratedApi().convertFragment(directory.resolve("api.yaml").toUri()).api
    assertTrue(api.services.flatMap { it.operations }.all { it.requestBody == null })
  }

  @Test
  fun `direct alias overrides do not leak mappings into composed children`(
    @TempDir directory: Path,
  ) {
    val fixture = Path.of("src/test/resources/asyncapi/ir/custom-types")
    val source =
      fixture
        .resolve("api-3.yaml")
        .readText()
        .replace(
          "    Alias:\n      ${'$'}ref: '#/components/schemas/Identifier'",
          "    Alias:\n      ${'$'}ref: '#/components/schemas/Identifier'\n" +
            "      x-sunday-kotlinType: io.example.OtherIdentifier",
        )
    val start = source.indexOf("    LocalIdentifier:")
    val end = source.indexOf("    Identifiers:", start)
    directory.resolve("api.yaml").writeText(
      source.substring(0, start) +
        "    LocalIdentifier:\n      allOf: [{${'$'}ref: '#/components/schemas/Identifier'}]\n" + source.substring(end),
    )
    directory.resolve("identifier.yaml").writeText(
      "type: object\nproperties: {value: {type: string}}\n" +
        fixture
          .resolve("identifier.yaml")
          .readText()
          .lines()
          .filter { it.startsWith("x-sunday-") }
          .joinToString("\n"),
    )
    val models =
      AsyncApiToGeneratedApi()
        .convertFragment(
          directory.resolve("api.yaml").toUri(),
        ).api.models
        .associateBy {
          it.name
        }
    assertEquals("io.example.OtherIdentifier", models.getValue("Alias").targets["kotlin"]?.typeName)
    assertEquals("DomainIdentifier", models.getValue("Alias").targets["swift"]?.typeName)
    assertTrue(models.getValue("LocalIdentifier").targets.isEmpty())
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "type: string",
      "type: string\nenum: [one, two]",
      "type: object\nproperties: {value: {type: string}}",
      "type: array\nitems: {type: string}",
      "type: object\nadditionalProperties: {type: string}",
      "oneOf: [{type: string}, {type: integer}]",
    ],
  )
  fun `all named schema kinds retain language mappings through external refs and aliases`(
    shape: String,
    @TempDir directory: Path,
  ) {
    val fixture = Path.of("src/test/resources/asyncapi/ir/custom-types")
    for (version in listOf("2", "3")) {
      directory.resolve("api.yaml").writeText(fixture.resolve("api-$version.yaml").readText())
      directory.resolve("identifier.yaml").writeText(
        shape + "\n" +
          fixture
            .resolve("identifier.yaml")
            .readText()
            .lines()
            .filter { it.startsWith("x-sunday-") }
            .joinToString("\n"),
      )
      val api = AsyncApiToGeneratedApi().convertFragment(directory.resolve("api.yaml").toUri()).api
      for (name in listOf("Identifier", "Alias", "LocalIdentifier")) {
        val model = api.models.single { it.name == name }
        assertEquals("io.example.Identifier", model.targets["kotlin"]?.typeName)
        assertEquals("DomainIdentifier", model.targets["swift"]?.typeName)
        assertEquals(
          GeneratedTarget(typeName = "DomainIdentifier", moduleName = "./domain.js"),
          model.targets["typescript"],
        )
        assertEquals("test_api.domain.Identifier", model.targets["python"]?.typeName)
      }
      assertTrue(
        api.models
          .single { it.name == "Record" }
          .targets
          .isEmpty(),
      )
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "kotlinType", "kotlinType:client", "kotlinType:server", "swiftType",
      "typeScriptType", "pythonType", "typeScriptModule",
    ],
  )
  fun `rejects invalid mapping metadata`(
    extension: String,
    @TempDir directory: Path,
  ) {
    val fixture = Path.of("src/test/resources/asyncapi/ir/custom-types")
    directory.resolve("api.yaml").writeText(fixture.resolve("api-3.yaml").readText())
    directory
      .resolve(
        "identifier.yaml",
      ).writeText("type: string\nx-sunday-typeScriptType: Custom\nx-sunday-$extension: ''\n")
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        AsyncApiToGeneratedApi().convertFragment(directory.resolve("api.yaml").toUri())
      }
    assertTrue(error.message.orEmpty().contains("x-sunday-$extension"), error.message)
  }
}
