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
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.enabledFor

/** Renders declarative transport-neutral Sunday Python clients from generated IR. */
class PythonClientRenderer(
  private val packageName: String,
  private val registerProblems: Boolean = false,
) {

  /** Renders one generated service client. */
  fun renderService(service: GeneratedService): PythonModule {
    val module = PythonModuleBuilder("$packageName/${service.pythonServiceModuleName}.py")
    val className = "${service.pythonServiceBaseName.pythonTypeName}Client"
    val problemRegistration =
      if (registerProblems) {
        PythonCodeBlock.of(
          "\n        %T(self._transport)",
          PythonSymbol(".problems", "register_problems"),
        )
      } else {
        PythonCodeBlock.of("")
      }

    module.addExport(className)
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L[TransportRequestT, TransportResponseT]:
            ${"\"\"\"Client operations for the %L service.\"\"\""}

            def __init__(self, transport: %T[TransportRequestT, TransportResponseT]) -> None:
                self._transport = transport%C

        %C
        """.trimIndent(),
        className,
        service.pythonServiceBaseName,
        PythonSymbol("sunday", "Transport"),
        problemRegistration,
        PythonCodeBlock.join(service.operations.map { it.renderOperationMethod() }, separator = "\n\n"),
      ),
    )

    service.operations.forEach { operation ->
      operation.renderDecoderFunctions()?.let(module::addCode)
      operation.renderRequestPayloadFunction()?.let(module::addCode)
    }
    return module.build()
  }

  private fun GeneratedOperation.renderOperationMethod(): PythonCodeBlock {
    val signature = renderSignatureParameters()
    val responseType = renderSuccessType()
    val operationType =
      when {
        streaming != null -> PythonSymbol("sunday", "EventStream")
        nullify != null -> PythonSymbol("sunday", "NullableOperation")
        requestBody.isPythonStreamingRequestBody -> PythonSymbol("sunday", "StreamingOperation")
        else -> PythonSymbol("sunday", "Operation")
      }
    val operationReturnType =
      if (streaming != null) {
        PythonCodeBlock.of("%T[%C]", operationType, responseType)
      } else {
        PythonCodeBlock.of("%T[%C, TransportRequestT, TransportResponseT]", operationType, responseType)
      }
    val body =
      if (streaming != null) {
        PythonCodeBlock.of(
          """
          |        request_spec: %T[%C] = %C
          |        return self._transport.event_stream(request_spec, %L)
          """.trimMargin(),
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(),
          eventDecoderName(),
        )
      } else {
        PythonCodeBlock.of(
          """
          |        request_spec: %T[%C] = %C
          |        operation_spec: %T[%C, %C] = %T(
          |            request=request_spec,
          |            responses=%C,
          |        )
          |%C
          """.trimMargin(),
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(),
          PythonSymbol("sunday", "OperationSpec"),
          renderRequestBodyType(),
          responseType,
          PythonSymbol("sunday", "OperationSpec"),
          renderResponseSpecs(),
          renderOperationConstruction(operationType),
        )
      }

    return if (!hasSignatureParameters()) {
      PythonCodeBlock.of(
        """
            def %L(self) -> %C:
                ${"\"\"\"Create the %L operation.\"\"\""}
        %C
        """.trimIndent(),
        id.pythonIdentifierName,
        operationReturnType,
        id,
        body,
      )
    } else {
      PythonCodeBlock.of(
        """
            def %L(
                self,
        %C
            ) -> %C:
                ${"\"\"\"Create the %L operation.\"\"\""}
        %C
        """.trimIndent(),
        id.pythonIdentifierName,
        signature,
        operationReturnType,
        id,
        body,
      )
    }
  }

  private fun GeneratedOperation.renderOperationConstruction(operationType: PythonSymbol): PythonCodeBlock =
    nullify?.let { nullify ->
      val problemTypes =
        nullify.problems.mapNotNull { problem ->
          problem.takeIf { type -> type.kind == GeneratedTypeRef.Kind.NAMED }?.let { type ->
            PythonCodeBlock.of("%T", PythonSymbol(".problems", type.name.pythonTypeName))
          }
        }
      PythonCodeBlock.of(
        "        return %T(\n" +
          "            self._transport,\n" +
          "            operation_spec,\n" +
          "            %T(statuses=%C, problem_types=%C),\n" +
          "        )",
        operationType,
        PythonSymbol("sunday", "NullifySpec"),
        renderTuple(nullify.statuses.map { status -> PythonCodeBlock.of("%L", status) }),
        renderTuple(problemTypes),
      )
    } ?: PythonCodeBlock.of("        return %T(self._transport, operation_spec)", operationType)

  private fun renderTuple(values: List<PythonCodeBlock>): PythonCodeBlock =
    if (values.isEmpty()) {
      PythonCodeBlock.of("()")
    } else {
      PythonCodeBlock.of("(%C,)", PythonCodeBlock.join(values, separator = ", "))
    }

  private fun GeneratedOperation.renderRequestSpec(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |%T(
      |            method=%S,
      |            path_template=%S,
      |            parameters=%C,
      |%C
      |            accept_types=%C,
      |        )
      """.trimMargin(),
      PythonSymbol("sunday", "RequestSpec"),
      httpMethod(),
      path,
      renderParameterSpecs(),
      renderRequestPayloadSpec(),
      renderMediaTypes(responseVariants().flatMap { variant -> variant.mediaTypes }.distinct()),
    )

  private fun GeneratedOperation.renderRequestPayloadSpec(): PythonCodeBlock =
    when {
      requestBody == null ->
        PythonCodeBlock.of(
          "            body=None,\n" +
            "            content_types=(),",
        )
      requestBody.payloads.isNotEmpty() ->
        PythonCodeBlock.of("            payload=%L(body),", requestPayloadFunctionName())
      else ->
        PythonCodeBlock.of(
          "            body=body,\n" +
            "            content_types=%C,",
          renderMediaTypes(requestBody.mediaTypes),
        )
    }

  private fun GeneratedOperation.renderParameterSpecs(): PythonCodeBlock {
    val parameters = httpParameters()
    if (parameters.isEmpty() && queryString == null) {
      return PythonCodeBlock.of("()")
    }
    val specs =
      parameters.map { it.renderParameterSpec() } +
        listOfNotNull(
          queryString?.let {
            PythonCodeBlock.of(
              "                %T(\n" +
                "                    name=%S,\n" +
                "                    value=%T(query_string),\n" +
                "                    location=%T.QUERY,\n" +
                "                    style=%T.FORM,\n" +
                "                    explode=True,\n" +
                "                ),",
              PythonSymbol("sunday", "ParameterSpec"),
              "",
              PythonSymbol("sunday", "parameter_object"),
              PythonSymbol("sunday", "ParameterLocation"),
              PythonSymbol("sunday", "ParameterStyle"),
            )
          },
        )
    return PythonCodeBlock.of(
      """
      |(
      |%C
      |            )
      """.trimMargin(),
      PythonCodeBlock.join(specs, separator = "\n"),
    )
  }

  private fun GeneratedParameter.renderParameterSpec(): PythonCodeBlock {
    val value = constantValue?.renderPythonValue() ?: PythonCodeBlock.of("%L", name.pythonIdentifierName)
    val encoding = encoding
    return PythonCodeBlock.of(
      """
      |                %T(
      |                    name=%S,
      |                    value=%C,
      |                    location=%T.%L,
      |                    style=%C,
      |                    explode=%L,
      |                    allow_reserved=%L,
      |                    allow_empty_value=%L,
      |                ),
      """.trimMargin(),
      PythonSymbol("sunday", "ParameterSpec"),
      wireName(),
      value,
      PythonSymbol("sunday", "ParameterLocation"),
      location.name,
      encoding?.style.renderParameterStyle(),
      encoding?.explode.pythonBooleanOrNone(),
      encoding?.allowReserved.pythonBoolean(),
      encoding?.allowEmptyValue.pythonBoolean(),
    )
  }

  private fun GeneratedOperation.renderResponseSpecs(): PythonCodeBlock {
    val variants = responseVariants()
    if (variants.isEmpty()) {
      return PythonCodeBlock.of("(%T(status=None, body_expected=False),)", PythonSymbol("sunday", "ResponseSpec"))
    }
    return PythonCodeBlock.of(
      """
      |(
      |%C
      |            )
      """.trimMargin(),
      PythonCodeBlock.join(
        variants.map { variant -> variant.renderResponseSpec(responseDecoderName(variant)) },
        separator = "\n",
      ),
    )
  }

  private fun PythonResponseVariant.renderResponseSpec(decoderName: String): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |                %T(
      |                    status=%L,
      |                    content_types=%C,
      |                    decoder=%L,
      |                    body_expected=%L,
      |                    headers=%C,
      |                ),
      """.trimMargin(),
      PythonSymbol("sunday", "ResponseSpec"),
      response.status?.toString() ?: "None",
      renderMediaTypes(mediaTypes),
      if (hasBody()) decoderName else "None",
      hasBody().pythonBoolean(),
      response.renderResponseHeaderSpecs(),
    )

  private fun GeneratedResponse.renderResponseHeaderSpecs(): PythonCodeBlock {
    if (headers.isEmpty()) {
      return PythonCodeBlock.of("()")
    }
    return PythonCodeBlock.of(
      """
      |(
      |%C
      |                    )
      """.trimMargin(),
      PythonCodeBlock.join(headers.map { it.renderResponseHeaderSpec() }, separator = "\n"),
    )
  }

  private fun GeneratedParameter.renderResponseHeaderSpec(): PythonCodeBlock {
    val repeated = type.kind == GeneratedTypeRef.Kind.ARRAY
    val decodedType = if (repeated) type.arguments.firstOrNull() ?: GeneratedTypeRef.scalar("any") else type
    return PythonCodeBlock.of(
      """
      |                        %T(
      |                            name=%S,
      |                            decoder=%T(%C).validate_python,
      |                            required=%L,
      |                            repeated=%L,
      |                        ),
      """.trimMargin(),
      PythonSymbol("sunday", "ResponseHeaderSpec"),
      wireName(),
      PythonSymbol("pydantic", "TypeAdapter"),
      decodedType.renderClientPythonType(nullable = false),
      required.pythonBoolean(),
      repeated.pythonBoolean(),
    )
  }

  private fun renderMediaTypes(mediaTypes: List<String>): PythonCodeBlock =
    if (mediaTypes.isEmpty()) {
      PythonCodeBlock.of("()")
    } else {
      PythonCodeBlock.of(
        "(%C,)",
        PythonCodeBlock.join(
          mediaTypes.map { mediaType ->
            PythonCodeBlock.of("%T(%S)", PythonSymbol("sunday", "MediaType"), mediaType)
          },
          separator = ", ",
        ),
      )
    }

  private fun GeneratedOperation.renderDecoderFunctions(): PythonCodeBlock? {
    if (streaming != null) {
      val responseType =
        responseVariants().firstNotNullOfOrNull { variant -> variant.type }
          ?: GeneratedTypeRef.scalar("string")
      return PythonCodeBlock.of(
        """
        def %L(event: %T) -> %C:
            if event.data is None:
                raise ValueError("Server-sent events must contain data")
            return %T(%C).validate_json(event.data)
        """.trimIndent(),
        eventDecoderName(),
        PythonSymbol("sunday", "ServerSentEvent"),
        responseType.renderClientPythonType(),
        PythonSymbol("pydantic", "TypeAdapter"),
        responseType.renderClientPythonType(nullable = false),
      )
    }

    val decoders =
      responseVariants().mapNotNull { variant ->
        val type = variant.type?.takeUnless { it.isNil() } ?: return@mapNotNull null
        PythonCodeBlock.of(
          """
          def %L(value: object) -> %C:
              return %T(%C).validate_python(value)
          """.trimIndent(),
          responseDecoderName(variant),
          type.renderClientPythonType(),
          PythonSymbol("pydantic", "TypeAdapter"),
          type.renderClientPythonType(nullable = false),
        )
      }
    return decoders.takeIf { it.isNotEmpty() }?.let { PythonCodeBlock.join(it, separator = "\n\n") }
  }

  private fun GeneratedOperation.renderSignatureParameters(): PythonCodeBlock {
    val parameters =
      httpParameters()
        .filter { parameter -> parameter.constantValue == null }
    val required =
      parameters
        .filter { parameter -> parameter.location == GeneratedParameter.Location.PATH }
        .map { it.renderRequiredParameter() } +
        listOfNotNull(requestBody?.renderRequestBodyParameter()) +
        listOfNotNull(
          queryString?.let { type ->
            PythonCodeBlock.of("        query_string: %C,", type.renderClientPythonType(nullable = false))
          },
        ) +
        parameters
          .filter { parameter -> parameter.required && parameter.location != GeneratedParameter.Location.PATH }
          .map { it.renderRequiredParameter() }
    val optional =
      parameters.filterNot { parameter ->
        parameter.required || parameter.location == GeneratedParameter.Location.PATH
      }

    return PythonCodeBlock.join(
      required + optional.map { it.renderOptionalParameter() },
      separator = "\n",
    )
  }

  private fun GeneratedParameter.renderRequiredParameter(): PythonCodeBlock =
    PythonCodeBlock.of("        %L: %C,", name.pythonIdentifierName, type.renderClientPythonType(nullable = false))

  private fun GeneratedPayload.renderRequestBodyParameter(): PythonCodeBlock =
    if (isPythonStreamingRequestBody) {
      PythonCodeBlock.of("        body: %T,", PythonSymbol("sunday", "StreamingBody"))
    } else {
      PythonCodeBlock.of("        body: %C,", renderClientBodyType())
    }

  private fun GeneratedPayload.renderClientBodyType(): PythonCodeBlock =
    PythonCodeBlock.join(
      requestVariants()
        .map { variant -> variant.renderClientType() }
        .distinct()
        .map { bodyType -> bodyType },
      separator = " | ",
    )

  private fun GeneratedParameter.renderOptionalParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %C = %C,",
      name.pythonIdentifierName,
      if (type.nullable) {
        type.renderClientPythonType()
      } else {
        PythonCodeBlock.of("%C | None", type.renderClientPythonType(nullable = false))
      },
      defaultValue?.renderPythonValue() ?: PythonCodeBlock.of("None"),
    )

  private fun GeneratedOperation.renderSuccessType(): PythonCodeBlock {
    val types = responseVariants().mapNotNull { variant -> variant.type }.distinct()
    if (types.isEmpty()) {
      return PythonCodeBlock.of("None")
    }
    return PythonCodeBlock.join(types.map { it.renderClientPythonType() }, separator = " | ")
  }

  private fun GeneratedOperation.renderRequestBodyType(): PythonCodeBlock =
    when {
      requestBody == null -> PythonCodeBlock.of("None")
      requestBody.isPythonStreamingRequestBody -> PythonCodeBlock.of("%T", PythonSymbol("sunday", "StreamingBody"))
      else -> requestBody.renderClientBodyType()
    }

  private fun GeneratedOperation.renderRequestPayloadFunction(): PythonCodeBlock? {
    val requestBody = requestBody?.takeIf { payload -> payload.payloads.isNotEmpty() } ?: return null
    val bodyType = requestBody.renderClientBodyType()
    val attempts = requestBody.requestVariants().map { variant -> variant.renderValidationAttempt() }
    return PythonCodeBlock.of(
      "def %L(body: %C) -> %T[%C]:\n" +
        "    validated: %C\n" +
        "%C\n" +
        "    raise ValueError(%S)",
      requestPayloadFunctionName(),
      bodyType,
      PythonSymbol("sunday", "RequestPayloadSpec"),
      bodyType,
      bodyType,
      PythonCodeBlock.join(attempts, separator = "\n"),
      "Request body does not match a declared payload for operation '$id'",
    )
  }

  private fun GeneratedOperation.requestPayloadFunctionName(): String = "_request_payload_${id.pythonIdentifierName}"

  private fun GeneratedPayload.requestVariants(): List<PythonRequestVariant> =
    if (payloads.isEmpty()) {
      listOf(PythonRequestVariant(type, mediaTypes))
    } else {
      payloads.map { payload -> PythonRequestVariant(payload.type, payload.mediaTypes) }
    }

  private fun PythonRequestVariant.renderClientType(): PythonCodeBlock =
    when {
      mediaTypes.any { mediaType -> mediaType.startsWith("multipart/", ignoreCase = true) } ->
        PythonCodeBlock.of("%T", PythonSymbol("sunday", "MultipartBody"))
      mediaTypes.any { mediaType -> mediaType.equals("application/json-patch+json", ignoreCase = true) } ->
        PythonCodeBlock.of("%T", PythonSymbol("sunday", "PatchDocument"))
      else -> type.renderClientPythonType(nullable = false)
    }

  private fun PythonRequestVariant.renderValidationAttempt(): PythonCodeBlock {
    val runtimeType =
      when {
        mediaTypes.any { mediaType -> mediaType.startsWith("multipart/", ignoreCase = true) } ->
          PythonSymbol("sunday", "MultipartBody")
        mediaTypes.any { mediaType -> mediaType.equals("application/json-patch+json", ignoreCase = true) } ->
          PythonSymbol("sunday", "PatchDocument")
        else -> null
      }
    return if (runtimeType != null) {
      PythonCodeBlock.of(
        "    if isinstance(body, %T):\n" +
          "        validated = body\n" +
          "        return %T(body=validated, content_types=%C)",
        runtimeType,
        PythonSymbol("sunday", "RequestPayloadSpec"),
        renderMediaTypes(mediaTypes),
      )
    } else {
      val type = type.renderClientPythonType(nullable = false)
      PythonCodeBlock.of(
        "    try:\n" +
          "        validated = %T(%C).validate_python(body)\n" +
          "    except %T:\n" +
          "        pass\n" +
          "    else:\n" +
          "        return %T(body=validated, content_types=%C)",
        PythonSymbol("pydantic", "TypeAdapter"),
        type,
        PythonSymbol("pydantic", "ValidationError"),
        PythonSymbol("sunday", "RequestPayloadSpec"),
        renderMediaTypes(mediaTypes),
      )
    }
  }

  private fun GeneratedTypeRef.renderClientPythonType(nullable: Boolean = true): PythonCodeBlock {
    val type =
      when (kind) {
        GeneratedTypeRef.Kind.NAMED -> PythonCodeBlock.of("%T", PythonSymbol(".models", name.pythonTypeName))
        GeneratedTypeRef.Kind.ARRAY ->
          PythonCodeBlock.of(
            "%L[%C]",
            if (collection?.name == "SET") "set" else "list",
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
            PythonCodeBlock.join(arguments.map { it.renderClientPythonType(nullable = false) }, separator = " | ")
          }
        GeneratedTypeRef.Kind.SCALAR -> renderPythonType(nullable = false)
      }
    return if (nullable && this.nullable) PythonCodeBlock.of("%C | None", type) else type
  }

  private fun GeneratedOperation.successResponses(): List<GeneratedResponse> =
    responses.filter { response -> response.status == null || response.status in 200..299 }

  private fun GeneratedOperation.responseDecoderName(variant: PythonResponseVariant): String =
    "_decode_${id.pythonIdentifierName}_${variant.response.status ?: "default"}_${variant.responseIndex}_${variant.payloadIndex}"

  private fun GeneratedOperation.eventDecoderName(): String = "_decode_${id.pythonIdentifierName}_event"

  private fun PythonResponseVariant.hasBody(): Boolean = type != null && !type.isNil()

  private fun GeneratedOperation.responseVariants(): List<PythonResponseVariant> =
    successResponses().flatMapIndexed { responseIndex, response ->
      if (response.payloads.isEmpty()) {
        listOf(PythonResponseVariant(response, response.type, response.mediaTypes, responseIndex, 0))
      } else {
        response.payloads.mapIndexed { payloadIndex, payload ->
          PythonResponseVariant(response, payload.type, payload.mediaTypes, responseIndex, payloadIndex)
        }
      }
    }

  private fun GeneratedTypeRef.isNil(): Boolean = kind == GeneratedTypeRef.Kind.SCALAR && name == "nil"

  private fun GeneratedOperation.httpMethod(): String =
    if (streaming != null && method.equals("SUBSCRIBE", ignoreCase = true)) "GET" else method.uppercase()

  private fun GeneratedOperation.httpParameters(): List<GeneratedParameter> =
    parameters.filter { parameter ->
      parameter.location in
        setOf(
          GeneratedParameter.Location.PATH,
          GeneratedParameter.Location.QUERY,
          GeneratedParameter.Location.HEADER,
          GeneratedParameter.Location.COOKIE,
        )
    }

  private fun GeneratedOperation.hasSignatureParameters(): Boolean =
    httpParameters().any { parameter -> parameter.constantValue == null } || requestBody != null || queryString != null

  private fun GeneratedParameter.wireName(): String = serializationName ?: name

  private fun String?.renderParameterStyle(): PythonCodeBlock =
    when (this) {
      null -> PythonCodeBlock.of("None")
      "simple" -> PythonCodeBlock.of("%T.SIMPLE", PythonSymbol("sunday", "ParameterStyle"))
      "label" -> PythonCodeBlock.of("%T.LABEL", PythonSymbol("sunday", "ParameterStyle"))
      "matrix" -> PythonCodeBlock.of("%T.MATRIX", PythonSymbol("sunday", "ParameterStyle"))
      "form" -> PythonCodeBlock.of("%T.FORM", PythonSymbol("sunday", "ParameterStyle"))
      "spaceDelimited" -> PythonCodeBlock.of("%T.SPACE_DELIMITED", PythonSymbol("sunday", "ParameterStyle"))
      "pipeDelimited" -> PythonCodeBlock.of("%T.PIPE_DELIMITED", PythonSymbol("sunday", "ParameterStyle"))
      "deepObject" -> PythonCodeBlock.of("%T.DEEP_OBJECT", PythonSymbol("sunday", "ParameterStyle"))
      else -> genError("Unsupported Python parameter style '$this'")
    }

  private fun Any.renderPythonValue(): PythonCodeBlock =
    when (this) {
      is Boolean -> PythonCodeBlock.of(pythonBoolean())
      is Number -> PythonCodeBlock.of("%L", this)
      is String -> PythonCodeBlock.of("%S", this)
      else -> PythonCodeBlock.of("None")
    }

  private fun Boolean?.pythonBoolean(): String = if (this == true) "True" else "False"

  private fun Boolean.pythonBoolean(): String = if (this) "True" else "False"

  private fun Boolean?.pythonBooleanOrNone(): String =
    when (this) {
      true -> "True"
      false -> "False"
      else -> "None"
    }

  private val GeneratedPayload?.isPythonStreamingRequestBody: Boolean
    get() = this?.streaming?.enabledFor(GenerationMode.Client) == true
}

private data class PythonResponseVariant(
  val response: GeneratedResponse,
  val type: GeneratedTypeRef?,
  val mediaTypes: List<String>,
  val responseIndex: Int,
  val payloadIndex: Int,
)

private data class PythonRequestVariant(
  val type: GeneratedTypeRef,
  val mediaTypes: List<String>,
)
