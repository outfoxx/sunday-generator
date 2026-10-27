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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Path
import kotlin.io.path.writeText

/** Parses problem declarations with and without a default detail through each supported source path. */
fun problemDetailApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val definitions =
    """
    with_default:
      status: 400
      title: With default
      detail: Default detail
    without_default:
      status: 400
      title: Without default
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "problems.raml" else "problems.yaml")
  source.writeText(
    (
      if (frontend == "raml") {
        """
        #%RAML 1.0
        title: Problem detail
        uses:
          sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
        (sunday.problemBaseUri): https://example.com/problems/
        (sunday.problemTypes):
        """.trimIndent()
      } else {
        """
        openapi: 3.1.0
        info: {title: Problem detail, version: 1.0.0}
        paths: {}
        x-sunday-problemBaseUri: https://example.com/problems/
        x-sunday-problemTypes:
        """.trimIndent()
      }
    ) + "\n" + definitions.prependIndent("  "),
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText("asyncapi: 3.0.0\ninfo: {title: Problem detail, version: 1.0.0}\nchannels: {}\noperations: {}\n")
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources).also { api ->
    api.problems.forEach { problem ->
      assertFalse(requireNotNull(problem.payload).fields.single { it.name == "detail" }.required)
    }
  }
}

/** Parses a problem whose nullable inherited detail is narrowed to an optional non-nullable string. */
fun narrowedProblemDetailApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val source = directory.resolve("problems.yaml")
  val header =
    if (frontend == "asyncapi") "asyncapi: 3.0.0\nchannels: {}\noperations: {}" else "openapi: 3.1.0\npaths: {}"
  source.writeText(
    header + "\n" +
      """
      info: {title: Problem encoding, version: '1.0'}
      components:
        schemas:
          HttpProblem:
            type: object
            additionalProperties: true
            properties:
              type: {type: [string, 'null'], format: uri, default: 'about:blank'}
              title: {type: [string, 'null']}
              status: {type: [integer, 'null']}
              detail: {type: [string, 'null']}
              instance: {type: [string, 'null'], format: uri}
          AccountFieldsInvalidProblem:
            allOf:
              - ${'$'}ref: '#/components/schemas/HttpProblem'
              - type: object
                required: [type, title, status]
                properties:
                  type: {type: string, format: uri, const: 'https://example.com/problems/account-fields-invalid'}
                  title: {type: string, const: Bad Request}
                  status: {type: integer, const: 400}
                  detail: {type: string}
      """.trimIndent(),
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      "asyncapi: 3.0.0\ninfo: {title: Problem encoding, version: '1.0'}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
