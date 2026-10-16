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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("requests")
class PythonBinaryResponseTest : PythonTest() {

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed", "openapi-alias", "openapi-concrete"])
  fun `binary downloads preserve bytes across frontends`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val sources =
      mapOf(
        "openapi" to
          """
          openapi: 3.1.0
          info: {title: Downloads, version: '1.0'}
          paths:
            /download:
              get:
                operationId: download
                responses:
                  '200':
                    description: Asset
                    content:
                      application/octet-stream:
                        schema: {type: string, format: binary}
                      '*/*':
                        schema: {type: string, format: binary}
          """.trimIndent(),
        "raml" to
          """
          #%RAML 1.0
          title: Downloads
          /download:
            get:
              displayName: download
              responses:
                200:
                  body:
                    application/octet-stream:
                      type: file
                    '*/*':
                      type: file
          """.trimIndent(),
        "asyncapi" to
          """
          asyncapi: 3.0.0
          info: {title: Downloads, version: '1.0'}
          servers:
            api: {host: example.test, protocol: https}
          channels:
            asset:
              address: /event
              messages:
                asset:
                  contentType: '*/*'
                  payload: {type: string, format: binary}
              bindings:
                http: {type: response, method: GET}
          operations:
            downloadEvent:
              action: receive
              channel:
                ${'$'}ref: '#/channels/asset'
              messages:
                - ${'$'}ref: '#/channels/asset/messages/asset'
          """.trimIndent(),
      )
    val kinds = if (frontend == "composed") listOf("openapi", "asyncapi") else listOf(frontend.substringBefore("-"))
    val uris =
      kinds.map { kind ->
        val file = directory.resolve("$kind." + if (kind == "raml") "raml" else "yaml")
        var source = sources.getValue(kind)
        if (frontend == "openapi-alias") {
          source = source.replace("{type: string, format: binary}", "{${'$'}ref: '#/components/schemas/Asset'}") +
            "\ncomponents:\n  schemas:\n    Asset: {type: string, format: binary}\n"
        }
        if (frontend == "openapi-concrete") {
          source = source.replace("application/octet-stream:", "image/png:").replace("'*/*':", "application/pdf:")
        }
        Files.writeString(file, source)
        file.toUri()
      }
    val api = GeneratedApiIrExporter().export(uris)
    val service =
      api.services.first().copy(
        name = "Downloads",
        group = null,
        operations =
          api.services.flatMap {
            it.operations
          },
      )
    val modules =
      PythonSundayIrGenerator(api.copy(services = listOf(service)), PythonGeneratorOptions(packageName = "binary_api"))
        .generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    val methods =
      service.operations
        .filter {
          it.streaming == null
        }.joinToString(", ") { "\"${it.id.pythonIdentifierName}\"" }
    val streams =
      service.operations
        .filter {
          it.streaming != null
        }.joinToString(", ") { "\"${it.id.pythonIdentifierName}\"" }
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import httpx
          from binary_api.downloads import DownloadsClient
          from sunday.httpx import HttpxTransport
          async def main():
              cases = [
                  ("application/octet-stream", bytes([0, 255])),
                  ("image/png", bytes([137, 80, 78, 71])),
                  ("application/pdf", b"%PDF"),
                  ("audio/mpeg", bytes([0, 255])),
                  ("video/mp4", bytes([0, 255])),
                  ("application/vnd.example.asset", bytes([0, 255])),
                  ("application/json", b'{ "asset": true }\n'),
                  ("text/plain; charset=iso-8859-1", bytes([99, 97, 102, 233])),
              ]
              for media, payload in cases:
                  if "$frontend" == "openapi-concrete" and media not in ("image/png", "application/pdf"):
                      continue
                  for body in (payload, b""):
                      async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(
                          lambda request: httpx.Response(200, content=body, headers={"Content-Type": media})
                      )) as native:
                          async with HttpxTransport(native) as transport:
                              client = DownloadsClient(transport)
                              for name in [$streams]:
                                  stream = getattr(client, name)()
                                  assert hasattr(stream, "__aiter__")
                                  await stream.aclose()
                              for name in [$methods]:
                                  operation = getattr(client, name)()
                                  assert await operation.execute() == body, (name, media)
                                  assert (await operation.response()).result == body, (name, media)
          asyncio.run(main())
          """.trimIndent(),
      ),
    )
  }
}
