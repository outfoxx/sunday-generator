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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateSunday
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [RAML] Response Body Content Test")
class ResponseBodyContentTest {

  @Test
  fun `test basic body parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("ResponseBodyContentTest/res-body-param.api.ts", output)
  }

  @Test
  fun `test generation of body parameter with explicit content type`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("ResponseBodyContentTest/res-body-param-explicit-content-type.api.ts", output)
  }

  @Test
  fun `test generation of body parameter with inline type in node-next mode`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)

    val builtTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    val typeSpec = findTypeMod("API@!api.js", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "api.js")
          .writeTo(this)
      }

    assertFalse(output.contains("import {API as API_} from './api';"))
    assertFalse(output.contains("import {API as API_} from './api.js';"))
    assertTrue(output.contains("Operation<void, API.FetchTestResponseBody, Factory>"))
    assertTrue(output.contains("SchemaLike<API.FetchTestResponseBody> = API.FetchTestResponseBodySchema"))
    assertSnapshot("ResponseBodyContentTest/res-body-param-inline-type.node-next.api.ts", output)
  }
}
