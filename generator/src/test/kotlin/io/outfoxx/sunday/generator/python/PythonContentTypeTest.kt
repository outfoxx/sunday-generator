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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedPayloadOption
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonContentTypeTest : PythonTest() {

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `generated binary routes preserve headers and bytes across frontends`(
    frontend: String,
    compiler: PythonCompiler,
  ) {
    val paths =
      when (frontend) {
        "raml" -> listOf("raml/ir/content-type.raml")
        "composed" -> listOf("openapi/ir/content-type.yaml", "asyncapi/ir/content-type.yaml")
        else -> listOf("$frontend/ir/content-type.yaml")
      }
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(paths.map { javaClass.getResource("/$it")!!.toURI() })
    val service =
      api.services.first().copy(
        name = "Uploads",
        group = null,
        operations = api.services.flatMap { it.operations },
      )
    val modules =
      PythonLitestarIrGenerator(
        api.copy(services = listOf(service)),
        PythonGeneratorOptions(packageName = "uploads_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("uploads_api.uploads_server"),
        smokeCode =
          """
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from uploads_api.uploads_server import create_uploads_router
          class Delegate:
              received = None
              def __getattr__(self, name):
                  async def call(*args):
                      self.received = (name, args)
                  return call
          delegate = Delegate()
          body = bytes([0, 255, 10, 13, 123, 125])
          with TestClient(Litestar(route_handlers=[create_uploads_router(delegate)], plugins=[SundayPlugin()])) as client:
              if "$frontend" != "asyncapi":
                  for path in ("raw", "image", "fixed", "enum", "constant", "multiple", "tolerant"):
                      for media in ("image/png", "image/jpeg", "image/webp", "text/plain", "application/octet-stream", "IMAGE/PNG", "image/png; profile=example"):
                          compatible = path == "raw" or (path == "fixed" and media == "application/octet-stream") or (path not in ("raw", "fixed", "multiple") and media.lower().startswith("image/")) or (path == "multiple" and media in ("image/png", "image/jpeg", "IMAGE/PNG", "image/png; profile=example"))
                          valid = path not in ("enum", "constant") or media in (("image/png",) if path == "constant" else ("image/png", "image/jpeg"))
                          delegate.received = None
                          response = client.put("/uploads/" + path, content=body, headers={"Content-Type": media})
                          expected = 400 if not valid else 204 if compatible else 415
                          assert response.status_code == expected, (path, media, response.status_code, response.text)
                          if expected == 204:
                              assert delegate.received[1][0] == body, delegate.received
                              assert str(delegate.received[1][1]) == media, delegate.received
                          else:
                              assert delegate.received is None, delegate.received
                  assert client.put("/uploads/raw", content=body).status_code == 400
                  for path, expected in (("optional", None), ("default", "application/octet-stream")):
                      response = client.put("/uploads/" + path, content=body)
                      assert response.status_code == 204, response.text
                      assert delegate.received[1] == (body, expected), delegate.received
                  assert client.get("/uploads/header", headers={"Content-Type": "image/png"}).status_code == 204
                  assert delegate.received == ("get_header", ("image/png",)), delegate.received
                  assert client.put("/uploads/raw", content=b'"YWJj"', headers={"Content-Type": "application/json"}).status_code == 204
                  assert delegate.received[1][0] == b'"YWJj"'
              if "$frontend" in ("asyncapi", "composed"):
                  for media in ("image/png", "image/jpeg"):
                      response = client.post("/uploads/events", content=body, headers={"Content-Type": media})
                      assert response.status_code < 300, response.text
                      assert delegate.received[1][0] == body and str(delegate.received[1][1]) == media
                  from uploads_api.models import EventMediaType, PngEnvelope
                  from pydantic import ValidationError
                  assert EventMediaType("image/jpeg").value == "image/jpeg"
                  assert PngEnvelope.model_validate({"contentType": "image/png"}).content_type == EventMediaType("image/png")
                  try:
                      PngEnvelope.model_validate({"contentType": "image/jpeg"})
                      raise AssertionError("inherited enum narrowing was lost")
                  except ValidationError:
                      pass
          """.trimIndent(),
      ),
    )
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "uploads_api/uploads_server.py")
    assertTrue(source.contains("HeaderParameter(name=\"Content-Type\")"), source)
    assertTrue(source.contains("await _read_body(request,"), source)
  }

  @Test
  fun `binary aliases use raw bodies while JSON base64 remains decoded`(compiler: PythonCompiler) {
    val uri = javaClass.getResource("/openapi/ir/content-type.yaml")!!.toURI()
    val api = GeneratedApiIrExporter().export(uri)
    val alias =
      GeneratedModel("BinaryAlias", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(GeneratedTypeRef.scalar("file")))
    val envelope =
      GeneratedModel(
        "BinaryEnvelope",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("payload", GeneratedTypeRef.scalar("string", format = "byte"), required = true),
          ),
      )
    val operations =
      listOf(
        GeneratedOperation(
          "putAlias",
          "PUT",
          "/alias",
          requestBody = GeneratedPayload(GeneratedTypeRef.named("BinaryAlias"), listOf("*/*")),
          responses = listOf(GeneratedResponse(status = 204)),
        ),
        GeneratedOperation(
          "putEncoded",
          "PUT",
          "/encoded",
          requestBody =
            GeneratedPayload(
              GeneratedTypeRef.scalar("string", format = "byte"),
              listOf("application/json"),
            ),
          responses = listOf(GeneratedResponse(status = 204)),
        ),
        GeneratedOperation(
          "putStructured",
          "PUT",
          "/structured",
          requestBody = GeneratedPayload(GeneratedTypeRef.named("BinaryEnvelope"), listOf("application/json")),
          responses = listOf(GeneratedResponse(status = 204)),
        ),
      )
    val modules =
      PythonLitestarIrGenerator(
        api.copy(
          models = listOf(alias, envelope),
          services = listOf(api.services.first().copy(name = "Uploads", operations = operations)),
        ),
        PythonGeneratorOptions(packageName = "uploads_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("uploads_api.uploads_server"),
        smokeCode =
          """
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from uploads_api.uploads_server import create_uploads_router
          class Delegate:
              async def put_alias(self, body):
                  assert body == b'"YWJj"', body
              async def put_encoded(self, body):
                  assert body == b'abc', body
              async def put_structured(self, body):
                  assert body.payload == b'abc', body
          with TestClient(Litestar(route_handlers=[create_uploads_router(Delegate())], plugins=[SundayPlugin()])) as client:
              for path in ("alias", "encoded"):
                  response = client.put("/" + path, content=b'"YWJj"', headers={"Content-Type": "application/json"})
                  assert response.status_code == 204, response.text
              response = client.put("/structured", json={"payload": "YWJj"})
              assert response.status_code == 204, response.text
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["payloads", "union", "named", "alias"])
  fun `mixed binary and structured representations fail with an operation diagnostic`(kind: String) {
    val binary = GeneratedTypeRef.scalar("file")
    val structured = GeneratedTypeRef.scalar("string")
    val union = GeneratedTypeRef(GeneratedTypeRef.Kind.UNION, "union", arguments = listOf(binary, structured))
    val uri = javaClass.getResource("/openapi/ir/content-type.yaml")!!.toURI()
    val api = GeneratedApiIrExporter().export(uri)
    val models =
      listOf(
        GeneratedModel("Mixed", GeneratedModel.Kind.UNION, aliases = listOf(binary, structured)),
        GeneratedModel("Alias", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(GeneratedTypeRef.named("Mixed"))),
      )
    val body =
      when (kind) {
        "union" -> GeneratedPayload(union)
        "named" -> GeneratedPayload(GeneratedTypeRef.named("Mixed"))
        "alias" -> GeneratedPayload(GeneratedTypeRef.named("Alias"))
        else ->
          GeneratedPayload(
            binary,
            payloads =
              listOf(
                GeneratedPayloadOption(binary, listOf("image/png")),
                GeneratedPayloadOption(structured, listOf("application/json")),
              ),
          )
      }
    val operation =
      GeneratedOperation(
        "putMixed",
        "PUT",
        "/mixed",
        requestBody = body,
      )
    val error =
      assertThrows(GenerationException::class.java) {
        PythonLitestarRenderer("uploads_api", api.copy(models = models))
          .renderService(GeneratedService("Uploads", operations = listOf(operation)))
      }
    assertTrue(error.message!!.contains("putMixed"), error.message)
    assertTrue(error.message!!.contains("mixes binary and structured"), error.message)
  }
}
