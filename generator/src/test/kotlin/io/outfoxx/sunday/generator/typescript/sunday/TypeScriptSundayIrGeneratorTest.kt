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
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateSunday
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Generator Test")
class TypeScriptSundayIrGeneratorTest {

  @Test
  fun `TypeScript Sunday CLI uses the IR exporter directly`() {
    val source =
      Files.readString(
        Path.of(
          "..",
          "cli",
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "typescript",
          "TypeScriptSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("TypeScriptSundayIrGenerator"))
    assertFalse(source.contains("TypeScriptSundayGenerator("), source)
  }

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
  fun `generates inline set request and response bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-set-inline.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestBodyParamTest/req-body-param-set-inline.api.ts")
  }

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
  fun `generates inline response body models from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/res-body-param-inline-type.node-next.api.ts",
      TypeScriptTypeRegistry.ImportStyle.NodeNext,
    )
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
  fun `generates HTTP problem models as Sunday Problem errors`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "HTTP Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri")),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer")),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string", format = "uri")),
                ),
            ),
            GeneratedModel(
              name = "BadRequestProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty(
                    "validation",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.MAP,
                      name = "map",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val httpProblemOutput =
      buildString {
        FileSpec
          .get(findTypeMod("HttpProblem@!http-problem", builtTypes), "http-problem")
          .writeTo(this)
      }
    val badRequestOutput =
      buildString {
        FileSpec
          .get(findTypeMod("BadRequestProblem@!bad-request-problem", builtTypes), "bad-request-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      httpProblemOutput.contains("Problem") &&
        httpProblemOutput.contains("from '@outfoxx/sunday'"),
      httpProblemOutput,
    )
    assertTrue(
      httpProblemOutput.contains("export class HttpProblem extends Problem implements HttpProblemSpec"),
      httpProblemOutput,
    )
    assertTrue(httpProblemOutput.contains("type: init.type ?? Problem.BLANK_URL"), httpProblemOutput)
    assertTrue(httpProblemOutput.contains("detail: string;"), httpProblemOutput)
    assertFalse(httpProblemOutput.contains("type: URL | null | undefined;"), httpProblemOutput)
    assertTrue(
      badRequestOutput.contains("export class BadRequestProblem extends HttpProblem implements BadRequestProblemSpec"),
      badRequestOutput,
    )
  }

  @Test
  fun `generates nullify methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMethodsTest/req-methods-nullify.default.api.ts")
  }

  @Test
  fun `generates streaming operations for streaming request bodies`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertTrue(compileTypes(compiler, builtTypes))

    val serviceSource =
      builtTypes
        .map { (typeName, typeSpec) ->
          buildString {
            val imported = typeName.base as SymbolSpec.Imported
            FileSpec
              .get(typeSpec, imported.source.removePrefix("!"))
              .writeTo(this)
          }
        }.single { source -> "importArchive(" in source }

    assertTrue(serviceSource.contains("importArchive(body: StreamingBody)"), serviceSource)
    assertTrue(serviceSource.contains("StreamingOperation<ImportAccepted, Factory>"), serviceSource)
    assertTrue(serviceSource.contains("NullableOperation<StreamingBody, ImportAccepted, Factory>"), serviceSource)
    assertTrue(serviceSource.contains("createStreamingOperation(this.transport"), serviceSource)
    assertFalse(serviceSource.contains("importArchiveBodyType"), serviceSource)
  }

  @Test
  fun `generates event stream methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-events.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseEventsTest/res-events.api.ts")
  }

  @Test
  fun `generates event source methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseEventsTest/res-event-source.api.ts")
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
  fun `generates response builder methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BuilderMethodsTest/response-builder.api.ts")
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Model API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userTypeName = TypeName.namedImport("User", "!user")
    assertTrue(builtTypes.containsKey(userTypeName), "Available types: ${builtTypes.keys.joinToString()}")

    val source =
      buildString {
        FileSpec
          .get(findTypeMod("User@!user", builtTypes), "user")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("export type User = SchemaOutput<typeof UserSchema>;"), source)
    assertTrue(source.contains("'displayName': z.string().nullish()"), source)
    assertTrue(source.contains("export const UserSchema"), source)
    assertFalse(source.contains("export interface UserSpec"), source)
    assertFalse(source.contains("export class User"), source)
  }

  @Test
  fun `applies IR validation constraints to TypeScript Sunday schemas`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Validation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchUsers",
                    method = "GET",
                    path = "/users",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "q",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string"),
                          required = true,
                          validation = mapOf("minLength" to "2", "maxLength" to "80"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "CreateUserRequest",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    validation =
                      mapOf(
                        "minLength" to "2",
                        "maxLength" to "50",
                        "pattern" to "^[A-Za-z].*$",
                      ),
                  ),
                  GeneratedModelProperty(
                    "luckyNumber",
                    GeneratedTypeRef.scalar("integer"),
                    validation =
                      mapOf(
                        "minimum" to "1",
                        "maximum" to "100",
                      ),
                  ),
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string", format = "uuid")),
                    ),
                    validation =
                      mapOf(
                        "minItems" to "1",
                        "maxItems" to "5",
                      ),
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource =
      buildString {
        FileSpec
          .get(findTypeMod("CreateUserRequest@!create-user-request", builtTypes), "create-user-request")
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get(findTypeMod("UsersAPI@!users-api", builtTypes), "users-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(requestSource.contains("'email': z.string().email()"), requestSource)
    assertTrue(requestSource.contains("'displayName': z.string().min(2).max(50).regex(/^[A-Za-z].*$/)"), requestSource)
    assertTrue(requestSource.contains("'luckyNumber': z.number().gte(1).lte(100).nullish()"), requestSource)
    assertTrue(requestSource.contains("'tags': z.array(z.string().uuid()).min(1).max(5).nullish()"), requestSource)
    assertTrue(serviceSource.contains("const searchUsersQParameterType = z.string().min(2).max(80);"), serviceSource)
    assertTrue(serviceSource.contains("q: searchUsersQParameterType.parse(q)"), serviceSource)
  }

  @Test
  fun `emits OpenAPI scalar alias reference fields as scalar schemas`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/scalar-alias-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val updateSource =
      buildString {
        FileSpec
          .get(findTypeMod("NarrativeScopeUpdate@!narrative-scope-update", builtTypes), "narrative-scope-update")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(updateSource.contains("'location': z.string().regex(/^[A-Z2-7]{26}$/).nullish()"), updateSource)
    assertFalse(updateSource.contains("z.record(z.string(), z.unknown()).regex"), updateSource)
  }

  @Test
  fun `treats OpenAPI empty schemas as unknown in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val holderSource =
      buildString {
        FileSpec
          .get(findTypeMod("AnyHolder@!any-holder", builtTypes), "any-holder")
          .writeTo(this)
      }
    val serviceSource =
      builtTypes
        .map { (typeName, moduleSpec) ->
          val imported = typeName.base as SymbolSpec.Imported
          val modulePath = imported.source.removePrefix("!")
          buildString {
            FileSpec
              .get(moduleSpec, modulePath)
              .writeTo(this)
          }
        }.single { source -> source.contains("updateValue") }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(holderSource.contains("'value': z.unknown().nullish()"), holderSource)
    assertTrue(holderSource.contains("'documented': z.unknown().nullish()"), holderSource)
    assertTrue(holderSource.contains("'named': z.unknown().nullish()"), holderSource)
    assertTrue(serviceSource.contains("body: unknown"), serviceSource)
    assertTrue(serviceSource.contains("Operation<unknown, unknown, Factory>"), serviceSource)
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

  @Test
  fun `lowers shared enums and alias-like models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Alias API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active", "inactive"),
            ),
            GeneratedModel(
              name = "TextAlias",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextList",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextSet",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
              collection = GeneratedCollectionKind.SET,
            ),
            GeneratedModel(
              name = "TextMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextUnion",
              kind = GeneratedModel.Kind.UNION,
              aliases = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.scalar("number")),
            ),
            GeneratedModel(
              name = "AliasContainer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("status", GeneratedTypeRef.named("Status"), required = true),
                  GeneratedModelProperty("alias", GeneratedTypeRef.named("TextAlias"), required = true),
                  GeneratedModelProperty("list", GeneratedTypeRef.named("TextList"), required = true),
                  GeneratedModelProperty("set", GeneratedTypeRef.named("TextSet"), required = true),
                  GeneratedModelProperty("map", GeneratedTypeRef.named("TextMap"), required = true),
                  GeneratedModelProperty("union", GeneratedTypeRef.named("TextUnion"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val statusSource =
      buildString {
        FileSpec
          .get(findTypeMod("Status@!status", builtTypes), "status")
          .writeTo(this)
      }
    val containerSource =
      buildString {
        FileSpec
          .get(findTypeMod("AliasContainer@!alias-container", builtTypes), "alias-container")
          .writeTo(this)
      }
    val textUnionSource =
      buildString {
        FileSpec
          .get(findTypeMod("TextUnion@!text-union", builtTypes), "text-union")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(statusSource.contains("export enum Status"), statusSource)
    assertTrue(statusSource.contains("export const StatusSchema"), statusSource)
    assertTrue(
      containerSource.contains("export type AliasContainer = SchemaOutput<typeof AliasContainerSchema>;"),
      containerSource,
    )
    assertTrue(containerSource.contains("'status': runtime.resolveSchema(StatusSchema)"), containerSource)
    assertTrue(containerSource.contains("'alias': z.string()"), containerSource)
    assertTrue(containerSource.contains("'list': z.array(z.string())"), containerSource)
    assertTrue(containerSource.contains("'set': z.array(z.string())"), containerSource)
    assertTrue(
      containerSource.contains("'map': runtime.resolveSchema(z.record(z.string(), z.string()))"),
      containerSource,
    )
    assertTrue(
      containerSource.contains("'union': z.lazy(() => runtime.resolveSchema(TextUnionSchema))"),
      containerSource,
    )
    assertTrue(
      textUnionSource.contains("export type TextUnion = SchemaOutput<typeof TextUnionSchema>;"),
      textUnionSource,
    )
    assertTrue(textUnionSource.contains("export const TextUnionSchema"), textUnionSource)
  }

  @Test
  fun `generates named discriminated union models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Union API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "InvalidValueCode",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("invalid-value"),
            ),
            GeneratedModel(
              name = "MissingValueCode",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("missing-value"),
            ),
            GeneratedModel(
              name = "InvalidValueProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.named("InvalidValueCode"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "MissingValueProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.named("MissingValueCode"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "GraphValidationProblem",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("InvalidValueProblem"),
                  GeneratedTypeRef.named("MissingValueProblem"),
                ),
              discriminator = "code",
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get(findTypeMod("GraphValidationProblem@!graph-validation-problem", builtTypes), "graph-validation-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      unionSource.contains("export type GraphValidationProblem = SchemaOutput<typeof GraphValidationProblemSchema>;"),
      unionSource,
    )
    assertTrue(unionSource.contains("z.union(["), unionSource)
    assertTrue(unionSource.contains("runtime.resolveSchema(InvalidValueProblemSchema)"), unionSource)
    assertTrue(unionSource.contains("runtime.resolveSchema(MissingValueProblemSchema)"), unionSource)
  }

  @Test
  fun `generates inherited discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }
    val child1Output =
      buildString {
        FileSpec
          .get(findTypeMod("Child1@!child1", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlDiscriminatedTypesTest/simple.parent.ts", parentOutput)
    assertSnapshot("RamlDiscriminatedTypesTest/simple.sunday-ir.child1.ts", child1Output)
  }

  @Test
  fun `generates enum discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlDiscriminatedTypesTest/enum.parent.ts", parentOutput)
  }

  @Test
  fun `generates externally discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }
    val testOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Test@!test", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.parent.ts", parentOutput)
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator-sunday-ir.test.ts", testOutput)
  }

  @Test
  fun `public TypeScript Sunday generator owns inherited discriminated model generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    assertTrue(generatedTypes.containsKey(TypeName.standard("Parent@!parent")))
    assertTrue(generatedTypes.containsKey(TypeName.standard("Child1@!child1")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("ParentSchema@!parent-schema")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("Child1Schema@!child1-schema")))
  }

  @Test
  fun `generates nested shared models directly from IR`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val groupOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Group@!group", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(groupOutput.contains("export type Group = SchemaOutput<typeof GroupSchema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Group"), groupOutput)
    assertTrue(groupOutput.contains("export type Member1 = SchemaOutput<typeof Member1Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export type Member2 = SchemaOutput<typeof Member2Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Member1"), groupOutput)
    assertTrue(groupOutput.contains("export type Sub = SchemaOutput<typeof SubSchema>;"), groupOutput)
    assertFalse(groupOutput.contains("export class Group"), groupOutput)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: TypeScriptCompiler) {
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Imported API",
        source = mainSource,
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = mainSource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "main-models")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "library-models")),
              properties =
                listOf(
                  GeneratedModelProperty("libraryValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Consumer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("main", GeneratedTypeRef.named("Test", source = mainSource), required = true),
                  GeneratedModelProperty(
                    "library",
                    GeneratedTypeRef.named("Test", source = librarySource),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get(findTypeMod("Consumer@!consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!main-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!library-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(source.contains("import {TestSchema} from './main-models/test';"), source)
    assertTrue(
      source.contains("import {TestSchema as TestSchema_} from './library-models/test';"),
      source,
    )
    assertTrue(source.contains("'main': z.lazy(() => runtime.resolveSchema(TestSchema))"), source)
    assertTrue(source.contains("'library': z.lazy(() => runtime.resolveSchema(TestSchema_))"), source)
  }

  @Test
  fun `emits explicit types for recursive schemas`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Recursive API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EntityType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("character", "location"),
            ),
            GeneratedModel(
              name = "EntitySummary",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityType"), required = true),
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "CharacterSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "character",
            ),
            GeneratedModel(
              name = "LocationSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "location",
              properties =
                listOf(
                  GeneratedModelProperty("parent", GeneratedTypeRef.named("LocationSummary"), required = false),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("list", "value"),
            ),
            GeneratedModel(
              name = "EntityStateProperty",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityStatePropertyType"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyList",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityStateProperty")),
              discriminatorValue = "list",
              properties =
                listOf(
                  GeneratedModelProperty(
                    "items",
                    GeneratedTypeRef(
                      GeneratedTypeRef.Kind.ARRAY,
                      "array",
                      arguments = listOf(GeneratedTypeRef.named("EntityStateProperty")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyValue",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityStateProperty")),
              discriminatorValue = "value",
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val locationSource =
      buildString {
        FileSpec
          .get(findTypeMod("LocationSummary@!location-summary", builtTypes), "location-summary")
          .writeTo(this)
      }
    val unionSource =
      buildString {
        FileSpec
          .get(findTypeMod("EntityStateProperty@!entity-state-property", builtTypes), "entity-state-property")
          .writeTo(this)
      }
    val entitySummarySource =
      buildString {
        FileSpec
          .get(findTypeMod("EntitySummary@!entity-summary", builtTypes), "entity-summary")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(locationSource.contains("export interface LocationSummary"), locationSource)
    assertTrue(
      locationSource.contains("export const LocationSummarySchema: SchemaLike<LocationSummary>"),
      locationSource,
    )
    assertTrue(
      locationSource.contains("'parent': z.lazy(() => runtime.resolveSchema(LocationSummarySchema)).nullish()"),
      locationSource,
    )
    assertTrue(
      unionSource.contains("export type EntityStateProperty = EntityStatePropertyList | EntityStatePropertyValue;"),
      unionSource,
    )
    assertTrue(
      unionSource.contains("export const EntityStatePropertySchema: SchemaLike<EntityStateProperty>"),
      unionSource,
    )
    assertTrue(
      entitySummarySource.contains("export type EntitySummary = CharacterSummary | LocationSummary;"),
      entitySummarySource,
    )
    assertTrue(
      entitySummarySource.contains("export const EntitySummarySchema: SchemaLike<EntitySummary>"),
      entitySummarySource,
    )
  }

  @Test
  fun `generates aggregate service from split IR services`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Aggregate API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              group = "Users",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getCurrentUser",
                    method = "GET",
                    path = "/users/me",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
            GeneratedService(
              name = "ProjectsService",
              group = "Projects",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/projects/{projectId}",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, aggregateServiceOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateOutput =
      buildString {
        FileSpec
          .get(findTypeMod("TurnPostAPI@!turn-post-api", builtTypes), "turn-post-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      aggregateOutput.contains("export interface TurnPostAPI<Factory extends SundayTransport>"),
      aggregateOutput,
    )
    assertTrue(aggregateOutput.contains("users: UsersAPI<Factory>;"), aggregateOutput)
    assertTrue(aggregateOutput.contains("projects: ProjectsAPI<Factory>;"), aggregateOutput)
    assertTrue(aggregateOutput.contains("options?.defaultContentTypes"), aggregateOutput)
    assertTrue(aggregateOutput.contains("createUsersAPI(transport"), aggregateOutput)
    assertTrue(aggregateOutput.contains("defaultAcceptTypes: this.defaultAcceptTypes"), aggregateOutput)
  }

  @Test
  fun `generates formatted date-time and typed external event data from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Events API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "EventsService",
              group = "Events",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "streamEvents",
                    method = "GET",
                    path = "/events",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("EventEnvelope"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "EventIdentity",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserIdentity"),
                  GeneratedTypeRef.named("ServiceIdentity"),
                ),
              discriminator = "kind",
            ),
            GeneratedModel(
              name = "UserIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ServiceIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("serviceId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              discriminatorMappings =
                mapOf(
                  "accounts.team.created" to GeneratedTypeRef.named("AccountsTeamCreatedData"),
                  "notification.created" to GeneratedTypeRef.named("NotificationCreatedData"),
                ),
            ),
            GeneratedModel(
              name = "AccountsTeamCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "accounts.team.created",
              properties =
                listOf(
                  GeneratedModelProperty("teamId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "NotificationCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "notification.created",
              properties =
                listOf(
                  GeneratedModelProperty("notificationId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "occurredAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty("producer", GeneratedTypeRef.named("EventIdentity"), required = true),
                  GeneratedModelProperty("actor", GeneratedTypeRef.named("EventIdentity"), required = false),
                  GeneratedModelProperty("description", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val envelopeOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventEnvelope@!event-envelope", builtTypes), "event-envelope")
          .writeTo(this)
      }
    val dataOutput =
      buildString {
        FileSpec
          .get(findTypeMod("AccountsTeamCreatedData@!accounts-team-created-data", builtTypes))
          .writeTo(this)
      }
    val identityOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventIdentity@!event-identity", builtTypes), "event-identity")
          .writeTo(this)
      }
    val serviceOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventsAPI@!events-api", builtTypes), "events-api")
          .writeTo(this)
      }
    val typeCheckedTypes =
      builtTypes +
        (
          TypeName.namedImport("EventEnvelopeTypeCheck", "!event-envelope-type-check") to
            ModuleSpec
              .builder("EventEnvelopeTypeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |function readEventDataId(envelope: %T): string {
                  |  if (envelope.type === 'accounts.team.created') {
                  |    const data: { teamId: string } = envelope.data;
                  |    // @ts-expect-error notification payload shape must not match in this branch
                  |    const wrongData: { notificationId: string } = envelope.data;
                  |    return data.teamId;
                  |  }
                  |
                  |  const data: { notificationId: string } = envelope.data;
                  |  // @ts-expect-error team payload shape must not match in this branch
                  |  const wrongData: { teamId: string } = envelope.data;
                  |  return data.notificationId;
                  |}
                  |
                  |export {readEventDataId};
                  |
                  """.trimMargin(),
                  TypeName.namedImport("EventEnvelope", "!event-envelope"),
                ),
              ).build()
        )

    assertTrue(compileTypes(compiler, typeCheckedTypes))
    assertTrue(
      envelopeOutput.contains("export type EventEnvelope = SchemaOutput<typeof EventEnvelopeSchema>;"),
      envelopeOutput,
    )
    assertTrue(envelopeOutput.contains("'occurredAt': runtime.resolveSchema(OffsetDateTimeSchema)"), envelopeOutput)
    assertTrue(
      envelopeOutput.contains("import {EventIdentitySchema} from './event-identity';"),
      envelopeOutput,
    )
    assertFalse(envelopeOutput.contains("event-identity-schema"), envelopeOutput)
    assertTrue(
      envelopeOutput.contains("'actor': z.lazy(() => runtime.resolveSchema(EventIdentitySchema)).nullish()"),
      envelopeOutput,
    )
    assertTrue(envelopeOutput.contains("'description': z.string().nullish()"), envelopeOutput)
    assertTrue(envelopeOutput.contains("z.discriminatedUnion('type'"), envelopeOutput)
    assertTrue(
      dataOutput.contains(
        "export type AccountsTeamCreatedData = SchemaOutput<typeof AccountsTeamCreatedDataSchema>;",
      ),
      dataOutput,
    )
    assertFalse(dataOutput.contains("extends EventData"), dataOutput)
    assertFalse(dataOutput.contains("AccountsTeamCreatedDataSpec"), dataOutput)
    assertTrue(
      identityOutput.contains("export type EventIdentity = SchemaOutput<typeof EventIdentitySchema>;"),
      identityOutput,
    )
    assertTrue(identityOutput.contains("z.union(["), identityOutput)
    assertFalse(identityOutput.contains("z.discriminatedUnion("), identityOutput)
    assertTrue(serviceOutput.contains("Operation<void, EventEnvelope, Factory>"), serviceOutput)
    assertTrue(serviceOutput.contains("SchemaLike<EventEnvelope>"), serviceOutput)
  }

  private fun assertIrServiceSnapshot(
    compiler: TypeScriptCompiler,
    testUri: URI,
    snapshotPath: String,
    importStyle: TypeScriptTypeRegistry.ImportStyle = TypeScriptTypeRegistry.ImportStyle.ESM,
    options: TypeScriptSundayOptions = typeScriptSundayTestOptions,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf(), importStyle)
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findTypeMod("API@!api${importStyle.importExtension}", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "api${importStyle.importExtension}")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot(snapshotPath, output)
  }

  private val aggregateServiceOptions =
    TypeScriptSundayOptions(
      "http://example.com/",
      listOf("application/json"),
      "API",
      true,
      "TurnPostAPI",
    )
}
