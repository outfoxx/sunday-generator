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

import amf.apicontract.client.platform.model.domain.Callback
import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Request
import amf.apicontract.client.platform.model.domain.Response
import amf.apicontract.client.platform.model.domain.Server
import amf.apicontract.client.platform.model.domain.TemplatedLink
import amf.apicontract.client.platform.model.domain.api.WebApi
import amf.apicontract.client.platform.model.domain.security.ParametrizedSecurityScheme
import amf.apicontract.client.platform.model.domain.security.SecurityRequirement
import amf.apicontract.client.platform.model.domain.security.SecurityScheme
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.DeclaresModel
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.Shape

abstract class ShapeVisitor {

  protected abstract fun visit(shape: Shape?)

  protected fun visit(unit: BaseUnit) {

    unit.references.forEach { visit(it) }

    if (unit is DeclaresModel) {

      unit.declares.forEach { declared ->

        if (declared is Shape) {
          visit(declared)
        }
      }
    }

    val model = unit as? EncodesModel
    if (model != null) {

      val api = model.encodes as? WebApi

      api?.servers?.forEach { server -> visit(server) }
      api?.security?.forEach { securityRequirement -> visit(securityRequirement) }
      api?.endPoints?.forEach { endPoint -> visit(endPoint) }
    }
  }

  protected fun visit(endPoint: EndPoint) {
    endPoint.operations.forEach { operation -> visit(operation) }
    endPoint.parameters.forEach { parameter -> visit(parameter.schema) }
    endPoint.payloads.forEach { payload -> visit(payload.schema) }
    endPoint.servers.forEach { server -> visit(server) }
    endPoint.security.forEach { securityRequirement -> visit(securityRequirement) }
  }

  protected fun visit(operation: Operation) {
    operation.request?.let { request -> visit(request) }
    operation.requests.forEach { request -> visit(request) }
    operation.responses.forEach { response -> visit(response) }
    operation.security.forEach { securityRequirement -> visit(securityRequirement) }
    operation.callbacks.forEach { callback -> visit(callback) }
    operation.servers.forEach { server -> visit(server) }
  }

  protected fun visit(request: Request) {
    request.uriParameters.forEach { param -> visit(param.schema) }
    request.queryParameters.forEach { param -> visit(param.schema) }
    request.cookieParameters.forEach { param -> visit(param.schema) }
    request.headers.forEach { param -> visit(param.schema) }
    request.payloads.forEach { payload -> visit(payload.schema) }
    request.queryString?.let { queryString -> visit(queryString) }
  }

  protected fun visit(response: Response) {
    response.headers.forEach { header -> visit(header.schema) }
    response.payloads.forEach { payload -> visit(payload.schema) }
    response.links.forEach { templatedLink -> visit(templatedLink) }
  }

  protected fun visit(templatedLink: TemplatedLink) {
    templatedLink.server?.let { server -> visit(server) }
  }

  protected fun visit(callback: Callback) {
    callback.endPoint?.let { endPoint -> visit(endPoint) }
  }

  protected fun visit(server: Server) {
    server.variables.forEach { serverVar -> visit(serverVar.schema) }
    server.security.forEach { securityRequirement -> visit(securityRequirement) }
  }

  protected fun visit(securityRequirement: SecurityRequirement) {
    securityRequirement.schemes.forEach { parametrizedSecurityScheme -> visit(parametrizedSecurityScheme) }
  }

  protected fun visit(scheme: ParametrizedSecurityScheme) {
    visit(scheme.scheme)
  }

  protected fun visit(scheme: SecurityScheme) {
    scheme.headers?.forEach { header -> visit(header.schema) }
    scheme.queryParameters?.forEach { header -> visit(header.schema) }
    scheme.responses?.forEach { response -> visit(response) }
    scheme.queryString?.let { queryString -> visit(queryString) }
  }
}
