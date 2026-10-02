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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Events Test")
@Tag("events")
class SwiftSundayIrEventsTest : SwiftSundayIrTestSupport() {

  @Test
  @Tag("requests")
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
    assertTrue(compileTypes(compiler, builtTypes))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "EventsAPI.swift")
    assertTrue(source.contains("public func streamProjectEvents("), source)
    assertTrue(source.contains("subscriberId: String"), source)
    assertTrue(source.contains("lastEventID: String? = nil"), source)
    assertTrue(source.contains("queryParameters: ["), source)
    assertTrue(source.contains("\"subscriberId\": try! ParameterValues.encode(subscriberId)"), source)
    assertTrue(source.contains("headers: ["), source)
    assertTrue(source.contains("\"Last-Event-ID\": try! ParameterValues.encode(lastEventID)"), source)
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
  @Tag("models")
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
    assertTrue(eventEnvelopeSource.contains("return .projectCreatedData(value.data)"), eventEnvelopeSource)
    assertTrue(
      eventDataSource.contains("public enum EventData : Codable, CustomDebugStringConvertible, Sendable"),
      eventDataSource,
    )
    assertTrue(eventDataSource.contains("case projectCreatedData(ProjectCreatedData)"), eventDataSource)
    assertTrue(projectCreatedSource.contains("public struct ProjectCreatedData : Codable"), projectCreatedSource)
  }

  @Test
  @Tag("models")
  fun `generates direct AsyncAPI discriminated event object unions from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/direct-discriminated-event-union.yaml") asyncApiUri: URI,
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
    val accountsTeamCreatedEventSource =
      buildString {
        FileSpec
          .get("", findType("AccountsTeamCreatedEvent", builtTypes))
          .writeTo(this)
      }
    val notificationEventSource =
      buildString {
        FileSpec
          .get("", findType("NotificationsAnnouncementPublishedEvent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "AccountsTeamCreatedData" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "NotificationAnnouncementPublishedData" })
    assertTrue(
      eventEnvelopeSource.contains("public enum EventEnvelope : Codable, CustomDebugStringConvertible, Sendable"),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains("case accountsTeamCreatedEvent(AccountsTeamCreatedEvent)"),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains(
        "case notificationsAnnouncementPublishedEvent(NotificationsAnnouncementPublishedEvent)",
      ),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains(
        "EventEnvelopeValidation.isValid(normalized: context.originalValue!, .response, context: &context)",
      ),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("switch context.selectedAlternative"), eventEnvelopeSource)
    assertTrue(
      eventEnvelopeSource.contains("self = .accountsTeamCreatedEvent(try AccountsTeamCreatedEvent(from: decoder))"),
      eventEnvelopeSource,
    )
    assertFalse(
      eventEnvelopeSource.contains("AnyValueDecoder.default.decode(AccountsTeamCreatedEvent.self, from: value)"),
      eventEnvelopeSource,
    )
    assertTrue(accountsTeamCreatedEventSource.contains("public let id: String"), accountsTeamCreatedEventSource)
    assertTrue(accountsTeamCreatedEventSource.contains("public let occurredAt: Date"), accountsTeamCreatedEventSource)
    assertTrue(
      accountsTeamCreatedEventSource.contains("public let data: AccountsTeamCreatedData"),
      accountsTeamCreatedEventSource,
    )
    assertTrue(notificationEventSource.contains("public let id: String"), notificationEventSource)
    assertTrue(notificationEventSource.contains("public let occurredAt: Date"), notificationEventSource)
    assertTrue(
      notificationEventSource.contains("public let data: NotificationAnnouncementPublishedData"),
      notificationEventSource,
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
}
