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

package io.outfoxx.sunday.generator.swift.tools

import amf.MessageStyles
import amf.ProfileNames
import amf.client.AMF
import amf.client.environment.Environment
import amf.client.model.document.Document
import amf.client.model.domain.ObjectNode
import amf.client.parse.Raml10Parser
import com.damnhandy.uri.template.UriTemplate
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUri
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemTypes
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.swift.SwiftGenerator
import io.outfoxx.sunday.generator.swift.SwiftResolutionContext
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.utils.LocalResourceLoader
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.cookieParameters
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.operationId
import io.outfoxx.sunday.generator.utils.operations
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.queryParameters
import io.outfoxx.sunday.generator.utils.queryString
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.responses
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.sunday.generator.utils.servers
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uriParameters
import io.outfoxx.sunday.generator.utils.url
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.TypeSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.net.URI
import kotlin.system.exitProcess

fun parseAndValidate(uri: URI): Document {

  val parentUri = uri.resolve(".")

  AMF.init().get()

  val environment = Environment().addClientLoader(LocalResourceLoader)
  val document = Raml10Parser(environment).parseFileAsync(uri.toString()).get() as Document
  val validation = AMF.validate(document.cloneUnit(), ProfileNames.RAML10(), MessageStyles.RAML()).get()

  if (!validation.conforms()) {

    validation.results().forEach { result ->

      val locationURI = result.location().orElse(null)?.let { URI(it) }
      val location = locationURI?.let { parentUri.relativize(it) }?.toASCIIString() ?: "unknown"
      val line = result.position().start().line()

      System.err.println("$location:$line: ${result.message()}")
    }

    exitProcess(1)
  }

  return document
}

fun findType(name: String, types: Map<DeclaredTypeName, TypeSpec>): TypeSpec =
  types[DeclaredTypeName.typeName(".$name")] ?: fail("Type '$name' not defined")

fun generateTypes(
  uri: URI,
  typeRegistry: SwiftTypeRegistry,
  compiler: SwiftCompiler
): Map<DeclaredTypeName, TypeSpec> {

  val document = parseAndValidate(uri)

  val apiTypeName = DeclaredTypeName.typeName(".API")
  typeRegistry.addServiceType(apiTypeName, TypeSpec.classBuilder(apiTypeName))

  document.api.endPoints.forEach { endPoint ->

    endPoint.operations.forEach { operation ->

      val opName = (operation.operationId ?: operation.name!!).toUpperCamelCase()

      operation.requests.forEach { request ->

        request.uriParameters.forEach { param ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}UriParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.queryParameters.forEach { param ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}QueryParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.cookieParameters.forEach { param ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}CookieParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.headers.forEach { header ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}RequestHeaderParams"))
          typeRegistry.resolveTypeName(header.schema!!, context)
        }

        request.payloads.forEach { payload ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}Payload"))
          typeRegistry.resolveTypeName(payload.schema!!, context)
        }
        val queryString = request.queryString
        if (queryString != null) {
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}QueryStringParams"))
          typeRegistry.resolveTypeName(queryString, context)
        }
      }

      operation.responses.forEach { response ->

        response.headers.forEach { header ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}ResponseHeaderParams"))
          typeRegistry.resolveTypeName(header.schema!!, context)
        }

        response.payloads.forEach { payload ->
          val context = SwiftResolutionContext(document, apiTypeName.nestedType("${opName}ResponsePayload"))
          typeRegistry.resolveTypeName(payload.schema!!, context)
        }
      }
    }
  }

  val baseUri = document.api.servers.firstOrNull()?.url ?: "http://example.com/"
  val problemBaseUri =
    UriTemplate.buildFromTemplate(baseUri)
      .template(document.api.findAnnotation(ProblemBaseUri, null)?.stringValue ?: "")
      .build()
      .expand()

  val problemTypesAnn = document.api.findAnnotation(ProblemTypes, null) as? ObjectNode
  problemTypesAnn?.properties()?.forEach { (problemCode, problemDef) ->
    val problemType = ProblemTypeDefinition(problemCode, problemDef as ObjectNode, URI(problemBaseUri), document, problemDef)
    typeRegistry.defineProblemType(problemCode, problemType)
  }

  val builtTypes =
    typeRegistry.buildTypes()
      .filter { it.key.enclosingTypeName() == null }

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}

fun generate(
  uri: URI,
  typeRegistry: SwiftTypeRegistry,
  compiler: SwiftCompiler,
  generatorFactory: (Document) -> SwiftGenerator
): Map<DeclaredTypeName, TypeSpec> {

  val document = parseAndValidate(uri)

  val generator = generatorFactory(document)

  generator.generateServiceTypes()

  val builtTypes =
    typeRegistry.buildTypes()
      .filter { it.key.enclosingTypeName() == null }

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}
