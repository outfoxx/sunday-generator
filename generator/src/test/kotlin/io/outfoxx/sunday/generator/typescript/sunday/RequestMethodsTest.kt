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
@DisplayName("[TypeScript/Sunday] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
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

    assertSnapshot("RequestMethodsTest/req-methods.default.api.ts", output)
  }

  @Test
  fun `test promise request method generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            false,
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods.promises.api.ts", output)
  }

  @Test
  fun `test request method generation with result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            false,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods.result-response.api.ts", output)
  }

  @Test
  fun `test request method generation with promise result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods.promise-result-response.api.ts", output)
  }

  @Test
  fun `test request method generation with nullify`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
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

    assertSnapshot("RequestMethodsTest/req-methods-nullify.default.api.ts", output)
  }

  @Test
  fun `test request method generation with nullify and promise results`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            false,
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods-nullify.promises.api.ts", output)
  }

  @Test
  fun `test request method generation with nullify and result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            false,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods-nullify.result-response.api.ts", output)
  }

  @Test
  fun `test request method generation with nullify and promise result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RequestMethodsTest/req-methods-nullify.promise-result-response.api.ts", output)
  }
}
