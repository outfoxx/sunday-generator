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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayOptions
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI

/** Shared compiler-backed fixtures for the IR feature tests. */
abstract class SwiftSundayIrTestSupport {
  protected fun assertIrServiceSnapshot(
    compiler: SwiftCompiler,
    testUri: URI,
    snapshotPath: String,
    options: SwiftSundayOptions = swiftSundayTestOptions,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("API", builtTypes)

    assertTrue(compileTypes(compiler, builtTypes))
    assertSwiftSnapshot(
      snapshotPath,
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  protected fun generateSwiftSundayFiles(
    compiler: SwiftCompiler,
    api: GeneratedApi,
    options: SwiftSundayOptions = swiftSundayTestOptions,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())

    SwiftSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    typeRegistry.generateFiles(
      setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model),
      compiler.srcDir,
    )
  }

  protected fun avatarUploadApi(): GeneratedApi =
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
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "contentType",
                        location = GeneratedParameter.Location.HEADER,
                        type = GeneratedTypeRef.named("AvatarContentType"),
                        required = true,
                        serializationName = "Content-Type",
                      ),
                    ),
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.scalar("file"),
                      mediaTypes = listOf("application/octet-stream"),
                    ),
                ),
                GeneratedOperation(
                  id = "getUserAvatar",
                  method = "GET",
                  path = "/users/{userId}/avatar",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("image/png", "image/jpeg", "image/webp"),
                      ),
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
}
