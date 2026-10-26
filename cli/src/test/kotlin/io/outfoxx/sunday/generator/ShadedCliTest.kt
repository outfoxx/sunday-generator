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

package io.outfoxx.sunday.generator

import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Runs the actual minimized CLI JAR so test dependencies cannot hide packaging failures. */
class ShadedCliTest {
  @Test
  fun `bundled CLI exports resolved OpenAPI problem URIs`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("problems.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Problems, version: '1'}
      servers: [{url: 'https://api.example.com/v1/'}]
      x-sunday-problemBaseUri: '{base}/'
      x-sunday-problemUriParams: {base: problems}
      x-sunday-problemTypes:
        missing: {status: 404, title: Missing}
      paths: {}
      components:
        schemas:
          MissingProblem:
            type: object
            required: [type, title, status]
            properties:
              type: {type: string, default: missing}
              title: {type: string, const: Missing}
              status: {type: integer, const: 404}
      """.trimIndent(),
    )
    val output = directory.resolve("problems.ir.yaml")
    val log = directory.resolve("cli.log")
    val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    val process =
      ProcessBuilder(
        java,
        "-jar",
        requireNotNull(System.getProperty("sunday.cli.shadow-jar")),
        "ir",
        "--source",
        "openapi",
        "-mode",
        "client",
        "-out",
        output.toString(),
        source.toString(),
      ).redirectErrorStream(true)
        .redirectOutput(log.toFile())
        .start()
    try {
      assertTrue(process.waitFor(60, TimeUnit.SECONDS), "CLI timed out: ${log.readText()}")
      assertEquals(0, process.exitValue(), log.readText())
    } finally {
      if (process.isAlive) process.destroyForcibly()
    }
    val api = GeneratedApiYaml.readPath(output)
    val expected = "https://api.example.com/v1/problems/missing"
    assertEquals(expected, api.problems.single().typeUri)
    val type =
      api.models
        .single()
        .properties
        .single { it.name == "type" }
    assertEquals(expected, type.defaultValue)
  }
}
