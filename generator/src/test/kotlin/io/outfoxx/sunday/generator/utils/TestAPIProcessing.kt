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

package io.outfoxx.sunday.generator.utils

import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.Shape
import com.damnhandy.uri.template.UriTemplate
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.common.ShapeIndex
import org.junit.jupiter.api.fail
import java.net.URI

object TestAPIProcessing : APIProcessor() {

  override fun process(uri: URI): Result {

    val result = super.process(uri)

    if (!result.isValid) {
      val log =
        result.validationLog.joinToString("\n") { entry ->
          "${entry.file}:${entry.line}: ${entry.message}"
        }

      fail("Invalid file\n$log")
    }

    return result
  }

  fun generateTypes(
    document: Document,
    shapeIndex: ShapeIndex,
    mode: GenerationMode,
    problemTypeHandler: (String, ProblemTypeDefinition, ShapeIndex) -> Unit,
    typeHandler: (name: String, shape: Shape) -> Unit
  ) {

    document.api.endPoints.forEach { endPoint ->

      endPoint.operations.forEach { operation ->

        val opName = (operation.operationId ?: operation.name)?.toUpperCamelCase() ?: ""

        operation.requests.forEach { request ->
          request.uriParameters.forEach { param -> typeHandler("${opName}${param.name}UriParam", param.schema!!) }
          request.queryParameters.forEach { param -> typeHandler("${opName}${param.name}QueryParam", param.schema!!) }
          request.cookieParameters.forEach { param -> typeHandler("${opName}${param.name}CookieParam", param.schema!!) }
          request.headers.forEach { param -> typeHandler("${opName}${param.name}RequestHeader", param.schema!!) }
          request.payloads.forEachIndexed { index, payload ->
            typeHandler(
              "${opName}Request${payload.name ?: "$index"}Payload",
              payload.schema!!
            )
          }
          request.queryString?.let { queryString -> typeHandler("${opName}QueryString", queryString) }
        }

        operation.responses.forEach { response ->
          response.headers.forEach { header -> typeHandler("${opName}${header.name}ResponseHeader", header.schema!!) }
          response.payloads.forEachIndexed { index, payload ->
            typeHandler(
              "${opName}Response${payload.name ?: "$index"}Payload",
              payload.schema!!
            )
          }
        }
      }
    }

    val baseUri = document.api.servers.firstOrNull()?.url ?: "http://example.com/"
    val problemBaseUri =
      UriTemplate.buildFromTemplate(baseUri)
        .template(document.api.findAnnotation(APIAnnotationName.ProblemBaseUri, mode)?.stringValue ?: "")
        .build()
        .expand()

    val problemTypesAnn =
      document.api.findAnnotation(APIAnnotationName.ProblemTypes, mode) as? ObjectNode
    problemTypesAnn?.properties()?.forEach { (problemCode, problemDef) ->
      val problemType =
        ProblemTypeDefinition(problemCode, problemDef as ObjectNode, URI(problemBaseUri), document, problemDef)
      problemTypeHandler(problemCode, problemType, shapeIndex)
    }
  }
}
