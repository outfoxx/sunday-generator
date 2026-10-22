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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GeneratedDocumentUri
import java.net.URI

/** Traverses all bound reference positions, preserving each declaration's lexical document context. */
internal class GeneratedTypeReferenceRewriter(
  private val source: GeneratedSourceSpec?,
  private val transform: (GeneratedTypeRef, GeneratedSourceSpec?) -> GeneratedTypeRef,
  private val modelName: (String) -> String = { it },
) {
  fun rewrite(api: GeneratedApi): GeneratedApi =
    api.copy(
      models = api.models.map { inSource(it.source).model(it) },
      servers = api.servers.map(::server),
      protocol = api.protocol?.let(::protocol),
      services = api.services.map(::service),
      problems =
        api.problems.map { problem ->
          inSource(problem.source).problem(problem)
        },
      auth = api.auth?.let(::auth),
    )

  fun problem(problem: GeneratedProblem): GeneratedProblem =
    problem.copy(
      model = problem.model?.let(::type),
      fields = problem.fields.map(::field),
      payload = problem.payload?.let { it.copy(type = type(it.type), fields = it.fields.map(::field)) },
    )

  private fun type(reference: GeneratedTypeRef): GeneratedTypeRef =
    transform(reference.copy(arguments = reference.arguments.map(::type)), source)

  private fun inSource(owner: GeneratedSourceSpec?) = GeneratedTypeReferenceRewriter(owner, transform, modelName)

  private fun inOperation(operation: GeneratedOperation): GeneratedTypeReferenceRewriter =
    inSource(
      operation.declarationUri?.let { uri ->
        source?.copy(location = GeneratedDocumentUri.canonical(URI(uri)).toString())
      }
        ?: source,
    )

  private fun field(property: GeneratedModelProperty) = property.copy(type = type(property.type))

  private fun parameter(parameter: GeneratedParameter) = parameter.copy(type = type(parameter.type))

  private fun option(option: GeneratedPayloadOption) = option.copy(type = type(option.type))

  fun model(model: GeneratedModel): GeneratedModel =
    model.copy(
      name = modelName(model.name),
      properties = model.properties.map(::field),
      inherits = model.inherits.map(::type),
      aliases = model.aliases.map(::type),
      patchOf = model.patchOf?.let(::type),
      additionalProperties = model.additionalProperties?.let { it.copy(type = it.type?.let(::type)) },
      patternProperties = model.patternProperties.map { it.copy(type = type(it.type)) },
      discriminatorMappings = model.discriminatorMappings.mapValues { type(it.value) },
      nested = model.nested?.let { it.copy(enclosedIn = it.enclosedIn?.let(::type)) },
      placementDeclarations = model.placementDeclarations.map { inSource(it.source).model(it) },
    )

  private fun protocol(protocol: GeneratedProtocol): GeneratedProtocol =
    protocol.copy(servers = protocol.servers.map(::server))

  private fun server(server: GeneratedServer): GeneratedServer =
    server.copy(variables = server.variables.map(::parameter), auth = server.auth?.let(::auth))

  fun service(service: GeneratedService): GeneratedService =
    service.copy(
      protocol = service.protocol?.let(::protocol),
      baseUriParameters = service.baseUriParameters.map(::parameter),
      servers = service.servers.map(::server),
      operations = service.operations.map { inOperation(it).operation(it) },
      auth = service.auth?.let(::auth),
    )

  fun operation(operation: GeneratedOperation): GeneratedOperation =
    operation.copy(
      placementDeclarations = operation.placementDeclarations.map { inOperation(it).operation(it) },
      protocol = operation.protocol?.let(::protocol),
      parameters = operation.parameters.map(::parameter),
      queryString = operation.queryString?.let(::type),
      requestBody =
        operation.requestBody?.let {
          it.copy(
            type = type(it.type),
            payloads = it.payloads.map(::option),
          )
        },
      responses =
        operation.responses.map { response ->
          response.copy(
            type = response.type?.let(::type),
            payloads = response.payloads.map(::option),
            headers = response.headers.map(::parameter),
          )
        },
      problems = operation.problems.map(::type),
      nullify = operation.nullify?.let { it.copy(problems = it.problems.map(::type)) },
      auth = operation.auth?.let(::auth),
      serverAuth = operation.serverAuth.mapValues { (_, value) -> auth(value) },
    )

  private fun auth(auth: GeneratedAuth): GeneratedAuth =
    auth.copy(
      securitySchemes =
        auth.securitySchemes.map { scheme ->
          scheme.copy(
            headers = scheme.headers.map(::parameter),
            queryParameters = scheme.queryParameters.map(::parameter),
            cookieParameters = scheme.cookieParameters.map(::parameter),
            queryString = scheme.queryString?.let(::type),
          )
        },
    )
}
