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
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.optionalRequestBodyApi
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
class PythonOptionalRequestBodyTest : PythonTest() {
  @Test
  fun `optional request bodies omit the HTTP entity`(compiler: PythonCompiler) {
    val modules =
      PythonSundayIrGenerator(
        optionalRequestBodyApi("openapi"),
        PythonGeneratorOptions(placement = pythonPlacement("optional_api")),
      ).generateModules(
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
          from optional_api.optional import OptionalClient
          from optional_api.models import Input
          from sunday.httpx import HttpxTransport
          async def main():
              bodies = []
              def respond(request):
                  bodies.append(request.content)
                  return httpx.Response(204)
              async with httpx.AsyncClient(base_url="https://example.test", transport=httpx.MockTransport(respond)) as native:
                  async with HttpxTransport(native) as transport:
                      client = OptionalClient(transport)
                      await client.optional_post().execute()
                      await client.default_post().execute()
                      assert bodies == [b"", b""]
                      await client.optional_post(Input(target="ok")).execute()
                      assert b"ok" in bodies[-1]
          asyncio.run(main())
          """.trimIndent(),
      ),
    )
  }
}
