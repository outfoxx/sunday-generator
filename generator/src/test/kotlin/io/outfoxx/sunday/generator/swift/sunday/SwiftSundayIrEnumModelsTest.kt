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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.reusableDiscriminatorMappingApi
import io.outfoxx.sunday.generator.swift.tools.reusableDiscriminatorMappingRuntimeTest
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] EnumModels Test")
@Tag("models")
class SwiftSundayIrEnumModelsTest : SwiftSundayIrTestSupport() {

  @Test
  fun `lowers shared enums and alias-like models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Alias API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        targets = mapOf("swift" to GeneratedTarget(modelModuleName = "AliasModels")),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("OPEN", "PULL_REQUEST_OPEN", "lower_snake_case", "mixed-kebab.case"),
            ),
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
              targets = mapOf("swift" to GeneratedTarget(typeName = "AliasModels.ContainerValue")),
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val enumSource =
      buildString {
        FileSpec
          .get("AliasModels", findType("AliasModels.Status", builtTypes))
          .writeTo(this)
      }
    val containerSource =
      buildString {
        FileSpec
          .get("AliasModels", findType("AliasModels.ContainerValue", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      enumSource.contains("public enum Status : String, CaseIterable, Codable, CustomStringConvertible, Sendable"),
      enumSource,
    )
    assertTrue(enumSource.contains("case `open` = \"OPEN\""), enumSource)
    assertTrue(enumSource.contains("case pullRequestOpen = \"PULL_REQUEST_OPEN\""), enumSource)
    assertTrue(enumSource.contains("case lowerSnakeCase = \"lower_snake_case\""), enumSource)
    assertTrue(enumSource.contains("case mixedKebabCase = \"mixed-kebab.case\""), enumSource)
    assertTrue(
      enumSource.contains("public var description: String {\n    return rawValue\n  }"),
      enumSource,
    )
    assertTrue(containerSource.contains("public let status: Status"), containerSource)
    assertTrue(containerSource.contains("public let alias: String"), containerSource)
    assertTrue(containerSource.contains("public let list: [String]"), containerSource)
    assertTrue(containerSource.contains("public let set: Set<String>"), containerSource)
    assertTrue(containerSource.contains("public let map: [String : String]"), containerSource)
    assertTrue(containerSource.contains("public let union: AnyValue"), containerSource)
  }

  @Test
  fun `rejects duplicate explicit Swift enum case names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("case name 'same' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `decodes reusable discriminator mappings through reference unions`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(reusableDiscriminatorMappingApi(), typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    typeRegistry.generateFiles(
      setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model),
      compiler.srcDir,
    )
    val serviceSourcePath =
      Files
        .walk(compiler.srcDir)
        .use { paths ->
          paths
            .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "API.swift" }
            .findFirst()
            .orElseThrow()
        }
    val serviceSourceRelativePath = compiler.srcDir.relativize(serviceSourcePath).toString()
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ReusableDiscriminatorMappingTests.swift"),
      reusableDiscriminatorMappingRuntimeTest,
    )

    assertTrue(compileAndTestGeneratedFiles(compiler))

    val referenceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/NotificationEventEnvelopeRef.swift",
      )
    val canonicalSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/EventOne.swift",
      )
    val notificationSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/Notification.swift",
      )
    val inheritedReferenceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/InheritedNotificationEventEnvelopeRef.swift",
      )
    val inheritedNotificationSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/InheritedNotification.swift",
      )
    val serviceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        serviceSourceRelativePath,
      )

    assertTrue(referenceSource.contains("case eventOne(EventOne)"), referenceSource)
    assertTrue(
      referenceSource.contains("case unrecognized(NotificationEventEnvelopeUnrecognized)"),
      referenceSource,
    )
    assertFalse(referenceSource.contains("public var value:"), referenceSource)
    assertFalse(referenceSource.contains("public init(value:"), referenceSource)
    assertTrue(canonicalSource.contains("public struct EventOne : EventEnvelope"), canonicalSource)
    assertFalse(canonicalSource.contains("NotificationEventEnvelope"), canonicalSource)
    assertTrue(notificationSource.contains("public let event: NotificationEventEnvelopeRef"), notificationSource)
    assertTrue(inheritedReferenceSource.contains("case eventOne(EventOne)"), inheritedReferenceSource)
    assertTrue(
      inheritedReferenceSource.contains("case unrecognized(InheritedNotificationEventEnvelopeUnrecognized)"),
      inheritedReferenceSource,
    )
    assertTrue(
      inheritedNotificationSource.contains("public let event: InheritedNotificationEventEnvelopeRef"),
      inheritedNotificationSource,
    )
    assertTrue(
      serviceSource.contains("Operation<Empty, NotificationEventEnvelopeRef, TransportType>"),
      serviceSource,
    )
    assertTrue(serviceSource.contains("AsyncStream<NotificationEventEnvelopeRef>"), serviceSource)
    assertTrue(
      serviceSource.contains("Operation<Empty, InheritedNotificationEventEnvelopeRef, TransportType>"),
      serviceSource,
    )
    assertTrue(serviceSource.contains("AsyncStream<InheritedNotificationEventEnvelopeRef>"), serviceSource)
    assertTrue(
      serviceSource.contains("decoder.decode(NotificationEventEnvelopeRef.self, from: data) }"),
      serviceSource,
    )
    assertFalse(serviceSource.contains("NotificationEventEnvelopeRef.self, from: data).value"), serviceSource)
  }

  @Test
  fun `rejects unmappable Swift enum values without explicit names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("maps to invalid case name '123'"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `rejects enum values that do not match enum entries`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum Default API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "SearchService",
              baseUri = "https://{status}.example.com",
              baseUriParameters =
                listOf(
                  GeneratedParameter(
                    "status",
                    GeneratedParameter.Location.PATH,
                    GeneratedTypeRef.named("Status"),
                    defaultValue = "missing",
                  ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    id = "search",
                    method = "GET",
                    path = "/search",
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("Swift enum 'Status' value 'missing'"), error.message)
    assertTrue(error.message!!.contains("does not match any enum value"), error.message)
  }

  @Test
  fun `generates object union enums directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertTrue(compileTypes(compiler, builtTypes))
    val serviceSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "UsersAPI.swift")
    val unionSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "UserProfile.swift")
    val validationSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "UserProfileValidation.swift")
    assertTrue(
      serviceSource.contains("public func getUser() throws -> Sunday.Operation<Empty, UserProfile, TransportType>"),
      serviceSource,
    )
    assertTrue(
      unionSource.contains("public enum UserProfile : Codable, CustomDebugStringConvertible, Sendable"),
      unionSource,
    )
    assertTrue(unionSource.contains("case userSelfResponse(UserSelfResponse)"), unionSource)
    assertTrue(unionSource.contains("case userSummaryResponse(UserSummaryResponse)"), unionSource)
    assertTrue(unionSource.contains("ModelValidationContext.decodingValue(decoder)"), unionSource)
    assertTrue(unionSource.contains("UserProfileValidation.isValid(normalized:"), unionSource)
    assertTrue(unionSource.contains("switch context.selectedAlternative"), unionSource)
    assertTrue(unionSource.contains("self = .userSelfResponse(try UserSelfResponse(from: decoder))"), unionSource)
    assertTrue(unionSource.contains("self = .userSummaryResponse(try UserSummaryResponse(from: decoder))"), unionSource)
    assertTrue(validationSource.contains("UserSelfResponseValidation.isValid(normalized:"), validationSource)
    assertTrue(validationSource.contains("UserSummaryResponseValidation.isValid(normalized:"), validationSource)
    assertFalse(validationSource.contains(".decode("), validationSource)
    assertTrue(unionSource.contains("case .userSelfResponse(let value):"), unionSource)
  }

  @Test
  fun `generates discriminator mapped object union decoders from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get("", findType("CheckoutTargetUnknownProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      unionSource.contains(
        "public enum CheckoutTargetUnknownProblem : Codable, CustomDebugStringConvertible, Sendable",
      ),
      unionSource,
    )
    assertTrue(
      unionSource.contains(
        "CheckoutTargetUnknownProblemValidation.isValid(normalized: context.originalValue!, .response, context: &context)",
      ),
      unionSource,
    )
    assertTrue(unionSource.contains("switch context.selectedAlternative"), unionSource)
    assertTrue(
      unionSource.contains("self = .repoNotFoundProblem(try RepoNotFoundProblem(from: decoder))"),
      unionSource,
    )
    assertTrue(
      unionSource.contains("self = .workingGraphNotFoundProblem(try WorkingGraphNotFoundProblem(from: decoder))"),
      unionSource,
    )
    assertFalse(unionSource.contains("object[\"type\"] != nil"), unionSource)
  }

  @Test
  fun `Swift Sunday generated files use OpenAPI enum varnames`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val notificationTypeSource = Files.readString(compiler.srcDir.resolve("Models").resolve("NotificationType.swift"))
    val fallbackTypeSource = Files.readString(compiler.srcDir.resolve("Models").resolve("FallbackType.swift"))
    val notificationSource = Files.readString(compiler.srcDir.resolve("Models").resolve("Notification.swift"))
    val notificationActivitySource =
      Files.readString(compiler.srcDir.resolve("Models").resolve("NotificationActivity.swift"))
    val reviewRequestedSource =
      Files.readString(compiler.srcDir.resolve("Models").resolve("PullRequestReviewRequestedNotification.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(
      notificationTypeSource.contains(
        "case pullRequestReviewRequested = \"notification.pull_request.review_requested\"",
      ),
      notificationTypeSource,
    )
    assertTrue(notificationTypeSource.contains("case pullRequestMerged = \"notification.pull_request.merged\""))
    assertTrue(notificationTypeSource.contains("case teamMemberAdded = \"notification.team.member_added\""))
    assertTrue(fallbackTypeSource.contains("case `open` = \"OPEN\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case lowerSnake = \"lower_snake\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case upperInterCaps = \"UpperInterCaps\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case lowerInterCaps = \"lowerInterCaps\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case dottedCase = \"dotted.case\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case mixedKebabCase = \"mixed-kebab.case\""), fallbackTypeSource)
    assertTrue(notificationSource.contains("public let type: NotificationType"), notificationSource)
    assertTrue(
      notificationActivitySource.contains("switch context.selectedAlternative"),
      notificationActivitySource,
    )
    assertTrue(
      reviewRequestedSource.contains("return NotificationType.pullRequestReviewRequested"),
      reviewRequestedSource,
    )
  }
}
