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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [RAML] Service Module Path Test")
class ServiceModulePathTest {

  @Test
  fun `directory typeScriptModule appends service file name in node-next mode`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-module.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!explicit/client/api.js", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "explicit/client/api.js")
          .writeTo(this)
      }

    assertThat(output, containsString("export class API"))
  }

  @Test
  fun `explicit ts module path uses file path directly in node-next mode`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type-module-file.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!explicit/client.js", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "explicit/client.js")
          .writeTo(this)
      }

    assertFalse(output.contains("import {API as API_} from './client';"))
    assertFalse(output.contains("import {API as API_} from './client.js';"))
    assertTrue(output.contains("Promise<API.FetchTestResponseBody>"))
    assertTrue(output.contains("SchemaLike<API.FetchTestResponseBody> = API.FetchTestResponseBodySchema"))
  }
}
