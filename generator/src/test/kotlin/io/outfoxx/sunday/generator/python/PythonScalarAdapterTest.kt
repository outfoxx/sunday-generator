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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("requests")
class PythonScalarAdapterTest : PythonTest() {

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `download headers retain scalar validation in either response order`(
    reverse: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val success =
      """
      '200':
        description: Download
        headers:
          Content-Type:
            schema: {type: string}
          Content-Length:
            schema: {type: integer}
          Cache-Control:
            schema: {type: string}
          ETag:
            schema: {type: string}
          Last-Modified:
            schema: {type: string}
        content:
          application/octet-stream:
            schema: {type: string, format: binary}
      """.trimIndent()
    val redirect =
      """
      '302':
        description: Redirect
        headers:
          Location:
            schema: {type: string, format: uri}
      """.trimIndent()
    val responses = (if (reverse) listOf(redirect, success) else listOf(success, redirect)).joinToString("\n")
    val spec = directory.resolve("api.yaml")
    Files.writeString(
      spec,
      """
      openapi: 3.1.0
      info: {title: Header collision, version: '1.0'}
      paths:
        /download:
          get:
            operationId: download
            x-sunday-service: Downloads
            responses:
      """.trimIndent() + "\n" + responses.prependIndent("        ") + "\n",
    )
    val api = GeneratedApiIrExporter().export(spec.toUri())
    val modules =
      PythonSundayIrGenerator(api, PythonGeneratorOptions(placement = pythonPlacement("header_fixture")))
        .generateModules(
          GeneratedOutputSelection(
            GeneratedTypeCategory.entries.toSet(),
          ),
        )
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import httpx
          from sunday.httpx import HttpxTransport
          from header_fixture.downloads import DownloadsClient
          async def main():
              body = bytes([0, 255, 128]) + b"asset"
              headers = {
                  "Content-Type": "application/octet-stream",
                  "Content-Length": str(len(body)),
                  "Cache-Control": "public, max-age=31536000, immutable",
                  "ETag": '"asset-etag"',
                  "Last-Modified": "Thu, 15 Oct 2026 17:00:00 GMT",
              }
              async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(
                  lambda request: httpx.Response(200, content=body, headers=headers)
              )) as client:
                  async with HttpxTransport(client) as transport:
                      operation = DownloadsClient(transport).download()
                      assert await operation.execute() == body
                      response = await operation.response()
                      assert response.result == body
                      for name, value in headers.items():
                          expected = len(body) if name == "Content-Length" else value
                          assert response.decoded_header(name) == expected, (name, response.decoded_header(name))
                      assert type(response.decoded_header("Content-Length")) is int
          asyncio.run(main())
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `adapter allocation separates validation types and reuses equivalent schemas across scopes`(
    compiler: PythonCompiler,
  ) {
    val string = GeneratedTypeRef.scalar("string")
    val uri = GeneratedTypeRef.scalar("string", format = "uri")
    val types =
      listOf(
        string,
        uri,
        GeneratedTypeRef.scalar("string", format = "date"),
        GeneratedTypeRef.scalar("string", format = "uuid"),
        GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(string)),
        GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(uri)),
        GeneratedTypeRef(
          GeneratedTypeRef.Kind.ARRAY,
          "array",
          arguments = listOf(string),
          collection = GeneratedCollectionKind.SET,
        ),
        GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(string)),
        GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(uri)),
        GeneratedTypeRef(
          GeneratedTypeRef.Kind.UNION,
          "union",
          arguments = listOf(uri, GeneratedTypeRef.scalar("integer")),
        ),
        GeneratedTypeRef(
          GeneratedTypeRef.Kind.UNION,
          "union",
          arguments = listOf(string, GeneratedTypeRef.scalar("integer")),
        ),
        GeneratedTypeRef.scalar("string", nullable = true, format = "uri-reference"),
        GeneratedTypeRef.scalar("string", format = "url"),
      )
    val operations =
      types.mapIndexed { index, type ->
        GeneratedOperation(
          "value$index",
          "GET",
          "/$index",
          parameters = listOf(GeneratedParameter("query", GeneratedParameter.Location.QUERY, type)),
          responses = listOf(GeneratedResponse(200, type, listOf("application/json"))),
        )
      } +
        GeneratedOperation(
          "location",
          "GET",
          "/location",
          responses =
            listOf(
              GeneratedResponse(
                200,
                headers = listOf(GeneratedParameter("Location", GeneratedParameter.Location.HEADER, uri)),
              ),
            ),
        )
    val bodyOperations =
      types.mapIndexed { index, type ->
        GeneratedOperation(
          "body$index",
          "POST",
          "/$index",
          requestBody = GeneratedPayload(type, listOf("application/json")),
          responses = listOf(GeneratedResponse(204)),
        )
      }
    val renderer = PythonClientRenderer("scalar_api")
    val modules =
      listOf(
        PythonModuleBuilder("scalar_api/__init__.py").build(),
        renderer.renderService(GeneratedService("Scalars", operations = operations + bodyOperations)),
        renderer.renderService(GeneratedService("Other", operations = (operations + bodyOperations).reversed())),
      )
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import datetime
          import uuid
          import httpx
          from pydantic import AnyUrl, TypeAdapter
          from sunday.errors import ResponseDecodingError, UnexpectedResponse
          from sunday.httpx import HttpxTransport
          from scalar_api import scalars, other
          async def main():
              for module, client_type in ((scalars, scalars.ScalarsClient), (other, other.OtherClient)):
                  adapters = [value for value in vars(module).values() if isinstance(value, TypeAdapter)]
                  assert len(adapters) == 11, len(adapters)
                  values = ["plain", "https://example.test/", "2026-10-15", "12345678-1234-5678-1234-567812345678",
                            ["plain"], ["https://example.test/"], ["plain"], {"key": "plain"},
                            {"key": "https://example.test/"}, "https://example.test/", "plain", "plain", "https://example.test/"]
                  expected_types = [str, AnyUrl, datetime.date, uuid.UUID, list, list, set, dict, dict, AnyUrl, str, str, AnyUrl]
                  annotations = [str, AnyUrl, datetime.date, uuid.UUID, list[str], list[AnyUrl], set[str],
                                 dict[str, str], dict[str, AnyUrl], AnyUrl | int, str | int, str, AnyUrl]
                  inputs = [TypeAdapter(annotation).validate_python(value) for annotation, value in zip(annotations, values)]
                  current = None
                  location = "https://example.test/"
                  def handle(request):
                      if request.url.path == "/location":
                          return httpx.Response(200, headers={"Location": location})
                      return httpx.Response(200, json=current)
                  async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(handle)) as client:
                      async with HttpxTransport(client) as transport:
                          generated = client_type(transport)
                          for index, (value, expected_type) in enumerate(zip(values, expected_types)):
                              current = value
                              operation = getattr(generated, "value" + str(index))(query=inputs[index])
                              body_operation = getattr(generated, "body" + str(index))(inputs[index])
                              adapter = body_operation.spec.request.body_adapter
                              assert adapter is not None
                              assert isinstance(adapter.validate_python(value), expected_type)
                              result = await operation.execute()
                              assert isinstance(result, expected_type), (index, result)
                              if index == 5:
                                  assert isinstance(result[0], AnyUrl)
                              if index == 8:
                                  assert isinstance(result["key"], AnyUrl)
                          response = await generated.location().response()
                          assert isinstance(response.decoded_header("Location"), AnyUrl)
                          location = "invalid"
                          for execute in (generated.location().execute, generated.location().response):
                              try:
                                  await execute()
                              except UnexpectedResponse:
                                  pass
                              else:
                                  raise AssertionError("invalid URI header accepted")
                          for index in (1, 2, 3, 5, 8, 9):
                              current = "invalid"
                              try:
                                  await getattr(generated, "value" + str(index))().execute()
                              except ResponseDecodingError:
                                  pass
                              else:
                                  raise AssertionError((index, "invalid formatted response accepted"))
          asyncio.run(main())
          """.trimIndent(),
      ),
    )
  }
}
