/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.swift.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

internal fun reusableDiscriminatorMappingApi(): GeneratedApi =
  GeneratedApi(
    name = "Events API",
    source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
    services =
      listOf(
        GeneratedService(
          name = "EventsService",
          operations =
            listOf(
              GeneratedOperation(
                id = "getNotificationEvent",
                method = "GET",
                path = "/notification-event",
                responses =
                  listOf(
                    GeneratedResponse(
                      status = 200,
                      type = GeneratedTypeRef.named("NotificationEventEnvelope"),
                      mediaTypes = listOf("application/json"),
                    ),
                  ),
              ),
              GeneratedOperation(
                id = "streamNotificationEvents",
                method = "GET",
                path = "/notification-events",
                responses =
                  listOf(
                    GeneratedResponse(
                      status = 200,
                      type = GeneratedTypeRef.named("NotificationEventEnvelope"),
                      mediaTypes = listOf("text/event-stream"),
                    ),
                  ),
                streaming = GeneratedStreaming(GeneratedStreaming.Kind.EVENT_STREAM),
              ),
            ),
        ),
      ),
    models =
      listOf(
        GeneratedModel(
          name = "EventType",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("event.one", "unrecognized"),
          unknownValue = "unrecognized",
        ),
        GeneratedModel(
          name = "EventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty("type", GeneratedTypeRef.named("EventType"), required = true),
            ),
          discriminator = "type",
          discriminatorMappings = mapOf("event.one" to GeneratedTypeRef.named("EventOne")),
        ),
        GeneratedModel(
          name = "EventOne",
          kind = GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("EventEnvelope")),
          discriminatorValue = "event.one",
          properties =
            listOf(
              GeneratedModelProperty("data", GeneratedTypeRef.scalar("string"), required = true),
            ),
        ),
        GeneratedModel(
          name = "NotificationEventType",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("event.one", "unrecognized"),
          unknownValue = "unrecognized",
        ),
        GeneratedModel(
          name = "NotificationEventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "type",
                GeneratedTypeRef.named("NotificationEventType"),
                required = true,
              ),
            ),
          discriminator = "type",
          discriminatorMappings = mapOf("event.one" to GeneratedTypeRef.named("EventOne")),
        ),
        GeneratedModel(
          name = "Notification",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "event",
                GeneratedTypeRef.named("NotificationEventEnvelope"),
                required = true,
              ),
            ),
        ),
      ),
  )

internal val reusableDiscriminatorMappingRuntimeTest: String =
  """
  import Foundation
  import XCTest
  @testable import SundayGenTest

  final class ReusableDiscriminatorMappingTests: XCTestCase {

    func testKnownAndUnknownMappingsRoundTrip() throws {
      let decoder = JSONDecoder()
      let encoder = JSONEncoder()

      let knownJSON = Data(#"{"event":{"type":"event.one","data":"known"}}"#.utf8)
      let known = try decoder.decode(Notification.self, from: knownJSON)
      guard case .eventOne(let event) = known.event else {
        return XCTFail("Expected canonical event.one mapping")
      }
      XCTAssertEqual(event.data, "known")
      try XCTAssertJSONEqual(encoder.encode(known), knownJSON)

      let unknownJSON = Data(#"{"event":{"type":"future.event","detail":"preserved"}}"#.utf8)
      let unknown = try decoder.decode(Notification.self, from: unknownJSON)
      guard case .unrecognized(let event) = unknown.event else {
        return XCTFail("Expected tolerant discriminator fallback")
      }
      XCTAssertEqual(event.type.rawValue, "future.event")
      try XCTAssertJSONEqual(encoder.encode(unknown), unknownJSON)
    }

    private func XCTAssertJSONEqual(
      _ actual: Data,
      _ expected: Data,
      file: StaticString = #filePath,
      line: UInt = #line
    ) throws {
      let actualObject = try JSONSerialization.jsonObject(with: actual) as? NSDictionary
      let expectedObject = try JSONSerialization.jsonObject(with: expected) as? NSDictionary
      XCTAssertEqual(actualObject, expectedObject, file: file, line: line)
    }
  }
  """.trimIndent()
