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
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Responses Test")
@Tag("responses")
class TypeScriptSundayIrResponsesTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `generates response bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseBodyContentTest/res-body-param.api.ts")
  }

  @Test
  fun `generates explicit response media from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseBodyContentTest/res-body-param-explicit-content-type.api.ts")
  }

  @Test
  fun `generates problem registration from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseProblemsTest/res-problems.api.ts")
  }

  @Test
  fun `generates no-problem registration from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseProblemsTest/res-no-problems.api.ts")
  }

  @Test
  fun `generates referenced problem types directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "ProblemService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "fetchTest",
                    method = "GET",
                    path = "/tests/{id}",
                    responses = listOf(GeneratedResponse(status = 200, type = GeneratedTypeRef.scalar("string"))),
                    problems = listOf(GeneratedTypeRef.named("InvalidIdProblem")),
                  ),
                ),
            ),
          ),
        problems =
          listOf(
            GeneratedProblem(
              name = "InvalidIdProblem",
              sourceName = "invalid_id",
              typeUri = "http://example.com/invalid_id",
              status = 400,
              title = "Invalid Id",
              detail = "The id contains one or more invalid characters.",
              fields =
                listOf(
                  GeneratedModelProperty("offendingId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val problemOutput =
      buildString {
        FileSpec
          .get(findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes), "invalid-id-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("ProblemTypesTest/invalid-id-problem.ts", problemOutput)
  }

  @Test
  fun `generates response builder methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BuilderMethodsTest/response-builder.api.ts")
  }
}
