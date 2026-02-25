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

import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [RAML] Request Uri Params Test")
class RequestUriParamsTest {

  @Test
  fun `test basic uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestUriParamsTest/req-uri-params.api.ts", output)
  }

  @Test
  fun `test inherited uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestUriParamsTest/req-uri-params-inherited.api.ts", output)
  }

  @Test
  fun `test optional uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestUriParamsTest/req-uri-params-optional.api.ts", output)
  }

  @Test
  fun `test generation of multiple uri parameters with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestUriParamsTest/req-uri-params-inline-types.api.ts", output)
  }
}
