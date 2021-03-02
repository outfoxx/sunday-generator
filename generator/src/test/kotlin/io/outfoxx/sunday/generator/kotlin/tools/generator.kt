package io.outfoxx.sunday.generator.kotlin.tools

import amf.MessageStyles
import amf.ProfileNames
import amf.client.AMF
import amf.client.environment.Environment
import amf.client.model.document.Document
import amf.client.model.domain.ObjectNode
import amf.client.parse.Raml10Parser
import com.damnhandy.uri.template.UriTemplate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUri
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemTypes
import io.outfoxx.sunday.generator.utils.LocalResourceLoader
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.cookieParameters
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.kotlin.KotlinGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinResolutionContext
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
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

      System.err.println("$location:${line}: ${result.message()}")

    }

    exitProcess(1)

  }

  return document
}

fun findType(name: String, types: Map<ClassName, TypeSpec>): TypeSpec =
  types[ClassName.bestGuess(name)] ?: fail("Type '$name' not defined")

fun generateTypes(uri: URI, typeRegistry: KotlinTypeRegistry): Map<ClassName, TypeSpec> {

  val document = parseAndValidate(uri)

  val apiPackageName =
    document.encodes.findStringAnnotation(APIAnnotationName.KotlinPkg, typeRegistry.generationMode)
      ?: typeRegistry.defaultModelPackageName

  val apiTypeName = ClassName.bestGuess("$apiPackageName.API")

  document.api.endPoints.forEach { endPoint ->

    endPoint.operations.forEach { operation ->

      val opName = (operation.operationId ?: operation.name!!).toUpperCamelCase()

      operation.requests.forEach { request ->

        request.uriParameters.forEach { param ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}UriParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.queryParameters.forEach { param ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}QueryParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.cookieParameters.forEach { param ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}CookieParams"))
          typeRegistry.resolveTypeName(param.schema!!, context)
        }

        request.headers.forEach { header ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}RequestHeaderParams"))
          typeRegistry.resolveTypeName(header.schema!!, context)
        }

        request.payloads.forEach { payload ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}Payload"))
          typeRegistry.resolveTypeName(payload.schema!!, context)
        }
        val queryString = request.queryString
        if (queryString != null) {
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}QueryStringParams"))
          typeRegistry.resolveTypeName(queryString, context)
        }

      }

      operation.responses.forEach { response ->

        response.headers.forEach { header ->
          val context =
            KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}ResponseHeaderParams"))
          typeRegistry.resolveTypeName(header.schema!!, context)
        }

        response.payloads.forEach { payload ->
          val context = KotlinResolutionContext(document, apiTypeName.nestedClass("${opName}ResponsePayload"))
          typeRegistry.resolveTypeName(payload.schema!!, context)
        }

      }

    }

  }

  val baseUri = document.api.servers.firstOrNull()?.url ?: "http://example.com/"
  val problemBaseUri =
    UriTemplate.buildFromTemplate(baseUri)
      .template(document.api.findAnnotation(ProblemBaseUri, typeRegistry.generationMode)?.stringValue ?: "")
      .build()
      .expand()

  val problemTypesAnn = document.api.findAnnotation(ProblemTypes, typeRegistry.generationMode) as? ObjectNode
  problemTypesAnn?.properties()?.forEach { (problemCode, problemDef) ->
    val problemType = ProblemTypeDefinition(problemCode, problemDef as ObjectNode, URI(problemBaseUri), document)
    typeRegistry.defineProblemType(problemCode, problemType)
  }

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(compileTypes(builtTypes).exitCode, KotlinCompilation.ExitCode.OK)

  return builtTypes
}

fun generate(
  uri: URI,
  typeRegistry: KotlinTypeRegistry,
  generatorFactory: (Document) -> KotlinGenerator
): Map<ClassName, TypeSpec> {

  val document = parseAndValidate(uri)

  val generator = generatorFactory(document)

  generator.generateServiceTypes()

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(compileTypes(builtTypes).exitCode, KotlinCompilation.ExitCode.OK)

  return builtTypes
}
