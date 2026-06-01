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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedModelScope
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTargetImplementation
import io.outfoxx.sunday.generator.ir.GeneratedTargetImplementationParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypes
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateSunday
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertKotlinSundaySnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@KotlinTest
@DisplayName("[Kotlin/Sunday] [IR] Generator Test")
class KotlinSundayIrGeneratorTest {

  private fun typeRegistry(options: Set<KotlinTypeRegistry.Option> = setOf()): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Client,
      options,
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `test helper generates service types from RAML through canonical IR path`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)
    generateSunday(testUri, typeRegistry, kotlinSundayTestOptions)

    assertEquals("Test API", api.name)
    assertKotlinSundaySnapshot(
      "RequestMethodsTest/test-request-method-generation.output.kt",
      compiledServiceSource(),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `Kotlin Sunday CLI uses the IR exporter directly`() {
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
          "kotlin",
          "KotlinSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"), source)
    assertTrue(source.contains("KotlinSundayIrGenerator"), source)
    assertFalse(source.contains("KotlinSundayGenerator("), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates shared object models directly from IR`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    findType("io.test.Test", builtTypes)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `lowers shared alias-like models directly from IR`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Alias API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "TextAlias",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextList",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.named("TextAlias")),
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
              aliases = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.scalar("integer")),
            ),
            GeneratedModel(
              name = "AliasContainer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("alias", GeneratedTypeRef.named("TextAlias"), required = true),
                  GeneratedModelProperty("list", GeneratedTypeRef.named("TextList"), required = true),
                  GeneratedModelProperty("set", GeneratedTypeRef.named("TextSet"), required = true),
                  GeneratedModelProperty("map", GeneratedTypeRef.named("TextMap"), required = true),
                  GeneratedModelProperty("union", GeneratedTypeRef.named("TextUnion"), required = true),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.AliasContainer", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(source, "public val `alias`: String")
    assertContains(source, "public val `list`: List<String>")
    assertContains(source, "public val `set`: Set<String>")
    assertContains(source, "public val `map`: Map<String, String>")
    assertContains(source, "public val `union`: Any")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates object union sealed interfaces directly from IR`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Union API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getUser",
                    method = "GET",
                    path = "/users/{userId}",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("UserProfile"),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "UserSelfResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("createdAt", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "teams",
                    GeneratedTypeRef(
                      GeneratedTypeRef.Kind.ARRAY,
                      "array",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "UserSummaryResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UserProfile",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserSelfResponse"),
                  GeneratedTypeRef.named("UserSummaryResponse"),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
          .writeTo(this)
      }
    val unionSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserProfile", builtTypes))
          .writeTo(this)
      }
    val selfSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserSelfResponse", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(serviceSource, "public fun getUser(): Operation<Unit, UserProfile, Req>")
    assertContains(unionSource, "public sealed interface UserProfile")
    assertContains(unionSource, "@JsonDeserialize(using = UserProfile.Deserializer::class)")
    assertContains(unionSource, "JsonDeserializer<UserProfile>()")
    assertContains(unionSource, "tree.has(\"createdAt\") || tree.has(\"teams\")")
    assertContains(unionSource, "return parser.codec.treeToValue(tree, UserSelfResponse::class.java)")
    assertFalse(unionSource.contains("UserSelfResponseValue"), unionSource)
    assertFalse(unionSource.contains("UserSummaryResponseValue"), unionSource)
    assertContains(selfSource, "public interface UserSelfResponse : UserProfile")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates concrete request body models when implementations are enabled`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Requests API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "createUser",
                    method = "POST",
                    path = "/users",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("CreateUserRequest"),
                        mediaTypes = listOf("application/json"),
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
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("password", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.CreateUserRequest", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(requestSource, "public data class CreateUserRequest(")
    assertContains(requestSource, "public val email: String")
    assertContains(requestSource, "public val displayName: String")
    assertContains(requestSource, "public val password: String? = null")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates empty request body objects as plain classes when implementations are enabled`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Requests API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "ReviewsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "assignPullRequestReviewer",
                    method = "PUT",
                    path = "/reviews/{reviewerUserId}",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("AssignPullRequestReviewerRequest"),
                        mediaTypes = listOf("application/json"),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "AssignPullRequestReviewerRequest",
              kind = GeneratedModel.Kind.OBJECT,
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.AssignPullRequestReviewerRequest", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(requestSource, "public class AssignPullRequestReviewerRequest()")
    assertFalse(requestSource.contains("data class AssignPullRequestReviewerRequest"), requestSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates discriminated request body roots as inheritable classes when implementations are enabled`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Updates API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "EntitiesService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "updateEntity",
                    method = "PUT",
                    path = "/entities/{entityId}",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("EntityUpdate"),
                        mediaTypes = listOf("application/json"),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "EntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("label", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "SceneUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityUpdate")),
              discriminatorValue = "scene",
              properties =
                listOf(
                  GeneratedModelProperty("sceneId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.EntityUpdate", builtTypes))
          .writeTo(this)
      }
    val childSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.SceneUpdate", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(rootSource, "public open class EntityUpdate(")
    assertFalse(rootSource.contains("data class EntityUpdate"), rootSource)
    assertContains(childSource, "public class SceneUpdate(")
    assertContains(childSource, ": EntityUpdate(")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates OpenAPI discriminated request body roots as inheritable classes when implementations are enabled`(
    @ResourceUri("openapi/ir/discriminated-update-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api = OpenApiToGeneratedApi().convert(testUri)

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.EntityUpdate", builtTypes))
          .writeTo(this)
      }
    val characterSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.CharacterUpdate", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(rootSource, "public open class EntityUpdate(")
    assertFalse(rootSource.contains("data class EntityUpdate"), rootSource)
    assertContains(characterSource, "public class CharacterUpdate(")
    assertContains(characterSource, ": EntityUpdate(")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `clears inherited JsonDeserialize from direct union member implementations`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Users API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "UserSelfResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UserSummaryResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UserResponse",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserSelfResponse"),
                  GeneratedTypeRef.named("UserSummaryResponse"),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserResponse", builtTypes))
          .writeTo(this)
      }
    val selfSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserSelfResponse", builtTypes))
          .writeTo(this)
      }
    val summarySource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserSummaryResponse", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(unionSource, "@JsonDeserialize(using = UserResponse.Deserializer::class)")
    assertContains(unionSource, "return parser.codec.treeToValue(tree, UserSelfResponse::class.java)")
    assertContains(selfSource, "@JsonDeserialize(using = JsonDeserializer.None::class)")
    assertContains(summarySource, "@JsonDeserialize(using = JsonDeserializer.None::class)")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates discriminator mapped object union decoders from IR`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Problems API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "RepoNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "WorkingGraphNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "CheckoutTargetUnknownProblem",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("RepoNotFoundProblem"),
                  GeneratedTypeRef.named("WorkingGraphNotFoundProblem"),
                ),
              discriminator = "code",
              discriminatorMappings =
                mapOf(
                  "TPG-REPO-404" to GeneratedTypeRef.named("RepoNotFoundProblem"),
                  "TPG-WG-404" to GeneratedTypeRef.named("WorkingGraphNotFoundProblem"),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.CheckoutTargetUnknownProblem", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(unionSource, "val discriminatorValue = tree.get(\"code\")?.asText()")
    assertContains(unionSource, "if (discriminatorValue == \"TPG-REPO-404\")")
    assertContains(unionSource, "RepoNotFoundProblem::class.java")
    assertContains(unionSource, "if (discriminatorValue == \"TPG-WG-404\")")
    assertFalse(unionSource.contains("tree.has(\"type\")"), unionSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `does not redeclare inherited object model properties from IR`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Problems API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "ProblemsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProblem",
                    method = "GET",
                    path = "/problem",
                    responses = listOf(GeneratedResponse(status = 200, type = GeneratedTypeRef.named("RepoProblem"))),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "GraphProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("traceId", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "RepoProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("GraphProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string", nullable = true), required = false),
                  GeneratedModelProperty(
                    "traceId",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = false,
                  ),
                  GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val childSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.RepoProblem", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertFalse(childSource.contains("public val `code`"), childSource)
    assertFalse(childSource.contains("public val `traceId`"), childSource)
    assertContains(childSource, "public val `repoId`: String")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Sunday throwable source problem models from IR`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC9457,
      )
    val api =
      GeneratedApi(
        name = "Problems API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "GraphProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = false),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "RepoProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("GraphProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string", nullable = true), required = false),
                  GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.GraphProblem", builtTypes))
          .writeTo(this)
      }
    val childSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.RepoProblem", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(rootSource, "SundayHttpProblem(")
    assertContains(rootSource, "type ?: URI.create(\"about:blank\")")
    assertFalse(rootSource.contains("public val `type`"), rootSource)
    assertContains(rootSource, "public val code: String")
    assertContains(childSource, ": GraphProblem(")
    assertFalse(childSource.contains("public val `code`"), childSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates concrete shared object models when implementations are enabled`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      GeneratedApi(
        name = "Models API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProfile",
                    method = "GET",
                    path = "/profile",
                    responses = listOf(GeneratedResponse(status = 200, type = GeneratedTypeRef.named("UserProfile"))),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "BaseProfile",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("traceId", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "UserProfile",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("BaseProfile")),
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string", nullable = true), required = false),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val baseSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.BaseProfile", builtTypes))
          .writeTo(this)
      }
    val childSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.UserProfile", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(baseSource, "public open class BaseProfile(")
    assertContains(childSource, "public class UserProfile(")
    assertContains(childSource, ": BaseProfile(")
    assertFalse(childSource.contains("public val id:"), childSource)
    assertContains(childSource, "public val displayName: String")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `uses content type header parameter as request media selection in Kotlin Sunday`() {
    val typeRegistry = typeRegistry()
    val api =
      avatarUploadApi()

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(serviceSource, "contentTypes = listOf(when (contentType)")
    assertContains(serviceSource, "AvatarContentType.ImagePng -> MediaType.from(\"image/png\")")
    assertFalse(serviceSource.contains("\"Content-Type\" to contentType"), serviceSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates aggregate Kotlin Sunday service from IR`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Craft API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
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
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/projects/{projectId}",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "projectId",
                          location = GeneratedParameter.Location.PATH,
                          type = GeneratedTypeRef.scalar("string"),
                          required = true,
                        ),
                      ),
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(
      api,
      typeRegistry,
      KotlinSundayOptions(
        defaultServicePackageName = "io.test.service",
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
        aggregateServices = true,
        aggregateServiceName = "TurnPostAPI",
      ),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateSource =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.TurnPostAPI", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(aggregateSource, "public val transport: Transport")
    assertContains(aggregateSource, "public val defaultContentTypes: List<MediaType>")
    assertContains(aggregateSource, "public val defaultAcceptTypes: List<MediaType>")
    assertContains(aggregateSource, "public val users: UsersAPI")
    assertContains(aggregateSource, "public val projects: ProjectsAPI")
    assertContains(aggregateSource, "this.users = UsersAPI(")
    assertContains(aggregateSource, "this.projects = ProjectsAPI(")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates composed OpenAPI and AsyncAPI HTTP event service from IR`(
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/event-stream-payload.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.EventsAPI", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(source, "public fun streamProjectEvents(")
    assertContains(source, ".eventStream(")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `omits broker-only AsyncAPI channels from Kotlin Sunday output`(
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/http-and-broker-events.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventsSource =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.EventsAPI", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(eventsSource, "public fun streamProjectEvents(")
    assertContains(eventsSource, "\"https://api.example.com\"")
    assertFalse(eventsSource.contains("broker.example.com"), eventsSource)
    assertFalse(eventsSource.contains("consumePlatformEvent"), eventsSource)
    assertFalse(eventsSource.contains("consumeBrokerPathEvent"), eventsSource)
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "PlatformAPI" })
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "BrokerAPI" })
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates typed AsyncAPI event payload models from IR`(
    @ResourceUri("asyncapi/ir/typed-event-envelope.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = typeRegistry(setOf(KotlinTypeRegistry.Option.JacksonAnnotations))
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val envelopeSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.EventEnvelope", builtTypes))
          .writeTo(this)
      }
    val eventDataSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.EventData", builtTypes))
          .writeTo(this)
      }
    val createdDataSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.ProjectCreatedData", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(envelopeSource, "public val `data`: EventData")
    assertContains(envelopeSource, "@get:JsonTypeInfo(")
    assertContains(envelopeSource, "JsonTypeInfo.As.EXTERNAL_PROPERTY")
    assertContains(envelopeSource, "property = \"type\"")
    assertContains(
      envelopeSource,
      "JsonSubTypes.Type(value = ProjectCreatedData::class, name = \"project.created\")",
    )
    assertContains(eventDataSource, "public sealed interface EventData")
    assertContains(eventDataSource, "using = EventData.Deserializer::class")
    assertContains(eventDataSource, "ProjectCreatedData::class.java")
    assertContains(createdDataSource, "public interface ProjectCreatedData : EventData")
    assertFalse(envelopeSource.contains("Map<String, Any>"), envelopeSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `treats OpenAPI empty schemas as Any in Kotlin Sunday`(
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val api = GeneratedApiIrExporter().export(testUri)

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val holderSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.AnyHolder", builtTypes))
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.API", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(holderSource, "public val `value`: Any?")
    assertContains(holderSource, "public val `documented`: Any?")
    assertContains(holderSource, "public val `named`: Any?")
    assertContains(serviceSource, "body: Any")
    assertContains(serviceSource, "Operation<Any, Any, Req>")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `uses OpenAPI enum varnames and wire values in Kotlin Sunday`(
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry(setOf(KotlinTypeRegistry.Option.JacksonAnnotations))
    val api = GeneratedApiIrExporter().export(testUri)

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val notificationTypeSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.NotificationType", builtTypes))
          .writeTo(this)
      }
    val fallbackTypeSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.FallbackType", builtTypes))
          .writeTo(this)
      }
    val notificationSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.Notification", builtTypes))
          .writeTo(this)
      }
    val eventSource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.NotificationEvent", builtTypes))
          .writeTo(this)
      }
    val activitySource =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.NotificationActivity", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(notificationTypeSource, "PullRequestReviewRequested(\"notification.pull_request.review_requested\")")
    assertContains(notificationTypeSource, "PullRequestMerged(\"notification.pull_request.merged\")")
    assertContains(notificationTypeSource, "TeamMemberAdded(\"notification.team.member_added\")")
    assertContains(notificationTypeSource, "@JsonValue")
    assertContains(notificationTypeSource, "@JsonCreator")
    assertContains(fallbackTypeSource, "OPEN(\"OPEN\")")
    assertContains(fallbackTypeSource, "LowerSnake(\"lower_snake\")")
    assertContains(fallbackTypeSource, "UpperInterCaps(\"UpperInterCaps\")")
    assertContains(fallbackTypeSource, "LowerInterCaps(\"lowerInterCaps\")")
    assertContains(fallbackTypeSource, "DottedCase(\"dotted.case\")")
    assertContains(fallbackTypeSource, "MixedKebabCase(\"mixed-kebab.case\")")
    assertContains(notificationSource, "public val `type`: NotificationType")
    assertContains(eventSource, "public val `kind`: NotificationType")
    assertContains(activitySource, "if (discriminatorValue == \"notification.pull_request.review_requested\")")
  }

  @Test
  fun `rejects duplicate explicit Kotlin enum constant names`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("one", "two"),
              enumValueNames = listOf("same", "same"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
          .generateServiceTypes()
      }

    assertContains(error.message!!, "constant name 'Same' is used for multiple values")
    assertContains(error.message!!, "x-enum-varnames")
  }

  @Test
  fun `rejects invalid explicit Kotlin enum constant names`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("wire"),
              enumValueNames = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
          .generateServiceTypes()
      }

    assertContains(error.message!!, "x-enum-varnames entry '123'")
    assertContains(error.message!!, "for value 'wire'")
    assertContains(error.message!!, "invalid constant name '123'")
  }

  @Test
  fun `rejects unmappable Kotlin enum values without explicit names`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
          .generateServiceTypes()
      }

    assertContains(error.message!!, "maps to invalid constant name '123'")
    assertContains(error.message!!, "x-enum-varnames")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `uses streaming operations for streaming request bodies`(
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val api = GeneratedApiIrExporter().export(testUri)

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))

    val serviceSource = compiledServiceSource()

    val compactServiceSource = serviceSource.compactWhitespace()

    assertContains(
      compactServiceSource,
      "public fun importArchive(body: StreamingBody): StreamingOperation<ImportAccepted, Req>",
    )
    assertContains(
      compactServiceSource,
      "public fun importArchiveOrNil(body: StreamingBody): NullableOperation<StreamingBody, ImportAccepted, Req>",
    )
    assertContains(serviceSource, "this.transport.operation<StreamingBody, ImportAccepted, Req>")
    assertContains(serviceSource, "this.transport.nullableOperation<StreamingBody, ImportAccepted, Req>")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `lowers supported IR scalar formats to Kotlin temporal and identity types`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Format API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:format.yaml"),
        services = listOf(),
        models =
          listOf(
            GeneratedModel(
              name = "FormatModel",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "createdAt",
                    type = GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "legacyCreatedAt",
                    type = GeneratedTypeRef.scalar("datetime"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "localCreatedAt",
                    type = GeneratedTypeRef.scalar("string", format = "date-time-only"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "birthday",
                    type = GeneratedTypeRef.scalar("string", format = "date"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "startsAt",
                    type = GeneratedTypeRef.scalar("string", format = "time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "requestId",
                    type = GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "callbackUrl",
                    type = GeneratedTypeRef.scalar("string", format = "url"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "payload",
                    type = GeneratedTypeRef.scalar("string", format = "binary"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "anyValue",
                    type = GeneratedTypeRef.scalar("any"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "title",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "enabled",
                    type = GeneratedTypeRef.scalar("boolean"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "defaultCount",
                    type = GeneratedTypeRef.scalar("integer"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "smallCount",
                    type = GeneratedTypeRef.scalar("integer", format = "int8"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "mediumCount",
                    type = GeneratedTypeRef.scalar("integer", format = "int16"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "count",
                    type = GeneratedTypeRef.scalar("integer", format = "int32"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "aliasCount",
                    type = GeneratedTypeRef.scalar("integer", format = "int"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "largeCount",
                    type = GeneratedTypeRef.scalar("integer", format = "int64"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "aliasLargeCount",
                    type = GeneratedTypeRef.scalar("integer", format = "long"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "legacyCount",
                    type = GeneratedTypeRef.scalar("int32"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "legacyLargeCount",
                    type = GeneratedTypeRef.scalar("int64"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "directLongCount",
                    type = GeneratedTypeRef.scalar("long"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "ratio",
                    type = GeneratedTypeRef.scalar("number"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "preciseRatio",
                    type = GeneratedTypeRef.scalar("double"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "filePayload",
                    type = GeneratedTypeRef.scalar("file"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "emptyValue",
                    type = GeneratedTypeRef.scalar("nil"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    name = "unknownValue",
                    type = GeneratedTypeRef.scalar("unknown"),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.FormatModel", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(source, "public val `createdAt`: OffsetDateTime")
    assertContains(source, "public val `legacyCreatedAt`: OffsetDateTime")
    assertContains(source, "public val `localCreatedAt`: LocalDateTime")
    assertContains(source, "public val `birthday`: LocalDate")
    assertContains(source, "public val `startsAt`: LocalTime")
    assertContains(source, "public val `requestId`: UUID")
    assertContains(source, "public val `callbackUrl`: URI")
    assertContains(source, "public val `payload`: ByteArray")
    assertContains(source, "public val `anyValue`: Any")
    assertContains(source, "public val `title`: String")
    assertContains(source, "public val `enabled`: Boolean")
    assertContains(source, "public val `defaultCount`: Int")
    assertContains(source, "public val `smallCount`: Byte")
    assertContains(source, "public val `mediumCount`: Short")
    assertContains(source, "public val `count`: Int")
    assertContains(source, "public val `aliasCount`: Int")
    assertContains(source, "public val `largeCount`: Long")
    assertContains(source, "public val `aliasLargeCount`: Long")
    assertContains(source, "public val `legacyCount`: Int")
    assertContains(source, "public val `legacyLargeCount`: Long")
    assertContains(source, "public val `directLongCount`: Long")
    assertContains(source, "public val `ratio`: Double")
    assertContains(source, "public val `preciseRatio`: Double")
    assertContains(source, "public val `filePayload`: ByteArray")
    assertContains(source, "public val `emptyValue`: Unit")
    assertContains(source, "public val `unknownValue`: String")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `registers operation scoped models through the IR model path`() {
    val typeRegistry = typeRegistry()
    val parameterScope =
      GeneratedModelScope(
        service = "LocalService",
        operation = "search",
        usage = GeneratedModelScope.Usage.PARAMETER,
        name = "mode",
      )
    val requestScope =
      GeneratedModelScope(
        service = "LocalService",
        operation = "search",
        usage = GeneratedModelScope.Usage.REQUEST_BODY,
      )
    val responseScope =
      GeneratedModelScope(
        service = "LocalService",
        operation = "search",
        usage = GeneratedModelScope.Usage.RESPONSE_BODY,
        status = 200,
      )
    val api =
      GeneratedApi(
        name = "Local API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "SearchMode",
              kind = GeneratedModel.Kind.ENUM,
              scope = parameterScope,
              values = listOf("name", "date"),
            ),
            GeneratedModel(
              name = "SearchRequestBody",
              kind = GeneratedModel.Kind.OBJECT,
              scope = requestScope,
              properties = listOf(GeneratedModelProperty("term", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "SearchResponseBody",
              kind = GeneratedModel.Kind.OBJECT,
              scope = responseScope,
              properties = listOf(GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true)),
            ),
          ),
        services =
          listOf(
            GeneratedService(
              name = "LocalService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "search",
                    method = "POST",
                    path = "/search",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "mode",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.named("SearchMode", scope = parameterScope),
                        ),
                      ),
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("SearchRequestBody", scope = requestScope),
                        mediaTypes = listOf("application/json"),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("SearchResponseBody", scope = responseScope),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.API", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    findType("io.test.service.API.SearchMode", builtTypes)
    findType("io.test.service.API.SearchRequestBody", builtTypes)
    findType("io.test.service.API.SearchResponseBody", builtTypes)
    assertContains(source, "public enum class SearchMode")
    assertContains(source, "public interface SearchRequestBody")
    assertContains(source, "public interface SearchResponseBody")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `uses response status when naming operation scoped response models`() {
    val typeRegistry = typeRegistry()
    val notReadyScope =
      GeneratedModelScope(
        service = "APIService",
        operation = "updateWorkingGraphCheckoutTarget",
        usage = GeneratedModelScope.Usage.RESPONSE_BODY,
        status = 400,
      )
    val notFoundScope =
      GeneratedModelScope(
        service = "APIService",
        operation = "updateWorkingGraphCheckoutTarget",
        usage = GeneratedModelScope.Usage.RESPONSE_BODY,
        status = 404,
      )
    val localName = "UpdateWorkingGraphCheckoutTargetResponseBody"
    val api =
      GeneratedApi(
        name = "API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = localName,
              kind = GeneratedModel.Kind.OBJECT,
              scope = notReadyScope,
              properties =
                listOf(
                  GeneratedModelProperty("reason", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = localName,
              kind = GeneratedModel.Kind.OBJECT,
              scope = notFoundScope,
              properties =
                listOf(
                  GeneratedModelProperty("checkoutId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
        services =
          listOf(
            GeneratedService(
              name = "APIService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "updateWorkingGraphCheckoutTarget",
                    method = "PUT",
                    path = "/graph/checkout/target",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 400,
                          type = GeneratedTypeRef.named(localName, scope = notReadyScope),
                          mediaTypes = listOf("application/json"),
                        ),
                        GeneratedResponse(
                          status = 404,
                          type = GeneratedTypeRef.named(localName, scope = notFoundScope),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.API", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    findType("io.test.service.API.UpdateWorkingGraphCheckoutTarget400ResponseBody", builtTypes)
    findType("io.test.service.API.UpdateWorkingGraphCheckoutTarget404ResponseBody", builtTypes)
    assertContains(source, "public interface UpdateWorkingGraphCheckoutTarget400ResponseBody")
    assertContains(source, "public interface UpdateWorkingGraphCheckoutTarget404ResponseBody")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates shared models with Kotlin target metadata from IR`() {
    val typeRegistry = typeRegistry()
    val api =
      GeneratedApi(
        name = "Target API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        targets =
          mapOf(
            "kotlin" to GeneratedTarget(modelPackageName = "io.example.models"),
            "kotlinClient" to GeneratedTarget(modelPackageName = "io.example.client.models"),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    targets =
                      mapOf(
                        "kotlinClient" to
                          GeneratedTarget(
                            implementation =
                              GeneratedTargetImplementation(
                                code = "return %T::class.qualifiedName + %S",
                                parameters =
                                  listOf(
                                    GeneratedTargetImplementationParameter("Type", "kotlin.String"),
                                    GeneratedTargetImplementationParameter("String", "-display"),
                                  ),
                              ),
                          ),
                      ),
                  ),
                ),
              targets =
                mapOf(
                  "kotlinClient" to GeneratedTarget(typeName = "io.example.client.UserValue"),
                ),
            ),
            GeneratedModel(
              name = "UserPreference",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("theme", GeneratedTypeRef.scalar("string"), required = true)),
              nested =
                GeneratedNestedType(
                  enclosedIn = GeneratedTypeRef.named("User"),
                  name = "Preference",
                ),
            ),
            GeneratedModel(
              name = "UserPreference-Option",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true)),
              nested =
                GeneratedNestedType(
                  strategy = GeneratedNestedType.Strategy.DASHED,
                  enclosedIn = GeneratedTypeRef.named("UserPreference"),
                  name = "Option",
                ),
            ),
            GeneratedModel(
              name = "UserPatch",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"))),
              patchable = true,
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userSource =
      buildString {
        FileSpec
          .get("io.example.client", findType("io.example.client.UserValue", builtTypes))
          .writeTo(this)
      }
    val patchSource =
      buildString {
        FileSpec
          .get("io.example.client.models", findType("io.example.client.models.UserPatch", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(userSource, "public interface UserValue")
    assertContains(userSource, "public interface Preference")
    assertContains(userSource, "public interface Option")
    assertContains(userSource, "public val `displayName`: String")
    assertContains(userSource, "get() = String::class.qualifiedName + \"-display\"")
    assertContains(patchSource, "public interface UserPatch : Patch")
    assertContains(patchSource, "public var `displayName`: UpdateOp<String>")
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `resolves duplicate shared model names by source identity from IR`() {
    val typeRegistry = typeRegistry()
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "library.raml")
    val api =
      GeneratedApi(
        name = "Library API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "main.raml"),
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Test", source = librarySource)),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              properties = listOf(GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true)),
              targets =
                mapOf(
                  "kotlinClient" to GeneratedTarget(modelPackageName = "io.example.library"),
                ),
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val testType = findType("io.test.Test", builtTypes)

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(testType.superinterfaces.containsKey(ClassName("io.example.library", "Test")))
    findType("io.test.Test", builtTypes)
    findType("io.example.library.Test", builtTypes)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates referenced problem types directly from IR`() {
    val typeRegistry = typeRegistry()
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
              detail = "Invalid id provided",
              fields =
                listOf(
                  GeneratedModelProperty("traceId", GeneratedTypeRef.scalar("string")),
                ),
            ),
            GeneratedProblem(
              name = "UnusedProblem",
              sourceName = "unused",
              typeUri = "http://example.com/unused",
              status = 409,
              title = "Unused",
              detail = "Unused problem",
            ),
          ),
      )

    KotlinSundayIrGenerator(api, typeRegistry, kotlinSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test", findType("io.test.InvalidIdProblem", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertContains(source, "public class InvalidIdProblem")
    assertContains(source, "traceId: String")
    assertContains(source, "AbstractThrowableProblem(TYPE_URI, \"Invalid Id\", Status.BAD_REQUEST")
    assertFalse(builtTypes.containsKey(ClassName("io.test", "UnusedProblem")))
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates request methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestMethodsTest/test-request-method-generation.output.kt",
      compiledServiceSource(),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates uri parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestUriParamsTest/test-basic-uri-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates base URL companion from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "BaseUriTest/test-baseurl-generation-in-api.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates basic query parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-basic-query-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates optional query parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-optional-query-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates inline query parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-generation-of-multiple-query-parameters-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates basic header parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestHeaderParamsTest/test-basic-header-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates constant header parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestHeaderParamsTest/test-constant-header-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates optional header parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestHeaderParamsTest/test-optional-header-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates inline header parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestHeaderParamsTest/test-generation-of-multiple-header-parameters-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates inherited uri parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestUriParamsTest/test-inherited-uri-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates optional uri parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestUriParamsTest/test-optional-uri-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates inline uri parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestUriParamsTest/test-generation-of-multiple-uri-parameters-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates mixed inline parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestMixedParamsTest/test-generation-of-multiple-parameters-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates same-name mixed inline parameters from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestMixedParamsTest/test-generation-of-multiple-parameters-of-same-name-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates request body from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestBodyParamTest/test-basic-body-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates optional request body from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestBodyParamTest/test-optional-body-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates explicit request content type from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestBodyParamTest/test-generation-of-body-parameter-with-explicit-content-type.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates basic response body media from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates response body media from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates inline response body media from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates no content response from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates no response from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-none.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseBodyContentTest/test-generation-of-no-response-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates request builder methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "BuilderMethodsTest/test-request-builder-method-generation.output.kt",
      compiledServiceSource(),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates response builder methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "BuilderMethodsTest/test-response-builder-method-generation.output.kt",
      compiledServiceSource(),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates problem registration from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-api-problem-registration.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates no-problem registration from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-api-problem-registration-when-no-problems.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates base URI problem registration from IR`(
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI,
  ) {
    val generated = generateServiceSource(testUri).compactWhitespace()

    assertContains(
      generated,
      "transport.registerProblem(\"http://api.example.com/api/invalid_id\", " +
        "InvalidIdProblem::class)",
    )
    assertContains(
      generated,
      "transport.registerProblem(\"http://api.example.com/api/test_not_found\", " +
        "TestNotFoundProblem::class)",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates absolute problem-base URI registration from IR`(
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI,
  ) {
    val generated = generateServiceSource(testUri).compactWhitespace()

    assertContains(
      generated,
      "transport.registerProblem(\"http://errors.example.com/docs/invalid_id\", " +
        "InvalidIdProblem::class)",
    )
    assertContains(
      generated,
      "transport.registerProblem(\"http://errors.example.com/docs/test_not_found\", " +
        "TestNotFoundProblem::class)",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates relative problem-base URI registration from IR`(
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI,
  ) {
    val generated = generateServiceSource(testUri).compactWhitespace()

    assertContains(
      generated,
      "transport.registerProblem(\"http://example.com/api/errors/invalid_id\", " +
        "InvalidIdProblem::class)",
    )
    assertContains(
      generated,
      "transport.registerProblem(\"http://example.com/api/errors/test_not_found\", " +
        "TestNotFoundProblem::class)",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates library problem registration from IR`(
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI,
  ) {
    val generated = generateServiceSource(testUri).compactWhitespace()

    assertContains(
      generated,
      "transport.registerProblem(\"http://example.com/invalid_id\", InvalidIdProblem::class)",
    )
    assertContains(
      generated,
      "transport.registerProblem(\"http://example.com/test_not_found\", TestNotFoundProblem::class)",
    )
    assertFalse(generated.contains("CreateFailedProblem"), generated)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates nullify methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "RequestMethodsTest/test-request-method-generation-with-nullify.output.kt",
      compiledServiceSource(),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates event source methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseEventsTest/test-event-source-method.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates event stream methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseEventsTest/test-event-stream-method-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates common base event stream methods from IR with existing Kotlin Sunday output shape`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {
    val typeSpec = generateServiceType(testUri)

    assertKotlinSundaySnapshot(
      "ResponseEventsTest/test-event-stream-method-generation-for-common-base-events.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  private fun generateServiceType(
    testUri: URI,
    options: KotlinSundayOptions = kotlinSundayTestOptions,
    compile: Boolean = true,
  ): TypeSpec {
    val typeRegistry = typeRegistry()
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    KotlinSundayIrGenerator(api, typeRegistry, options).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    if (compile) {
      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    }

    return findType("io.test.service.API", builtTypes)
  }

  @OptIn(ExperimentalCompilerApi::class)
  private fun generateServiceSource(
    testUri: URI,
    compile: Boolean = true,
  ): String {
    generateServiceType(testUri, compile = compile)
    return compiledServiceSource()
  }

  private fun compiledServiceSource(): String =
    CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/service/API.kt")

  private fun avatarUploadApi(): GeneratedApi =
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

  private fun String.compactWhitespace(): String = replace(Regex("\\s+"), " ")

  private fun assertContains(
    actual: String,
    expected: String,
  ) {
    assertTrue(actual.contains(expected), actual)
  }
}
