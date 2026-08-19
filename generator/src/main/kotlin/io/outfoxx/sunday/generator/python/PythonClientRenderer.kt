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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.enabledFor

/** Renders the first Python client runtime and service method surface from IR. */
class PythonClientRenderer(
  private val packageName: String,
  private val registerProblems: Boolean = false,
) {

  /** Renders the shared Python client runtime module. */
  fun renderRuntime(): PythonModule {
    val module = PythonModuleBuilder("$packageName/runtime.py")

    module.addExport("Operation")
    module.addExport("OperationResponse")
    module.addExport("EventStream")
    module.addExport("MediaType")
    module.addExport("ResponseHeaders")
    module.addExport("StreamingBody")
    module.addExport("StreamingOperation")
    module.addExport("Transport")
    module.addExport("TransportRequest")
    module.addExport("TransportResponse")
    module.addExport("as_transport")
    module.addExport("json_body")
    module.addExport("parameter_map")
    module.addExport("path_template")
    module.addCode(
      PythonCodeBlock.of(
        """
        Operation = %T
        OperationResponse = %T
        EventStream = %T
        MediaType = %T
        ResponseHeaders = %T
        StreamingBody = %T
        StreamingOperation = %T
        Transport = %T
        TransportRequest = %T
        TransportResponse = %T
        as_transport = %T
        json_body = %T
        parameter_map = %T
        path_template = %T
        """.trimIndent(),
        PythonSymbol("sunday.httpx_compat", "Operation", "_Operation"),
        PythonSymbol("sunday.httpx_compat", "OperationResponse", "_OperationResponse"),
        PythonSymbol("sunday.httpx_compat", "EventStream", "_EventStream"),
        PythonSymbol("sunday.httpx_compat", "MediaType", "_MediaType"),
        PythonSymbol("sunday.httpx_compat", "ResponseHeaders", "_ResponseHeaders"),
        PythonSymbol("sunday.httpx_compat", "StreamingBody", "_StreamingBody"),
        PythonSymbol("sunday.httpx_compat", "StreamingOperation", "_StreamingOperation"),
        PythonSymbol("sunday.httpx_compat", "Transport", "_Transport"),
        PythonSymbol("sunday.httpx_compat", "TransportRequest", "_TransportRequest"),
        PythonSymbol("sunday.httpx_compat", "TransportResponse", "_TransportResponse"),
        PythonSymbol("sunday.httpx_compat", "as_transport", "_as_transport"),
        PythonSymbol("sunday.httpx_compat", "json_body", "_json_body"),
        PythonSymbol("sunday.httpx_compat", "parameter_map", "_parameter_map"),
        PythonSymbol("sunday.httpx_compat", "path_template", "_path_template"),
      ),
    )

    return module.build()
  }

  /** Renders a Python service client module. */
  fun renderService(service: GeneratedService): PythonModule {
    val moduleName = service.pythonServiceModuleName
    val module = PythonModuleBuilder("$packageName/$moduleName.py")
    val className = "${service.pythonServiceBaseName.pythonTypeName}Client"
    val operationMethods =
      PythonCodeBlock.join(
        service.operations.map { operation ->
          operation.renderOperationMethod()
        },
        "\n\n",
      )
    val problemRegistration =
      if (registerProblems) {
        PythonCodeBlock.of(
          "\n        %T(self._transport.problem_registry)",
          PythonSymbol(".problems", "register_problems"),
        )
      } else {
        PythonCodeBlock.of("")
      }

    module.addExport(className)
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L:
            ${"\"\"\"Client operations for the %L service.\"\"\""}

            def __init__(self, transport: %T) -> None:
                self._transport = %T(transport)%C

        %C
        """.trimIndent(),
        className,
        service.pythonServiceBaseName,
        PythonSymbol(".runtime", "Transport"),
        PythonSymbol(".runtime", "as_transport"),
        problemRegistration,
        operationMethods,
      ),
    )

    service.operations.forEach { operation ->
      module.addCode(operation.renderResponseDecoderFunction())
    }

    return module.build()
  }

  private fun GeneratedOperation.renderOperationMethod(): PythonCodeBlock {
    if (streaming != null) {
      return renderStreamMethod()
    }

    val response = successResponse()
    val responseType = response?.type ?: GeneratedTypeRef.scalar("nil")
    val decoderName = "_decode_${id.pythonIdentifierName}_response"
    val signatureParameters = renderClientSignatureParameterLines()
    val operationType =
      if (requestBody.isPythonStreamingRequestBody) {
        PythonSymbol(".runtime", "StreamingOperation")
      } else {
        PythonSymbol(".runtime", "Operation")
      }

    return if (hasClientSignatureParameters()) {
      PythonCodeBlock.of(
        """
        |    def %L(
        |        self,
        |%C
        |    ) -> %T[%C]:
        |        ${"\"\"\"Create the %L operation.\"\"\""}
        |%C
        |%C
        """.trimMargin(),
        id.pythonIdentifierName,
        signatureParameters,
        operationType,
        responseType.renderClientPythonType(),
        id,
        if (requestBody.isPythonStreamingRequestBody) {
          renderBuildStreamingRequestFunction()
        } else {
          renderBuildRequest()
        },
        renderOperationReturn(operationType, decoderName, requestBody.isPythonStreamingRequestBody),
      )
    } else {
      PythonCodeBlock.of(
        """
        |    def %L(self) -> %T[%C]:
        |        ${"\"\"\"Create the %L operation.\"\"\""}
        |%C
        |%C
        """.trimMargin(),
        id.pythonIdentifierName,
        operationType,
        responseType.renderClientPythonType(),
        id,
        if (requestBody.isPythonStreamingRequestBody) {
          renderBuildStreamingRequestFunction()
        } else {
          renderBuildRequest()
        },
        renderOperationReturn(operationType, decoderName, requestBody.isPythonStreamingRequestBody),
      )
    }
  }

  private fun GeneratedOperation.renderStreamMethod(): PythonCodeBlock {
    val responseType = successResponse()?.type ?: GeneratedTypeRef.scalar("string")
    val decoderName = "_decode_${id.pythonIdentifierName}_event"
    val signatureParameters = renderClientSignatureParameterLines()

    return if (hasClientSignatureParameters()) {
      PythonCodeBlock.of(
        """
        |    def %L(
        |        self,
        |%C
        |    ) -> %T[%C]:
        |        ${"\"\"\"Create the %L event stream.\"\"\""}
        |%C
        |%C
        """.trimMargin(),
        id.pythonIdentifierName,
        signatureParameters,
        PythonSymbol(".runtime", "EventStream"),
        responseType.renderClientPythonType(),
        id,
        renderBuildRequest(),
        renderOperationReturn(PythonSymbol(".runtime", "EventStream"), decoderName, false),
      )
    } else {
      PythonCodeBlock.of(
        """
        |    def %L(self) -> %T[%C]:
        |        ${"\"\"\"Create the %L event stream.\"\"\""}
        |%C
        |%C
        """.trimMargin(),
        id.pythonIdentifierName,
        PythonSymbol(".runtime", "EventStream"),
        responseType.renderClientPythonType(),
        id,
        renderBuildRequest(),
        renderOperationReturn(PythonSymbol(".runtime", "EventStream"), decoderName, false),
      )
    }
  }

  private fun GeneratedOperation.renderBuildRequest(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |        request = self._transport.client.build_request(
      |            %S,
      |%C%C
      |        )
      """.trimMargin(),
      httpMethod(),
      renderPathTemplateArgument("            "),
      renderBuildRequestArguments("            "),
    )

  private fun GeneratedOperation.renderBuildStreamingRequestFunction(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |
      |        def build_request() -> %T:
      |            return self._transport.client.build_request(
      |                %S,
      |%C%C
      |            )
      |
      """.trimMargin(),
      PythonSymbol(".runtime", "TransportRequest"),
      httpMethod(),
      renderPathTemplateArgument("                "),
      renderBuildRequestArguments("                "),
    )

  private fun GeneratedOperation.renderOperationReturn(
    operationType: PythonSymbol,
    decoderName: String,
    streamingRequestBody: Boolean,
  ): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |        return %T(
      |%C
      |        )
      """.trimMargin(),
      operationType,
      if (streamingRequestBody) {
        PythonCodeBlock.of(
          """
          |            transport=self._transport,
          |            build_request=build_request,
          |            decode=%L,
          """.trimMargin(),
          decoderName,
        )
      } else {
        PythonCodeBlock.of(
          """
          |            transport=self._transport,
          |            request=request,
          |            decode=%L,
          """.trimMargin(),
          decoderName,
        )
      },
    )

  private fun GeneratedOperation.hasClientSignatureParameters(): Boolean =
    pathParameters().isNotEmpty() ||
      requestBody != null ||
      queryParameters().isNotEmpty() ||
      headerParameters().isNotEmpty()

  private fun GeneratedOperation.httpMethod(): String =
    if (streaming != null && method.equals("SUBSCRIBE", ignoreCase = true)) {
      "GET"
    } else {
      method.uppercase()
    }

  private fun GeneratedOperation.renderResponseDecoderFunction(): PythonCodeBlock {
    val response = successResponse()
    val responseType = response?.type ?: GeneratedTypeRef.scalar("nil")
    val decoderName =
      if (streaming == null) {
        "_decode_${id.pythonIdentifierName}_response"
      } else {
        "_decode_${id.pythonIdentifierName}_event"
      }

    return if (streaming == null) {
      PythonCodeBlock.of(
        """
        def %L(response: %T) -> %C:
        %C
        """.trimIndent(),
        decoderName,
        PythonSymbol("httpx", "Response"),
        responseType.renderClientPythonType(),
        responseType.renderResponseDecoder(),
      )
    } else {
      PythonCodeBlock.of(
        """
        def %L(data: str) -> %C:
            return %T(%C).validate_json(data)
        """.trimIndent(),
        decoderName,
        responseType.renderClientPythonType(),
        PythonSymbol("pydantic", "TypeAdapter"),
        responseType.renderClientPythonType(),
      )
    }
  }

  private fun GeneratedOperation.successResponse(): GeneratedResponse? =
    responses.firstOrNull { response -> response.status in 200..299 && response.type != null }
      ?: responses.firstOrNull { response -> streaming != null && response.type != null }

  private fun GeneratedParameter.wireName(): String = serializationName ?: name

  private fun GeneratedOperation.renderClientSignatureParameterLines(): PythonCodeBlock {
    val queryAndHeaderParameters =
      parameters.filter { parameter ->
        parameter.location == GeneratedParameter.Location.QUERY ||
          parameter.location == GeneratedParameter.Location.HEADER
      }
    val requiredParameters =
      pathParameters().map { parameter -> parameter.renderRequiredParameter() } +
        listOfNotNull(requestBody?.renderRequestBodyParameter()) +
        queryAndHeaderParameters
          .filter { parameter -> parameter.required }
          .map { parameter -> parameter.renderRequiredParameter() }
    val optionalParameters =
      queryAndHeaderParameters
        .filterNot { parameter -> parameter.required }
        .map { parameter ->
          parameter.renderOptionalParameter()
        }

    return PythonCodeBlock.join(requiredParameters + optionalParameters, separator = "\n")
  }

  private fun GeneratedParameter.renderRequiredParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %C,",
      name.pythonIdentifierName,
      type.renderClientPythonType(nullable = false),
    )

  private fun GeneratedPayload.renderRequestBodyParameter(): PythonCodeBlock =
    if (isPythonStreamingRequestBody) {
      PythonCodeBlock.of("        body: %T,", PythonSymbol(".runtime", "StreamingBody"))
    } else {
      PythonCodeBlock.of("        body: %C,", type.renderClientPythonType(nullable = false))
    }

  private fun GeneratedParameter.renderOptionalParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %C = %C,",
      name.pythonIdentifierName,
      type.renderOptionalParameterType(),
      renderDefaultValue(),
    )

  private fun GeneratedTypeRef.renderOptionalParameterType(): PythonCodeBlock =
    if (nullable) {
      renderClientPythonType()
    } else {
      PythonCodeBlock.of("%C | None", renderClientPythonType(nullable = false))
    }

  private fun GeneratedParameter.renderDefaultValue(): PythonCodeBlock =
    defaultValue?.renderPythonValue() ?: PythonCodeBlock.of("None")

  private fun Any.renderPythonValue(): PythonCodeBlock =
    when (this) {
      is Boolean -> PythonCodeBlock.of(if (this) "True" else "False")
      is Number -> PythonCodeBlock.of("%L", this)
      is String -> PythonCodeBlock.of("%S", this)
      else -> PythonCodeBlock.of("None")
    }

  private fun GeneratedOperation.renderPathTemplateArgument(indent: String): PythonCodeBlock {
    val pathParameters = pathParameters()
    val inlineMap = pathParameters.renderInlinePathParameterMap()
    val inlineCall = "path_template(${path.pythonStringLiteral()}, $inlineMap),"
    return if (inlineCall.length <= 100) {
      PythonCodeBlock.of(
        "%L%T(%S, %L),",
        indent,
        PythonSymbol(".runtime", "path_template"),
        path,
        inlineMap,
      )
    } else {
      PythonCodeBlock.of(
        """
        |%L%T(
        |%L    %S,
        |%C
        |%L),
        """.trimMargin(),
        indent,
        PythonSymbol(".runtime", "path_template"),
        indent,
        path,
        pathParameters.renderMultilinePathParameterMap(indent),
        indent,
      )
    }
  }

  private fun List<GeneratedParameter>.renderInlinePathParameterMap(): String =
    if (isEmpty()) {
      "{}"
    } else {
      joinToString(", ", prefix = "{", postfix = "}") { parameter ->
        "${parameter.wireName().pythonStringLiteral()}: ${parameter.name.pythonIdentifierName}"
      }
    }

  private fun List<GeneratedParameter>.renderMultilinePathParameterMap(indent: String): PythonCodeBlock =
    if (isEmpty()) {
      PythonCodeBlock.of("%L    {},", indent)
    } else {
      PythonCodeBlock.join(
        listOf(PythonCodeBlock.of("%L    {", indent)) +
          map { parameter ->
            PythonCodeBlock.of(
              "%L        %S: %L,",
              indent,
              parameter.wireName(),
              parameter.name.pythonIdentifierName,
            )
          } +
          listOf(PythonCodeBlock.of("%L    },", indent)),
      )
    }

  private fun GeneratedOperation.renderBuildRequestArguments(indent: String): PythonCodeBlock {
    val arguments =
      listOfNotNull(
        queryParameters().renderRequestParameters("params", indent),
        renderHeadersArgument(indent),
        requestBody?.renderRequestBodyArgument(indent),
      )

    return if (arguments.isEmpty()) {
      PythonCodeBlock.of("")
    } else {
      PythonCodeBlock.of("\n%C", PythonCodeBlock.join(arguments, separator = "\n"))
    }
  }

  private fun GeneratedOperation.renderHeadersArgument(indent: String): PythonCodeBlock? {
    val headerParameters = headerParameters()
    val contentType =
      if (headerParameters.any { parameter -> parameter.wireName().equals("Content-Type", ignoreCase = true) }) {
        null
      } else {
        requestBody.contentTypeHeaderForContentBody()
      }

    if (headerParameters.isEmpty() && contentType == null) {
      return null
    }

    return PythonCodeBlock.of(
      "%Lheaders=%C,",
      indent,
      when {
        headerParameters.isEmpty() ->
          PythonCodeBlock.of("{%S: %S}", "Content-Type", contentType)
        contentType == null ->
          headerParameters.renderRequestParameterMap()
        else ->
          PythonCodeBlock.of(
            "{**%C, %S: %S}",
            headerParameters.renderRequestParameterMap(),
            "Content-Type",
            contentType,
          )
      },
    )
  }

  private fun GeneratedPayload.renderRequestBodyArgument(indent: String): PythonCodeBlock =
    when {
      isPythonStreamingRequestBody ->
        PythonCodeBlock.of("%Lcontent=body.content(),", indent)
      type.isBinaryPayload() ->
        PythonCodeBlock.of("%Lcontent=body,", indent)
      else ->
        PythonCodeBlock.of("%Ljson=%T(body),", indent, PythonSymbol(".runtime", "json_body"))
    }

  private fun GeneratedTypeRef.isBinaryPayload(): Boolean =
    kind == GeneratedTypeRef.Kind.SCALAR &&
      (name == "file" || name == "binary" || name == "byte")

  private fun List<GeneratedParameter>.renderRequestParameters(
    argumentName: String,
    indent: String,
  ): PythonCodeBlock? {
    if (isEmpty()) {
      return null
    }

    return PythonCodeBlock.of(
      "%L%L=%T({%L}),",
      indent,
      argumentName,
      PythonSymbol(".runtime", "parameter_map"),
      joinToString(", ") { parameter ->
        "${parameter.wireName().pythonStringLiteral()}: ${parameter.name.pythonIdentifierName}"
      },
    )
  }

  private fun List<GeneratedParameter>.renderRequestParameterMap(): PythonCodeBlock =
    PythonCodeBlock.of(
      "%T({%L})",
      PythonSymbol(".runtime", "parameter_map"),
      joinToString(", ") { parameter ->
        "${parameter.wireName().pythonStringLiteral()}: ${parameter.name.pythonIdentifierName}"
      },
    )

  private fun GeneratedPayload?.contentTypeHeaderForContentBody(): String? {
    if (this == null || mediaTypes.isEmpty() || !usesPythonContentRequestBody) {
      return null
    }

    return mediaTypes.first()
  }

  private val GeneratedPayload.usesPythonContentRequestBody: Boolean
    get() = isPythonStreamingRequestBody || type.isBinaryPayload()

  private fun GeneratedOperation.pathParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.PATH }

  private fun GeneratedOperation.queryParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.QUERY }

  private fun GeneratedOperation.headerParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.HEADER }

  private fun GeneratedTypeRef.renderClientPythonType(nullable: Boolean = true): PythonCodeBlock {
    val type =
      when (kind) {
        GeneratedTypeRef.Kind.NAMED -> PythonCodeBlock.of("%T", PythonSymbol(".models", name.pythonTypeName))
        GeneratedTypeRef.Kind.ARRAY ->
          PythonCodeBlock.of(
            "list[%C]",
            arguments.firstOrNull()?.renderClientPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
          )
        GeneratedTypeRef.Kind.MAP ->
          PythonCodeBlock.of(
            "dict[str, %C]",
            arguments.firstOrNull()?.renderClientPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
          )
        GeneratedTypeRef.Kind.UNION ->
          if (arguments.isEmpty()) {
            PythonCodeBlock.of("object")
          } else {
            PythonCodeBlock.join(
              arguments.map { type ->
                type.renderClientPythonType(nullable = false)
              },
              separator = " | ",
            )
          }
        GeneratedTypeRef.Kind.SCALAR -> renderPythonType(nullable = false)
      }

    return if (nullable && this.nullable) {
      PythonCodeBlock.of("%C | None", type)
    } else {
      type
    }
  }

  private fun GeneratedTypeRef.renderResponseDecoder(): PythonCodeBlock =
    when (kind) {
      GeneratedTypeRef.Kind.NAMED ->
        PythonCodeBlock.of(
          "    return %T(%C).validate_python(response.json())",
          PythonSymbol("pydantic", "TypeAdapter"),
          renderClientPythonType(nullable = false),
        )
      GeneratedTypeRef.Kind.SCALAR ->
        when (name) {
          "nil" -> PythonCodeBlock.of("    return None")
          "string" -> PythonCodeBlock.of("    return response.text")
          "file" -> PythonCodeBlock.of("    return response.content")
          else ->
            PythonCodeBlock.of(
              "    return %T(%C).validate_python(response.json())",
              PythonSymbol("pydantic", "TypeAdapter"),
              renderClientPythonType(nullable = false),
            )
        }
      else ->
        PythonCodeBlock.of(
          "    return %T(%C).validate_python(response.json())",
          PythonSymbol("pydantic", "TypeAdapter"),
          renderClientPythonType(nullable = false),
        )
    }

  private val GeneratedPayload?.isPythonStreamingRequestBody: Boolean
    get() = this?.streaming?.enabledFor(GenerationMode.Client) == true
}
