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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService

/**
 * Default request and response media selected for a generated service.
 */
data class GeneratedMediaSelection(
  val contentTypes: List<String>,
  val acceptTypes: List<String>,
) {

  companion object {

    /** Server-sent event stream media type. */
    const val EVENT_STREAM = "text/event-stream"
  }
}

/**
 * Returns the primary success response for operation return-type and accept negotiation.
 */
fun GeneratedOperation.primarySuccessResponse(): GeneratedResponse? =
  responses.firstOrNull { response -> response.status == null || response.status in 200..299 }

/**
 * True when the response is HTTP 204 No Content.
 */
val GeneratedResponse.isNoContent: Boolean
  get() = status == 204

/**
 * Orders source-declared media types with target preference media types first.
 */
fun GeneratedApi.orderedDefaultMediaTypes(preferredMediaTypes: List<String>): List<String> {
  val ordered = linkedSetOf<String>()
  val sourceMediaTypes = (media?.request.orEmpty() + media?.response.orEmpty()).distinct()

  preferredMediaTypes
    .filter(sourceMediaTypes::contains)
    .forEach(ordered::add)

  sourceMediaTypes.forEach(ordered::add)

  return ordered.toList()
}

/**
 * Selects the service-level default content and accept media from operation references.
 */
fun GeneratedService.defaultMediaSelection(
  defaultMediaTypes: List<String>,
  eventStreamMediaType: String = GeneratedMediaSelection.EVENT_STREAM,
): GeneratedMediaSelection {
  val referencedContentTypes = linkedSetOf<String>()
  val referencedAcceptTypes = linkedSetOf<String>()

  operations.forEach { operation ->
    operation.requestBody
      ?.mediaTypes
      .orEmpty()
      .forEach(referencedContentTypes::add)

    val responseMediaTypes =
      if (operation.streaming != null) {
        listOf(eventStreamMediaType)
      } else {
        operation
          .primarySuccessResponse()
          ?.mediaTypes
          .orEmpty()
      }

    if (responseMediaTypes.isEmpty()) {
      referencedAcceptTypes.addAll(defaultMediaTypes)
    } else {
      referencedAcceptTypes.addAll(responseMediaTypes)
    }
  }

  return GeneratedMediaSelection(
    contentTypes = defaultMediaTypes.filter(referencedContentTypes::contains),
    acceptTypes = defaultMediaTypes.filter(referencedAcceptTypes::contains),
  )
}

/**
 * Returns explicit content types when a payload cannot use the service default content types.
 */
fun GeneratedPayload.explicitContentTypes(defaultMediaTypes: List<String>): List<String>? {
  val contentType = mediaTypes.firstOrNull()
  return if (contentType != null && contentType !in defaultMediaTypes) {
    listOf(contentType)
  } else {
    null
  }
}

/**
 * Returns explicit accept types when a response cannot use the service default accept types.
 */
fun GeneratedResponse?.explicitAcceptTypes(defaultMediaTypes: List<String>): List<String>? {
  val responseMediaTypes = this?.mediaTypes.orEmpty()
  return if (responseMediaTypes != defaultMediaTypes) {
    responseMediaTypes
  } else {
    null
  }
}
