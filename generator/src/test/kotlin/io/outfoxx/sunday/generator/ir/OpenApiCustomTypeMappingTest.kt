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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiCustomTypeMappingTest {
  @Test
  fun `external schema retains every language mapping independently`() {
    val api =
      OpenApiToGeneratedApi().convert(
        Path.of("src/test/resources/openapi/ir/custom-types/mapped-api.yaml").toUri(),
      )
    val identifier = api.models.single { it.name == "Identifier" }
    assertEquals(
      mapOf(
        "kotlin" to GeneratedTarget(typeName = "io.example.Identifier"),
        "swift" to GeneratedTarget(typeName = "DomainIdentifier"),
        "typescript" to GeneratedTarget(typeName = "DomainIdentifier", moduleName = "./domain.js"),
        "python" to GeneratedTarget(typeName = "test_api.domain.Identifier"),
      ),
      identifier.targets,
    )
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, identifier.kind)
    assertEquals("^id:[a-z]+$", identifier.validation["pattern"])
  }

  @Test
  fun `custom types retain wire schemas and independent target modes`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Types, version: '1'}
      paths: {}
      components:
        schemas:
          Identifier:
            type: string
            pattern: '^id:'
            minLength: 4
            x-sunday-kotlinType: io.example.Identifier
            x-sunday-kotlinType:client: io.example.ClientIdentifier
            x-sunday-kotlinType:server: io.example.ServerIdentifier
          Plain: {type: string}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val identifier = api.models.single { it.name == "Identifier" }
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, identifier.kind)
    assertEquals(listOf(GeneratedTypeRef.scalar("string")), identifier.aliases)
    assertEquals(mapOf("pattern" to "^id:", "minLength" to "4"), identifier.validation)
    assertEquals(
      mapOf(
        "kotlin" to GeneratedTarget(typeName = "io.example.Identifier"),
        "kotlinClient" to GeneratedTarget(typeName = "io.example.ClientIdentifier"),
        "kotlinServer" to GeneratedTarget(typeName = "io.example.ServerIdentifier"),
      ),
      identifier.targets,
    )
    assertTrue(
      api.models
        .single { it.name == "Plain" }
        .targets
        .isEmpty(),
    )
  }

  @Test
  fun `invalid custom type metadata fails with model context`(
    @TempDir directory: Path,
  ) {
    for (value in listOf("null", "''", "true", "[]", "{}")) {
      val source = directory.resolve("invalid.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Types, version: '1'}
        paths: {}
        components:
          schemas:
            Identifier:
              type: string
              x-sunday-kotlinType: $value
        """.trimIndent(),
      )
      val error = assertThrows(IllegalArgumentException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
      assertTrue(error.message.orEmpty().contains("Identifier"))
      assertTrue(error.message.orEmpty().contains("x-sunday-kotlinType"))
    }
  }
}
