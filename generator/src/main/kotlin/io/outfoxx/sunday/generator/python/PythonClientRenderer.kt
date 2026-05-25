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

import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Renders the first Python client runtime and service method surface from IR. */
class PythonClientRenderer(
  private val packageName: String,
) {

  /** Renders the shared Python client runtime module. */
  fun renderRuntime(): PythonModule {
    val module = PythonModuleBuilder("$packageName/runtime.py")

    module.addExport("Operation")
    module.addExport("OperationResponse")
    module.addExport("EventStream")
    module.addExport("MediaType")
    module.addExport("ResponseHeaders")
    module.addExport("Transport")
    module.addExport("TransportRequest")
    module.addExport("TransportResponse")
    module.addExport("json_body")
    module.addExport("parameter_map")
    module.addExport("path_template")
    module.addCode(
      PythonCodeBlock.of(
        """
        type Transport = %T
        type TransportRequest = %T
        type TransportResponse = %T


        @%T(frozen=True, slots=True)
        class MediaType:
            ${"\"\"\"A parsed media type header value.\"\"\""}

            value: str

            @property
            def type(self) -> str:
                ${"\"\"\"Return the top-level media type.\"\"\""}
                return self.value.split(";", 1)[0].partition("/")[0].strip().lower()

            @property
            def subtype(self) -> str:
                ${"\"\"\"Return the media subtype.\"\"\""}
                return self.value.split(";", 1)[0].partition("/")[2].strip().lower()

            def __str__(self) -> str:
                return self.value


        @%T(frozen=True, slots=True)
        class ResponseHeaders:
            ${"\"\"\"Case-insensitive access to response headers.\"\"\""}

            entries: tuple[tuple[str, str], ...]

            @classmethod
            def from_headers(cls, headers: %T) -> ResponseHeaders:
                ${"\"\"\"Create a response header view from httpx headers.\"\"\""}
                return cls(tuple(headers.multi_items()))

            def get_all(self, name: str) -> tuple[str, ...]:
                ${"\"\"\"Return all response header values with the requested name.\"\"\""}
                lower_name = name.lower()
                return tuple(value for header_name, value in self.entries if header_name.lower() == lower_name)

            def get(self, name: str) -> str | None:
                ${"\"\"\"Return the first response header value with the requested name.\"\"\""}
                values = self.get_all(name)
                return values[0] if values else None

            @property
            def content_type(self) -> MediaType | None:
                ${"\"\"\"Return the parsed Content-Type header when present.\"\"\""}
                value = self.get("Content-Type")
                return MediaType(value) if value is not None else None


        @%T(frozen=True, slots=True)
        class OperationResponse[ResponseT]:
            ${"\"\"\"A typed operation result with its transport response metadata.\"\"\""}

            result: ResponseT
            transport_response: TransportResponse

            @property
            def status(self) -> int:
                ${"\"\"\"Return the HTTP response status code.\"\"\""}
                return self.transport_response.status_code

            @property
            def headers(self) -> ResponseHeaders:
                ${"\"\"\"Return a case-insensitive response header view.\"\"\""}
                return ResponseHeaders.from_headers(self.transport_response.headers)

            def get_headers(self, name: str) -> tuple[str, ...]:
                ${"\"\"\"Return all response header values with the requested name.\"\"\""}
                return self.headers.get_all(name)

            def get_header(self, name: str) -> str | None:
                ${"\"\"\"Return the first response header value with the requested name.\"\"\""}
                return self.headers.get(name)

            @property
            def content_type(self) -> MediaType | None:
                ${"\"\"\"Return the parsed Content-Type header when present.\"\"\""}
                return self.headers.content_type


        @%T(frozen=True, slots=True)
        class Operation[ResponseT]:
            ${"\"\"\"A prepared HTTP operation that can produce transport values or execute.\"\"\""}

            transport: Transport
            request: TransportRequest
            decode: %T[[%T], ResponseT]

            async def execute(self) -> ResponseT:
                ${"\"\"\"Send the request and decode the successful response body.\"\"\""}
                return (await self.response()).result

            async def response(self) -> OperationResponse[ResponseT]:
                ${"\"\"\"Send the request and return the decoded response with metadata.\"\"\""}
                response = await self.transport_response()
                response.raise_for_status()
                return OperationResponse(result=self.decode(response), transport_response=response)

            def transport_request(self) -> TransportRequest:
                ${"\"\"\"Return the transport-specific request value.\"\"\""}
                return self.request

            async def transport_response(self) -> TransportResponse:
                ${"\"\"\"Send the request and return the transport-specific response value.\"\"\""}
                return await self.transport.send(self.request)


        @%T(frozen=True, slots=True)
        class EventStream[EventT]:
            ${"\"\"\"A prepared HTTP SSE stream that decodes events as they arrive.\"\"\""}

            transport: Transport
            request: TransportRequest
            decode: %T[[str], EventT]

            def __aiter__(self) -> %T[EventT]:
                return self.events()

            async def events(self) -> %T[EventT]:
                ${"\"\"\"Send the request and decode server-sent event payloads.\"\"\""}
                response = await self.transport.send(self.request, stream=True)
                try:
                    response.raise_for_status()
                    async for payload in event_payloads(response):
                        yield self.decode(payload)
                finally:
                    await response.aclose()


        def json_body(body: object | None) -> object | None:
            ${"\"\"\"Convert a generated model into a JSON request body.\"\"\""}
            if isinstance(body, %T):
                return body.model_dump(mode="json", by_alias=True, exclude_none=True)
            return body


        def parameter_map(parameters: %T[str, object | None]) -> dict[str, str]:
            ${"\"\"\"Format request parameters while omitting absent values.\"\"\""}
            return {name: parameter_value(value) for name, value in parameters.items() if value is not None}


        def path_template(path: str, path_parameters: %T[str, object]) -> str:
            ${"\"\"\"Expand a URI path template using URL-encoded path parameter values.\"\"\""}
            for name, value in path_parameters.items():
                path = path.replace("{" + name + "}", %T(parameter_value(value), safe=""))
            return path


        def parameter_value(value: object) -> str:
            if isinstance(value, bool):
                return "true" if value else "false"
            return str(value)


        async def event_payloads(response: %T) -> %T[str]:
            data_lines: list[str] = []
            async for line in response.aiter_lines():
                if line == "":
                    if data_lines:
                        yield "\n".join(data_lines)
                        data_lines = []
                    continue
                if line.startswith(":"):
                    continue
                field, _, value = line.partition(":")
                if value.startswith(" "):
                    value = value[1:]
                if field == "data":
                    data_lines.append(value)

            if data_lines:
                yield "\n".join(data_lines)
        """.trimIndent(),
        PythonSymbol("httpx", "AsyncClient"),
        PythonSymbol("httpx", "Request"),
        PythonSymbol("httpx", "Response"),
        PythonSymbol("dataclasses", "dataclass"),
        PythonSymbol("dataclasses", "dataclass"),
        PythonSymbol("httpx", "Headers"),
        PythonSymbol("dataclasses", "dataclass"),
        PythonSymbol("dataclasses", "dataclass"),
        PythonSymbol("collections.abc", "Callable"),
        PythonSymbol("httpx", "Response"),
        PythonSymbol("dataclasses", "dataclass"),
        PythonSymbol("collections.abc", "Callable"),
        PythonSymbol("collections.abc", "AsyncIterator"),
        PythonSymbol("collections.abc", "AsyncIterator"),
        PythonSymbol("pydantic", "BaseModel"),
        PythonSymbol("collections.abc", "Mapping"),
        PythonSymbol("collections.abc", "Mapping"),
        PythonSymbol("urllib.parse", "quote"),
        PythonSymbol("httpx", "Response"),
        PythonSymbol("collections.abc", "AsyncIterator"),
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

    module.addExport(className)
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L:
            ${"\"\"\"Client operations for the %L service.\"\"\""}

            def __init__(self, transport: %T) -> None:
                self._transport = transport

        %C
        """.trimIndent(),
        className,
        service.pythonServiceBaseName,
        PythonSymbol(".runtime", "Transport"),
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
        PythonSymbol(".runtime", "Operation"),
        responseType.renderClientPythonType(),
        id,
        renderBuildRequest(),
        renderOperationReturn(PythonSymbol(".runtime", "Operation"), decoderName),
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
        PythonSymbol(".runtime", "Operation"),
        responseType.renderClientPythonType(),
        id,
        renderBuildRequest(),
        renderOperationReturn(PythonSymbol(".runtime", "Operation"), decoderName),
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
        renderOperationReturn(PythonSymbol(".runtime", "EventStream"), decoderName),
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
        renderOperationReturn(PythonSymbol(".runtime", "EventStream"), decoderName),
      )
    }
  }

  private fun GeneratedOperation.renderBuildRequest(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |        request = self._transport.build_request(
      |            %S,
      |%C%C
      |        )
      """.trimMargin(),
      httpMethod(),
      renderPathTemplateArgument(),
      renderBuildRequestArguments(),
    )

  private fun GeneratedOperation.renderOperationReturn(
    operationType: PythonSymbol,
    decoderName: String,
  ): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |        return %T(
      |            transport=self._transport,
      |            request=request,
      |            decode=%L,
      |        )
      """.trimMargin(),
      operationType,
      decoderName,
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
    val requiredParameters =
      pathParameters().map { parameter ->
        PythonCodeBlock.of(
          "        %L: %C,",
          parameter.name.pythonIdentifierName,
          parameter.type.renderClientPythonType(nullable = false),
        )
      } + listOfNotNull(requestBody?.renderRequestBodyParameter())
    val optionalParameters =
      parameters
        .filter { parameter ->
          parameter.location == GeneratedParameter.Location.QUERY ||
            parameter.location == GeneratedParameter.Location.HEADER
        }.map { parameter ->
          parameter.renderOptionalParameter()
        }

    return PythonCodeBlock.join(requiredParameters + optionalParameters, separator = "\n")
  }

  private fun GeneratedPayload.renderRequestBodyParameter(): PythonCodeBlock =
    PythonCodeBlock.of("        body: %C,", type.renderClientPythonType(nullable = false))

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

  private fun GeneratedOperation.renderPathTemplateArgument(): PythonCodeBlock {
    val pathParameters = pathParameters()
    val inlineMap = pathParameters.renderInlinePathParameterMap()
    val inlineCall = "path_template(${path.pythonStringLiteral()}, $inlineMap),"
    return if (inlineCall.length <= 100) {
      PythonCodeBlock.of(
        "            %T(%S, %L),",
        PythonSymbol(".runtime", "path_template"),
        path,
        inlineMap,
      )
    } else {
      PythonCodeBlock.of(
        """
        |            %T(
        |                %S,
        |%C
        |            ),
        """.trimMargin(),
        PythonSymbol(".runtime", "path_template"),
        path,
        pathParameters.renderMultilinePathParameterMap(),
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

  private fun List<GeneratedParameter>.renderMultilinePathParameterMap(): PythonCodeBlock =
    if (isEmpty()) {
      PythonCodeBlock.of("                {},")
    } else {
      PythonCodeBlock.join(
        listOf(PythonCodeBlock.of("                {")) +
          map { parameter ->
            PythonCodeBlock.of(
              "                    %S: %L,",
              parameter.wireName(),
              parameter.name.pythonIdentifierName,
            )
          } +
          listOf(PythonCodeBlock.of("                },")),
      )
    }

  private fun GeneratedOperation.renderBuildRequestArguments(): PythonCodeBlock {
    val arguments =
      listOfNotNull(
        queryParameters().renderRequestParameters("params"),
        headerParameters().renderRequestParameters("headers"),
        requestBody?.renderRequestBodyArgument(),
      )

    return if (arguments.isEmpty()) {
      PythonCodeBlock.of("")
    } else {
      PythonCodeBlock.of("\n%C", PythonCodeBlock.join(arguments, separator = "\n"))
    }
  }

  private fun GeneratedPayload.renderRequestBodyArgument(): PythonCodeBlock =
    if (type.isBinaryPayload()) {
      PythonCodeBlock.of("            content=body,")
    } else {
      PythonCodeBlock.of("            json=%T(body),", PythonSymbol(".runtime", "json_body"))
    }

  private fun GeneratedTypeRef.isBinaryPayload(): Boolean =
    kind == GeneratedTypeRef.Kind.SCALAR &&
      (name == "file" || name == "binary" || name == "byte")

  private fun List<GeneratedParameter>.renderRequestParameters(argumentName: String): PythonCodeBlock? {
    if (isEmpty()) {
      return null
    }

    return PythonCodeBlock.of(
      "            %L=%T({%L}),",
      argumentName,
      PythonSymbol(".runtime", "parameter_map"),
      joinToString(", ") { parameter ->
        "${parameter.wireName().pythonStringLiteral()}: ${parameter.name.pythonIdentifierName}"
      },
    )
  }

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
}
