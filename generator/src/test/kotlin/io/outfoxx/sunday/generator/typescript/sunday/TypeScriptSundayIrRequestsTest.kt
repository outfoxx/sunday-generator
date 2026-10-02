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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Requests Test")
@Tag("requests")
class TypeScriptSundayIrRequestsTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `generates request methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMethodsTest/req-methods.default.api.ts")
  }

  @Test
  fun `generates path parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestUriParamsTest/req-uri-params.api.ts")
  }

  @Test
  fun `generates optional query parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestQueryParamsTest/req-query-params-optional.api.ts")
  }

  @Test
  fun `generates constant headers from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestHeaderParamsTest/req-header-params-constant.api.ts")
  }

  @Test
  fun `generates same-name mixed inline parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMixedParamsTest/req-mixed-params-inline-types-same-name.api.ts")
  }

  @Test
  fun `generates request bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestBodyParamTest/req-body-param.api.ts")
  }

  @Test
  @Tag("responses")
  fun `generates inline set request and response bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-set-inline.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestBodyParamTest/req-body-param-set-inline.api.ts")
  }

  @Test
  fun `generates base URL companion from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BaseUriTest/base-uri.api.ts")
  }

  @Test
  fun `generates request builder methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BuilderMethodsTest/request-builder.api.ts")
  }

  @Test
  fun `uses content type header parameter as request media selection in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Avatar API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "putUserAvatar",
                    method = "PUT",
                    path = "/users/{userId}/avatar",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "userId",
                          GeneratedParameter.Location.PATH,
                          GeneratedTypeRef.scalar("string"),
                          required = true,
                        ),
                        GeneratedParameter(
                          "contentType",
                          GeneratedParameter.Location.HEADER,
                          GeneratedTypeRef.named("AvatarContentType"),
                          required = true,
                          serializationName = "Content-Type",
                        ),
                      ),
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("image/png", "image/jpeg", "image/webp"),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "AvatarContentType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("image/png", "image/jpeg", "image/webp"),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get(findTypeMod("UsersAPI@!users-api", builtTypes), "users-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("contentTypes: [MediaType.from(contentType)]"), serviceSource)
    assertFalse(serviceSource.contains("'Content-Type': contentType"), serviceSource)
  }
}
