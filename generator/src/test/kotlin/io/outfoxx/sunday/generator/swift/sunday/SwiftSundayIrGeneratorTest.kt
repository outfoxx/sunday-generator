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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedDocumentation
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.AssociatedExtensions
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayOptions
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateSunday
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.tag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Generator Test")
class SwiftSundayIrGeneratorTest {

  @Test
  fun `Swift Sunday CLI uses IR exporter directly`() {
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
          "swift",
          "SwiftSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("SwiftSundayIrGenerator"))
    assertFalse(source.contains("SwiftSundayGenerator("), source)
  }

  @Test
  fun `Swift Sunday IR renderer does not read AMF service model types`() {
    val source =
      Files.readString(
        Path.of(
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "swift",
          "SwiftSundayIrGenerator.kt",
        ),
      )

    assertFalse(source.contains("amf."), source)
    assertFalse(source.contains("processService"), source)
    assertFalse(source.contains("processResourceMethod"), source)
    assertFalse(source.contains("processReturnType("), source)
  }

  @Test
  fun `public generator generates service types from RAML through IR path`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val builtTypes = generateSunday(testUri, typeRegistry, compiler)
    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestMethodsTest/test-request-method-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `Swift Sunday generated files track Foundation imports for file payloads`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "TurnPost API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:turnpost.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "TeamsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "putTeamAvatar",
                    method = "PUT",
                    path = "/teams/{teamId}/avatar",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("application/octet-stream"),
                      ),
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()
    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Service), compiler.srcDir)

    val source = Files.readString(compiler.srcDir.resolve("TeamsAPI.swift"))
    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("body: Data"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from RAML source`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/ir/any-shapes.raml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val source = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import PotentCodables"), source)
    assertTrue(source.contains("public let value: AnyValue?"), source)
    assertTrue(source.contains("try container.decodeIfPresent(AnyValue.self, forKey: .value)"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from OpenAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/operation-surface-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files use streaming operations for streaming request bodies`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val source =
      Files
        .walk(compiler.srcDir)
        .use { files ->
          files
            .filter { path -> path.fileName.toString().endsWith(".swift") }
            .map { path -> Files.readString(path) }
            .filter { content -> "importArchive" in content }
            .findFirst()
            .orElseThrow()
        }

    assertTrue("body: StreamingBody" in source)
    assertTrue("Sunday.StreamingOperation<ImportAccepted, TransportType>" in source)
    assertTrue("Sunday.NilableOperation<StreamingBody, ImportAccepted, TransportType>" in source)
    assertTrue("spec: Sunday.OperationSpec.streaming(" in source)
    assertTrue("nilify: Sunday.NilifySpec(" in source)
    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files treat OpenAPI empty schemas as AnyValue`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(
      compiler,
      GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = true)).export(testUri),
    )

    val holderSource = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))
    val entityStatePropertyValueSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyValue.swift"),
      )
    val entityStatePropertyRefSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyRef.swift"),
      )
    val serviceSource = Files.readString(compiler.srcDir.resolve("NarrativeAPI.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(holderSource.contains("public let value: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let documented: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let named: AnyValue?"), holderSource)
    assertTrue(
      entityStatePropertyValueSource.contains("public struct EntityStatePropertyValue : EntityStateProperty"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyValueSource.contains("public let value: AnyValue?"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyRefSource.contains("case value(EntityStatePropertyValue)"),
      entityStatePropertyRefSource,
    )
    assertTrue(serviceSource.contains("body: AnyValue"), serviceSource)
    assertTrue(serviceSource.contains("Operation<AnyValue, AnyValue, TransportType>"), serviceSource)
  }

  @Test
  fun `Swift Sunday generated files compile from AsyncAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files compile from composed OpenAPI and AsyncAPI sources`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri)))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `generates documentation comments from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Documentation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:docs.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/repos/{projectId}",
                    documentation =
                      GeneratedDocumentation(
                        summary = "Fetch a project.",
                        description = "Returns a project visible to the `/repos/**` caller.",
                      ),
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "projectId",
                          location = GeneratedParameter.Location.PATH,
                          type = GeneratedTypeRef.scalar("string"),
                        ),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("Project"),
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
              name = "Project",
              kind = GeneratedModel.Kind.OBJECT,
              documentation =
                GeneratedDocumentation(
                  summary = "Project model.",
                  description = "A project in the workspace.",
                ),
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "projectId",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    documentation = GeneratedDocumentation(description = "Stable project identifier."),
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("ProjectsAPI", builtTypes))
          .writeTo(this)
      }
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("Project", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("Fetch a project."), serviceSource)
    assertTrue(serviceSource.contains("Returns a project visible to the `/repos/ **` caller."), serviceSource)
    assertTrue(modelSource.contains("Project model."), modelSource)
    assertTrue(modelSource.contains("A project in the workspace."), modelSource)
    assertTrue(modelSource.contains("Stable project identifier."), modelSource)
  }

  @Test
  fun `uses content type header parameter as request media selection in Swift Sunday`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = avatarUploadApi()

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("UsersAPI", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("try .init(valid: contentType.rawValue)"), serviceSource)
    assertTrue(serviceSource.contains("acceptTypes: [try .init(valid: \"image/png\")"), serviceSource)
    assertFalse(serviceSource.contains("\"Content-Type\": contentType"), serviceSource)
  }

  @Test
  fun `generates composed OpenAPI and AsyncAPI HTTP event service from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/event-stream-payload.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("EventsAPI", builtTypes)
    val source =
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public func streamProjectEvents("), source)
    assertTrue(source.contains("transport.eventStream"), source)
  }

  @Test
  fun `omits broker-only AsyncAPI channels from Swift Sunday output`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/http-and-broker-events.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventsSource =
      buildString {
        FileSpec
          .get("", findType("EventsAPI", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(eventsSource.contains("public func streamProjectEvents("), eventsSource)
    assertTrue(eventsSource.contains("format: \"https://api.example.com\""), eventsSource)
    assertFalse(eventsSource.contains("broker.example.com"), eventsSource)
    assertFalse(eventsSource.contains("consumePlatformEvent"), eventsSource)
    assertFalse(eventsSource.contains("consumeBrokerPathEvent"), eventsSource)
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "PlatformAPI" })
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "BrokerAPI" })
  }

  @Test
  fun `generates typed AsyncAPI event payload models from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventEnvelopeSource =
      buildString {
        FileSpec
          .get("", findType("EventEnvelope", builtTypes))
          .writeTo(this)
      }
    val projectCreatedSource =
      buildString {
        FileSpec
          .get("", findType("ProjectCreatedData", builtTypes))
          .writeTo(this)
      }
    val eventDataSource =
      buildString {
        FileSpec
          .get("", findType("EventData", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "EventData" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "ProjectDeletedData" })
    assertTrue(
      eventEnvelopeSource.contains("public enum EventEnvelope : Codable, CustomDebugStringConvertible, Sendable"),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("case projectCreated(ProjectCreatedEvent)"), eventEnvelopeSource)
    assertTrue(eventEnvelopeSource.contains("public var data: EventData"), eventEnvelopeSource)
    assertTrue(eventEnvelopeSource.contains("public struct ProjectCreatedEvent"), eventEnvelopeSource)
    assertTrue(
      eventEnvelopeSource.contains("let discriminatorValue = try container.decode(String.self, forKey: .type)"),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("if discriminatorValue == \"project.created\""), eventEnvelopeSource)
    assertTrue(eventDataSource.contains("public protocol EventData"), eventDataSource)
    assertTrue(projectCreatedSource.contains("public struct ProjectCreatedData : EventData"), projectCreatedSource)
  }

  @Test
  fun `generates request methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation.output.swift",
    )
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
                  GeneratedModelProperty(
                    "metadata",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.scalar("object")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let id: String"), source)
    assertTrue(source.contains("public let displayName: String?"), source)
    assertTrue(source.contains("public let metadata: [[String : AnyValue]]"), source)
    assertTrue(source.contains("case displayName = \"displayName\""), source)
  }

  @Test
  fun `lowers IR date scalar properties to Swift Date`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Dates API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "AuditEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("dateOnly", GeneratedTypeRef.scalar("date"), required = true),
                  GeneratedModelProperty("timeOnly", GeneratedTypeRef.scalar("time"), required = true),
                  GeneratedModelProperty("localDateTime", GeneratedTypeRef.scalar("datetime-only"), required = true),
                  GeneratedModelProperty("timestamp", GeneratedTypeRef.scalar("datetime"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("AuditEvent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("public let dateOnly: Date"), source)
    assertTrue(source.contains("public let timeOnly: Date"), source)
    assertTrue(source.contains("public let localDateTime: Date"), source)
    assertTrue(source.contains("public let timestamp: Date"), source)
  }

  @Test
  fun `lowers supported IR scalar formats to Swift Foundation types`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Formatted API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "FormattedService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getFormatted",
                    method = "GET",
                    path = "/formatted/{resourceId}",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "resourceId",
                          GeneratedParameter.Location.PATH,
                          GeneratedTypeRef.scalar("string", format = "uuid"),
                          required = true,
                        ),
                        GeneratedParameter(
                          "callbackUrl",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                        ),
                        GeneratedParameter(
                          "location",
                          GeneratedParameter.Location.HEADER,
                          GeneratedTypeRef.scalar("string", format = "uri-reference"),
                          required = true,
                          serializationName = "Location",
                        ),
                      ),
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "FormattedModel",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "absoluteUrl",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "relativeUrl",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri-reference"),
                  ),
                  GeneratedModelProperty(
                    "id",
                    GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "encoded",
                    GeneratedTypeRef.scalar("string", format = "byte"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "binary",
                    GeneratedTypeRef.scalar("string", format = "binary"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "createdAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "calendarDay",
                    GeneratedTypeRef.scalar("string", format = "date"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localTime",
                    GeneratedTypeRef.scalar("string", format = "time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localDateTime",
                    GeneratedTypeRef.scalar("string", format = "datetime-only"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("FormattedModel", builtTypes))
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(modelSource.contains("import Foundation"), modelSource)
    assertTrue(modelSource.contains("public let absoluteUrl: URL"), modelSource)
    assertTrue(modelSource.contains("public let relativeUrl: URL?"), modelSource)
    assertTrue(modelSource.contains("public let id: UUID"), modelSource)
    assertTrue(modelSource.contains("public let encoded: Data"), modelSource)
    assertTrue(modelSource.contains("public let binary: Data"), modelSource)
    assertTrue(modelSource.contains("public let createdAt: Date"), modelSource)
    assertTrue(modelSource.contains("public let calendarDay: Date"), modelSource)
    assertTrue(modelSource.contains("public let localTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let localDateTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let email: String"), modelSource)
    assertTrue(serviceSource.contains("resourceId: UUID"), serviceSource)
    assertTrue(serviceSource.contains("callbackUrl: URL? = nil"), serviceSource)
    assertTrue(serviceSource.contains("location: URL"), serviceSource)
  }

  @Test
  fun `adds Identifiable to IR models with id when default identifiable option is enabled`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      source.contains("public struct User : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      source,
    )
  }

  @Test
  fun `adds Identifiable to IR models with camel or acronym id suffixes`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "UserRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "TeamRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("teamID", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "NotIdentifiable",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userid", GeneratedTypeRef.scalar("string"), required = true)),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userRefSource =
      buildString {
        FileSpec
          .get("", findType("UserRef", builtTypes))
          .writeTo(this)
      }
    val teamRefSource =
      buildString {
        FileSpec
          .get("", findType("TeamRef", builtTypes))
          .writeTo(this)
      }
    val notIdentifiableSource =
      buildString {
        FileSpec
          .get("", findType("NotIdentifiable", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      userRefSource.contains("public struct UserRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      userRefSource,
    )
    assertTrue(userRefSource.contains("public var id: String"), userRefSource)
    assertTrue(userRefSource.contains("return self.userId"), userRefSource)
    assertTrue(
      teamRefSource.contains("public struct TeamRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      teamRefSource,
    )
    assertTrue(teamRefSource.contains("return self.teamID"), teamRefSource)
    assertFalse(
      notIdentifiableSource.contains(
        "public struct NotIdentifiable : Codable, CustomDebugStringConvertible, Sendable, Identifiable",
      ),
      notIdentifiableSource,
    )
  }

  @Test
  fun `filters inherited properties from Swift model subclasses`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = false),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                ),
            ),
            GeneratedModel(
              name = "BadRequest",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Conflict",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                ),
            ),
            GeneratedModel(
              name = "GraphsProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "RepoNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("GraphsProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("", findType("HttpProblem", builtTypes))
          .writeTo(this)
      }
    val source =
      buildString {
        FileSpec
          .get("", findType("BadRequest", builtTypes))
          .writeTo(this)
      }
    val graphsSource =
      buildString {
        FileSpec
          .get("", findType("GraphsProblem", builtTypes))
          .writeTo(this)
      }
    val repoNotFoundSource =
      buildString {
        FileSpec
          .get("", findType("RepoNotFoundProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(rootSource.contains("public protocol HttpProblem : Problem"), rootSource)
    assertFalse(rootSource.contains("public class HttpProblem"), rootSource)
    assertTrue(source.contains("public struct BadRequest : HttpProblem"), source)
    assertTrue(source.contains("public let type: URL"), source)
    assertTrue(source.contains("public let title: String"), source)
    assertTrue(source.contains("public let status: Int"), source)
    assertTrue(source.contains("public let code: String"), source)
    assertTrue(graphsSource.contains("public protocol GraphsProblem : Problem"), graphsSource)
    assertTrue(graphsSource.contains("var code: String { get }"), graphsSource)
    assertTrue(repoNotFoundSource.contains("public struct RepoNotFoundProblem : GraphsProblem"), repoNotFoundSource)
    assertFalse(repoNotFoundSource.contains("override"), repoNotFoundSource)
    assertTrue(repoNotFoundSource.contains("public let parameters: [String : AnyValue]?"), repoNotFoundSource)
  }

  @Test
  fun `generates sendable request body types for inherited and discriminated models`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "NarrativeService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "updateScript",
                    method = "PATCH",
                    path = "/scripts/{scriptId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("ScriptUpdate")),
                  ),
                  GeneratedOperation(
                    id = "updateEntity",
                    method = "PATCH",
                    path = "/entities/{entityId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("EntityUpdate")),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Update",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ScriptUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Update")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "EntityKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("scene"),
            ),
            GeneratedModel(
              name = "EntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("EntityKind"), required = true),
                ),
            ),
            GeneratedModel(
              name = "SceneEntityUpdate",
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }
    val scriptUpdateSource =
      buildString {
        FileSpec
          .get("", findType("ScriptUpdate", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntityUpdateRef", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      serviceSource.contains(
        "public func updateScript(body: ScriptUpdate) throws -> Sunday.Operation<ScriptUpdate, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      serviceSource.contains(
        "public func updateEntity(body: EntityUpdateRef) throws -> Sunday.Operation<EntityUpdateRef, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      scriptUpdateSource.contains("public struct ScriptUpdate : Codable, CustomDebugStringConvertible, Sendable"),
      scriptUpdateSource,
    )
    assertTrue(scriptUpdateSource.contains("public let version: Int"), scriptUpdateSource)
    assertTrue(scriptUpdateSource.contains("public let title: String?"), scriptUpdateSource)
    assertTrue(
      entityRefSource.contains("public enum EntityUpdateRef : Codable, CustomDebugStringConvertible, Sendable"),
    )
  }

  @Test
  fun `qualifies Sunday Problem when generated model has same name`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Problem",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "code",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "detail",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = false,
                  ),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "NarrativeProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Problem")),
              properties =
                listOf(
                  GeneratedModelProperty("narrativeId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val problemSource =
      buildString {
        FileSpec
          .get("", findType("Problem", builtTypes))
          .writeTo(this)
      }
    val narrativeProblemSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(problemSource.contains("public protocol Problem : Sunday.Problem"), problemSource)
    assertTrue(narrativeProblemSource.contains("public struct NarrativeProblem : Problem"), narrativeProblemSource)
  }

  @Test
  fun `uses reference models for recursive Swift object graphs`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Recursive API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EntityType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("location", "character"),
            ),
            GeneratedModel(
              name = "EntitySummary",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityType"), required = true),
                ),
            ),
            GeneratedModel(
              name = "LocationSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "location",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "parent",
                    GeneratedTypeRef.named("LocationSummary").copy(nullable = true),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "CharacterSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "character",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EntityList",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "items",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.named("EntitySummary")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "EntityMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.named("EntitySummary")),
            ),
            GeneratedModel(
              name = "EntityMapHolder",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("entries", GeneratedTypeRef.named("EntityMap"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val locationSource =
      buildString {
        FileSpec
          .get("", findType("LocationSummary", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntitySummaryRef", builtTypes))
          .writeTo(this)
      }
    val listSource =
      buildString {
        FileSpec
          .get("", findType("EntityList", builtTypes))
          .writeTo(this)
      }
    val mapHolderSource =
      buildString {
        FileSpec
          .get("", findType("EntityMapHolder", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(locationSource.contains("public final class LocationSummary : EntitySummary"), locationSource)
    assertTrue(locationSource.contains("public let parent: LocationSummary?"), locationSource)
    assertTrue(locationSource.contains("parent: LocationSummary? = nil"), locationSource)
    assertTrue(
      entityRefSource.contains("let type = try container.decode(String.self, forKey: CodingKeys.type)"),
      entityRefSource,
    )
    assertTrue(entityRefSource.contains("case \"location\":"), entityRefSource)
    assertTrue(listSource.contains("public let items: [EntitySummaryRef]"), listSource)
    assertTrue(mapHolderSource.contains("public let entries: [String : EntitySummaryRef]"), mapHolderSource)
  }

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
    assertTrue(enumSource.contains("public enum Status : String, CaseIterable, Codable"), enumSource)
    assertTrue(enumSource.contains("case `open` = \"OPEN\""), enumSource)
    assertTrue(enumSource.contains("case pullRequestOpen = \"PULL_REQUEST_OPEN\""), enumSource)
    assertTrue(enumSource.contains("case lowerSnakeCase = \"lower_snake_case\""), enumSource)
    assertTrue(enumSource.contains("case mixedKebabCase = \"mixed-kebab.case\""), enumSource)
    assertTrue(containerSource.contains("public let status: Status"), containerSource)
    assertTrue(containerSource.contains("public let alias: String"), containerSource)
    assertTrue(containerSource.contains("public let list: [String]"), containerSource)
    assertTrue(containerSource.contains("public let set: Set<String>"), containerSource)
    assertTrue(containerSource.contains("public let map: [String : String]"), containerSource)
    assertTrue(containerSource.contains("public let union: AnyValue"), containerSource)
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
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("UsersAPI", builtTypes))
          .writeTo(this)
      }
    val unionSource =
      buildString {
        FileSpec
          .get("", findType("UserProfile", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
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
    assertTrue(unionSource.contains("let container = try decoder.container(keyedBy: CodingKeys.self)"), unionSource)
    assertTrue(unionSource.contains("let keys = container.allKeys"), unionSource)
    assertTrue(unionSource.contains("if keys.contains(.createdAt) || keys.contains(.teams)"), unionSource)
    assertTrue(unionSource.contains("self = .userSelfResponse(try UserSelfResponse(from: decoder))"), unionSource)
    assertTrue(
      unionSource.contains("if keys.contains(.userId) && keys.contains(.email) && keys.contains(.displayName)"),
      unionSource,
    )
    assertTrue(unionSource.contains("self = .userSummaryResponse(try UserSummaryResponse(from: decoder))"), unionSource)
    assertTrue(unionSource.contains("DecodingError.typeMismatch(Self.self"), unionSource)
    assertTrue(unionSource.contains("case .userSelfResponse(let value):"), unionSource)
  }

  @Test
  fun `generates nested shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Nested API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Container",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("child", GeneratedTypeRef.named("Child"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Child",
              kind = GeneratedModel.Kind.OBJECT,
              nested =
                GeneratedNestedType(
                  enclosedIn = GeneratedTypeRef.named("Container"),
                  name = "Child",
                ),
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Container", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let child: Child"), source)
    assertTrue(source.contains("public struct Child"), source)
    assertTrue(source.contains("public let value: String"), source)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: SwiftCompiler) {
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val typeRegistry = SwiftTypeRegistry(setOf())
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
              targets = mapOf("swift" to GeneratedTarget(typeName = "MainTest")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("swift" to GeneratedTarget(typeName = "LibraryTest")),
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let main: MainTest"), source)
    assertTrue(source.contains("public let library: LibraryTest"), source)
  }

  @Test
  fun `generates patchable shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Patch API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "PatchModel",
              kind = GeneratedModel.Kind.OBJECT,
              patchable = true,
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "nullable",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("PatchModel", builtTypes)
    val source =
      buildString {
        FileSpec
          .builder("", typeSpec.name)
          .addType(typeSpec)
          .apply {
            typeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public struct PatchModel"), source)
    assertTrue(source.contains("Sendable"), source)
    assertTrue(source.contains("public let value: UpdateOp<String>?"), source)
    assertTrue(source.contains("public let nullable: PatchOp<String>?"), source)
    assertTrue(source.contains("value: UpdateOp<String>? = .none"), source)
    assertTrue(source.contains("nullable: PatchOp<String>? = .none"), source)
    assertTrue(source.contains("self.value = try container.decodeIfExists(String.self, forKey: .value)"), source)
    assertTrue(source.contains("try container.encodeIfExists(self.value, forKey: .value)"), source)
    assertTrue(source.contains("extension AnyPatchOp where Value == PatchModel"), source)
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
    assertTrue(unionSource.contains("let discriminatorValue = object[\"code\"]?.unwrapped as? String"), unionSource)
    assertTrue(unionSource.contains("if discriminatorValue == \"TPG-REPO-404\""), unionSource)
    assertTrue(
      unionSource.contains("AnyValueDecoder.default.decode(RepoNotFoundProblem.self, from: value)"),
      unionSource,
    )
    assertTrue(unionSource.contains("if discriminatorValue == \"TPG-WG-404\""), unionSource)
    assertFalse(unionSource.contains("object[\"type\"] != nil"), unionSource)
  }

  @Test
  fun `generates externally discriminated shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "External Discriminator API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Parent",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              externallyDiscriminated = true,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Cat",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "cat",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Dog",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "dog",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Envelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "payload",
                    GeneratedTypeRef.named("Parent"),
                    required = true,
                    externalDiscriminator = "kind",
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentSource =
      buildString {
        FileSpec
          .get("", findType("Parent", builtTypes))
          .writeTo(this)
      }
    val envelopeSource =
      buildString {
        FileSpec
          .get("", findType("Envelope", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(parentSource.contains("public protocol Parent"), parentSource)
    assertFalse(parentSource.contains("enum AnyRef"), parentSource)
    assertTrue(envelopeSource.contains("public struct Envelope"), envelopeSource)
    assertTrue(envelopeSource.contains("public let payload: Parent"), envelopeSource)
    assertTrue(envelopeSource.contains("switch self.kind"), envelopeSource)
    assertTrue(envelopeSource.contains("case \"cat\":"), envelopeSource)
    assertTrue(
      envelopeSource.contains("self.payload = try container.decode(Cat.self, forKey: .payload)"),
      envelopeSource,
    )
    assertTrue(envelopeSource.contains("case \"dog\":"), envelopeSource)
    assertTrue(envelopeSource.contains("try container.encode(self.payload as! Cat, forKey: .payload)"), envelopeSource)
  }

  @Test
  fun `generates inherited discriminated model snapshots from IR with existing Swift output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertTrue(compileTypes(compiler, builtTypes))
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output.swift",
      buildString {
        FileSpec
          .get("", findType("Parent", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output2.swift",
      buildString {
        FileSpec
          .get("", findType("Child1", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output3.swift",
      buildString {
        FileSpec
          .get("", findType("Child2", builtTypes))
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-basic-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates inherited path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-inherited-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional query parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestQueryParamsTest/test-optional-query-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates constant headers from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestHeaderParamsTest/test-constant-header-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates mixed inline parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMixedParamsTest/test-generation-of-multiple-parameters-with-inline-type-definitions.output.swift",
    )
  }

  @Test
  fun `generates explicit security parameters from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-explicit-security-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "SwiftSundayIrGeneratorTest/test-explicit-security-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-basic-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-optional-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates explicit request body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-generation-of-body-parameter-with-explicit-content-type.output.swift",
    )
  }

  @Test
  fun `generates explicit response body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates polymorphic response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates inline response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates no content response from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-client-mode.output.swift",
    )
  }

  @Test
  fun `generates nullify methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation-with-nullify.output.swift",
    )
  }

  @Test
  fun `registers referenced problems from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseProblemsTest/test-api-problem-registration.output.swift",
    )
  }

  @Test
  fun `generates referenced problem types directly from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertTrue(compileTypes(compiler, builtTypes))
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "CreateFailedProblem" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "TestNotFoundProblem" })
    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates event source methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-source-method.output.swift",
    )
  }

  @Test
  fun `generates event stream methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-stream-method-generation.output.swift",
    )
  }

  @Test
  fun `generates common-base event stream methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-stream-method-generation-for-common-base-events.output.swift",
    )
  }

  @Test
  fun `generates base URL companion from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BaseUriTest/test-baseurl-generation-in-api.output.swift",
    )
  }

  @Test
  fun `generates request builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-request-builder-method-generation.output.swift",
    )
  }

  @Test
  fun `generates response builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-response-builder-method-generation.output.swift",
    )
  }

  private fun assertIrServiceSnapshot(
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

  private fun generateSwiftSundayFiles(
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
