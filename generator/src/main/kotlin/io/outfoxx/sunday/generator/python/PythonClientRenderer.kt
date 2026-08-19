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
import io.outfoxx.sunday.generator.ir.GeneratedExchange
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.enabledFor
import io.outfoxx.sunday.generator.ir.emit.flattenedUnionTypes

/** Renders declarative transport-neutral Sunday Python clients from generated IR. */
class PythonClientRenderer(
  private val packageName: String,
  private val registerProblems: Boolean = false,
  models: List<GeneratedModel> = emptyList(),
  private val defaultMediaTypes: List<String> = listOf("application/json"),
) {

  private val modelIndex = models.associateBy { model -> model.name }

  /** Renders one generated service client. */
  fun renderService(service: GeneratedService): PythonModule {
    val module = PythonModuleBuilder("$packageName/${service.pythonServiceModuleName}.py")
    val className = "${service.pythonServiceBaseName.pythonTypeName}Client"
    val mediaSelection = service.defaultMediaSelection(defaultMediaTypes)
    val problemRegistration =
      if (registerProblems) {
        PythonCodeBlock.of(
          "\n        %T(self.transport)",
          PythonSymbol(".problems", "register_problems"),
        )
      } else {
        PythonCodeBlock.of("")
      }

    module.addExport(className)
    service.adapterTypes().forEach { type -> module.addCode(type.renderAdapterConstant()) }
    service.operations.forEach { operation ->
      operation.renderDecoderFunctions()?.let(module::addCode)
      operation.renderResponseSpecConstant()?.let(module::addCode)
      operation.renderRequestPayloadFunction()?.let(module::addCode)
    }
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L[TransportRequestT, TransportResponseT]:
            ${"\"\"\"Client operations for the %L service.\"\"\""}

            def __init__(
                self,
                transport: %T[TransportRequestT, TransportResponseT],
                *,
                default_content_types: %T[%T] = %C,
                default_accept_types: %T[%T] = %C,
            ) -> None:
                self.transport = transport
                self.default_content_types = tuple(default_content_types)
                self.default_accept_types = tuple(default_accept_types)%C

        %C
        """.trimIndent(),
        className,
        service.pythonServiceBaseName,
        PythonSymbol("sunday", "Transport"),
        PythonSymbol("collections.abc", "Sequence"),
        PythonSymbol("sunday", "MediaType"),
        renderMediaTypes(mediaSelection.contentTypes),
        PythonSymbol("collections.abc", "Sequence"),
        PythonSymbol("sunday", "MediaType"),
        renderMediaTypes(mediaSelection.acceptTypes),
        problemRegistration,
        PythonCodeBlock.join(
          service.operations.map {
            it.renderOperationMethod(mediaSelection.contentTypes, mediaSelection.acceptTypes)
          },
          separator = "\n\n",
        ),
      ),
    )
    return module.build()
  }

  private fun GeneratedOperation.renderOperationMethod(
    defaultContentTypes: List<String>,
    defaultAcceptTypes: List<String>,
  ): PythonCodeBlock {
    val signature = renderSignatureParameters()
    val responseType = renderSuccessType()
    val operationType =
      when {
        streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE -> PythonSymbol("sunday", "EventSource")
        streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM -> PythonSymbol("sunday", "EventStream")
        nullify != null -> PythonSymbol("sunday", "NullableOperation")
        requestBody.isPythonStreamingRequestBody -> PythonSymbol("sunday", "StreamingOperation")
        else -> PythonSymbol("sunday", "Operation")
      }
    val operationReturnType =
      when {
        streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE -> PythonCodeBlock.of("%T", operationType)
        streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM ->
          PythonCodeBlock.of(
            "%T[%C]",
            operationType,
            responseType,
          )
        exchange == GeneratedExchange.REQUEST -> PythonCodeBlock.of("TransportRequestT")
        exchange == GeneratedExchange.RESPONSE -> PythonCodeBlock.of("TransportResponseT")
        else -> PythonCodeBlock.of("%T[%C, TransportRequestT, TransportResponseT]", operationType, responseType)
      }
    val body =
      if (streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE) {
        PythonCodeBlock.of(
          "        request_spec: %T[%C] = %C\n" +
            "        return self.transport.event_source(request_spec)",
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(defaultContentTypes, defaultAcceptTypes),
        )
      } else if (streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM) {
        PythonCodeBlock.of(
          """
          |        request_spec: %T[%C] = %C
          |        return self.transport.event_stream(request_spec, %L)
          """.trimMargin(),
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(defaultContentTypes, defaultAcceptTypes),
          eventDecoderName(),
        )
      } else if (exchange != null) {
        val transportCall =
          when (exchange) {
            GeneratedExchange.REQUEST -> "        return await self.transport.transport_request(request_spec)"
            GeneratedExchange.RESPONSE ->
              "        request = await self.transport.transport_request(request_spec)\n" +
                "        return await self.transport.transport_response(request)"
          }
        PythonCodeBlock.of(
          "        request_spec: %T[%C] = %C\n%L",
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(defaultContentTypes, defaultAcceptTypes),
          transportCall,
        )
      } else {
        PythonCodeBlock.of(
          """
          |        request_spec: %T[%C] = %C
          |        operation_spec: %T[%C, %C] = %T(
          |            request=request_spec,
          |            responses=%L,
          |        )
          |%C
          """.trimMargin(),
          PythonSymbol("sunday", "RequestSpec"),
          renderRequestBodyType(),
          renderRequestSpec(defaultContentTypes, defaultAcceptTypes),
          PythonSymbol("sunday", "OperationSpec"),
          renderRequestBodyType(),
          responseType,
          PythonSymbol("sunday", "OperationSpec"),
          responseSpecsName(),
          renderOperationConstruction(operationType),
        )
      }

    val functionPrefix = if (exchange != null && streaming == null) "async def" else "def"

    return if (!hasSignatureParameters()) {
      PythonCodeBlock.of(
        """
            %L %L(self) -> %C:
                ${"\"\"\"Create the %L operation.\"\"\""}
        %C
        """.trimIndent(),
        functionPrefix,
        id.pythonIdentifierName,
        operationReturnType,
        id,
        body,
      )
    } else {
      PythonCodeBlock.of(
        """
            %L %L(
                self,
        %C
            ) -> %C:
                ${"\"\"\"Create the %L operation.\"\"\""}
        %C
        """.trimIndent(),
        functionPrefix,
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
          "            self.transport,\n" +
          "            operation_spec,\n" +
          "            %T(statuses=%C, problem_types=%C),\n" +
          "        )",
        operationType,
        PythonSymbol("sunday", "NullifySpec"),
        renderTuple(nullify.statuses.map { status -> PythonCodeBlock.of("%L", status) }),
        renderTuple(problemTypes),
      )
    } ?: PythonCodeBlock.of("        return %T(self.transport, operation_spec)", operationType)

  private fun renderTuple(values: List<PythonCodeBlock>): PythonCodeBlock =
    if (values.isEmpty()) {
      PythonCodeBlock.of("()")
    } else {
      PythonCodeBlock.of("(%C,)", PythonCodeBlock.join(values, separator = ", "))
    }

  private fun GeneratedOperation.renderRequestSpec(
    defaultContentTypes: List<String>,
    defaultAcceptTypes: List<String>,
  ): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      |%T(
      |            method=%S,
      |            path_template=%C,
      |%C%C%C%C        )
      """.trimMargin(),
      PythonSymbol("sunday", "RequestSpec"),
      httpMethod(),
      renderPathTemplate(),
      renderTemplateParameterArgument(),
      renderParameterArgument(),
      renderRequestPayloadSpec(defaultContentTypes),
      renderAcceptTypes(defaultAcceptTypes),
    )

  private fun GeneratedOperation.renderRequestPayloadSpec(defaultContentTypes: List<String>): PythonCodeBlock =
    when {
      requestBody == null -> PythonCodeBlock.of("")
      requestBody.payloads.isNotEmpty() ->
        PythonCodeBlock.of("            payload=%L(body),\n", requestPayloadFunctionName())
      else ->
        PythonCodeBlock.of(
          "            body=body,\n" +
            "            content_types=%C,\n",
          if (requestBody.mediaTypes == defaultContentTypes) {
            PythonCodeBlock.of("self.default_content_types")
          } else {
            renderMediaTypes(requestBody.mediaTypes)
          },
        )
    }

  private fun GeneratedOperation.renderAcceptTypes(defaultAcceptTypes: List<String>): PythonCodeBlock {
    val acceptTypes = responseVariants().flatMap { variant -> variant.mediaTypes }.distinct()
    if (acceptTypes.isEmpty()) {
      return PythonCodeBlock.of("")
    }
    return PythonCodeBlock.of(
      "            accept_types=%C,\n",
      if (acceptTypes == defaultAcceptTypes) {
        PythonCodeBlock.of("self.default_accept_types")
      } else {
        renderMediaTypes(acceptTypes)
      },
    )
  }

  private fun GeneratedOperation.renderParameterArgument(): PythonCodeBlock =
    if (requestParameters().isEmpty() && queryString == null) {
      PythonCodeBlock.of("")
    } else {
      PythonCodeBlock.of("            parameters=%C,\n", renderParameterSpecs())
    }

  private fun GeneratedOperation.renderParameterSpecs(): PythonCodeBlock {
    val parameters = requestParameters()
    if (parameters.isEmpty() && queryString == null) {
      return PythonCodeBlock.of("()")
    }
    if (parameters.size == 1 && queryString == null && parameters.single().usesDefaultEncoding()) {
      return PythonCodeBlock.of("(%C,)", parameters.single().renderInlineParameterSpec())
    }
    val specs =
      parameters.map { it.renderParameterSpec() } +
        listOfNotNull(
          queryString?.let {
            PythonCodeBlock.of(
              "                %T(name=%S, value=%T(query_string), location=%T.QUERY),",
              PythonSymbol("sunday", "ParameterSpec"),
              "",
              PythonSymbol("sunday", "parameter_object"),
              PythonSymbol("sunday", "ParameterLocation"),
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

  private fun GeneratedParameter.usesDefaultEncoding(): Boolean =
    encoding?.let { encoding ->
      encoding.style == null &&
        encoding.explode == null &&
        encoding.allowReserved != true &&
        encoding.allowEmptyValue != true
    } ?: true

  private fun GeneratedParameter.renderInlineParameterSpec(): PythonCodeBlock {
    val value = constantValue?.renderPythonValue() ?: PythonCodeBlock.of("%L", name.pythonIdentifierName)
    return PythonCodeBlock.of(
      "%T(name=%S, value=%C, location=%T.%L)",
      PythonSymbol("sunday", "ParameterSpec"),
      wireName(),
      value,
      PythonSymbol("sunday", "ParameterLocation"),
      location.name,
    )
  }

  private fun GeneratedParameter.renderParameterSpec(): PythonCodeBlock {
    val value = constantValue?.renderPythonValue() ?: PythonCodeBlock.of("%L", name.pythonIdentifierName)
    val encoding = encoding
    val optionalArguments =
      listOfNotNull(
        encoding?.style?.let { style -> PythonCodeBlock.of("style=%C", style.renderParameterStyle()) },
        encoding?.explode?.let { explode -> PythonCodeBlock.of("explode=%L", explode.pythonBoolean()) },
        encoding?.allowReserved?.takeIf { it }?.let { PythonCodeBlock.of("allow_reserved=True") },
        encoding?.allowEmptyValue?.takeIf { it }?.let { PythonCodeBlock.of("allow_empty_value=True") },
      )
    if (optionalArguments.isEmpty()) {
      return PythonCodeBlock.of(
        "                %T(name=%S, value=%C, location=%T.%L),",
        PythonSymbol("sunday", "ParameterSpec"),
        wireName(),
        value,
        PythonSymbol("sunday", "ParameterLocation"),
        location.name,
      )
    }
    return PythonCodeBlock.of(
      """
      |                %T(
      |                    name=%S,
      |                    value=%C,
      |                    location=%T.%L,
      |                    %C,
      |                ),
      """.trimMargin(),
      PythonSymbol("sunday", "ParameterSpec"),
      wireName(),
      value,
      PythonSymbol("sunday", "ParameterLocation"),
      location.name,
      PythonCodeBlock.join(optionalArguments, separator = ",\n                    "),
    )
  }

  private fun GeneratedOperation.renderResponseSpecs(): PythonCodeBlock {
    val variants = responseVariants()
    if (variants.isEmpty()) {
      return PythonCodeBlock.of("(%T(status=None, body_expected=False),)", PythonSymbol("sunday", "ResponseSpec"))
    }
    val specs = variants.map { variant -> variant.renderResponseSpec() }
    val singleVariant = variants.singleOrNull()
    if (singleVariant != null && !singleVariant.hasBody() && singleVariant.response.headers.isEmpty()) {
      return PythonCodeBlock.of("(%C,)", specs.single())
    }
    return PythonCodeBlock.of(
      """
      |(
      |    %C,
      |)
      """.trimMargin(),
      PythonCodeBlock.join(
        specs,
        separator = ",\n    ",
      ),
    )
  }

  private fun GeneratedOperation.renderResponseSpecConstant(): PythonCodeBlock? {
    if (streaming != null || exchange != null) {
      return null
    }
    return PythonCodeBlock.of(
      "%L: tuple[%T[%C], ...] = %C",
      responseSpecsName(),
      PythonSymbol("sunday", "ResponseSpec"),
      renderSuccessType(),
      renderResponseSpecs(),
    )
  }

  private fun GeneratedOperation.responseSpecsName(): String = "_${id.pythonIdentifierName}_responses"

  private fun PythonResponseVariant.renderResponseSpec(): PythonCodeBlock {
    val arguments =
      buildList {
        add(PythonCodeBlock.of("status=%L", response.status?.toString() ?: "None"))
        if (mediaTypes.isNotEmpty()) {
          add(PythonCodeBlock.of("content_types=%C", renderMediaTypes(mediaTypes)))
        }
        if (hasBody()) {
          add(PythonCodeBlock.of("decoder=%L.validate_python", checkNotNull(type).adapterName()))
        } else {
          add(PythonCodeBlock.of("body_expected=False"))
        }
        if (response.headers.isNotEmpty()) {
          add(PythonCodeBlock.of("headers=%C", response.renderResponseHeaderSpecs()))
        }
      }
    return if (arguments.size <= 2 && response.headers.isEmpty()) {
      PythonCodeBlock.of(
        "%T(%C)",
        PythonSymbol("sunday", "ResponseSpec"),
        PythonCodeBlock.join(arguments, separator = ", "),
      )
    } else {
      PythonCodeBlock.of(
        "%T(\n" +
          "        %C,\n" +
          "    )",
        PythonSymbol("sunday", "ResponseSpec"),
        PythonCodeBlock.join(arguments, separator = ",\n        "),
      )
    }
  }

  private fun GeneratedResponse.renderResponseHeaderSpecs(): PythonCodeBlock {
    if (headers.isEmpty()) {
      return PythonCodeBlock.of("()")
    }
    val specs = headers.map { it.renderResponseHeaderSpec() }
    if (headers.size == 1 && !headers.single().required && headers.single().type.kind != GeneratedTypeRef.Kind.ARRAY) {
      return PythonCodeBlock.of("(%C,)", specs.single())
    }
    return PythonCodeBlock.of(
      """
      |(
      |            %C,
      |        )
      """.trimMargin(),
      PythonCodeBlock.join(specs, separator = ",\n            "),
    )
  }

  private fun GeneratedParameter.renderResponseHeaderSpec(): PythonCodeBlock {
    val repeated = type.kind == GeneratedTypeRef.Kind.ARRAY
    val decodedType = if (repeated) type.arguments.firstOrNull() ?: GeneratedTypeRef.scalar("any") else type
    val optionalArguments =
      listOfNotNull(
        required.takeIf { it }?.let { PythonCodeBlock.of("required=True") },
        repeated.takeIf { it }?.let { PythonCodeBlock.of("repeated=True") },
      )
    if (optionalArguments.isEmpty()) {
      return PythonCodeBlock.of(
        "%T(name=%S, decoder=%L.validate_python)",
        PythonSymbol("sunday", "ResponseHeaderSpec"),
        wireName(),
        decodedType.adapterName(),
      )
    }
    return PythonCodeBlock.of(
      """
      |%T(
      |                name=%S,
      |                decoder=%L.validate_python,
      |                %C,
      |            )
      """.trimMargin(),
      PythonSymbol("sunday", "ResponseHeaderSpec"),
      wireName(),
      decodedType.adapterName(),
      PythonCodeBlock.join(optionalArguments, separator = ",\n                "),
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

  private fun GeneratedService.adapterTypes(): List<GeneratedTypeRef> =
    operations
      .flatMap { operation ->
        val responseTypes =
          operation.responseVariants().mapNotNull { variant ->
            variant.type?.takeUnless { it.isNil() }
          }
        val eventTypes = responseTypes.flatMap { type -> type.eventDiscriminatorMappings().map { it.second } }
        val headerTypes =
          operation.responses.flatMap { response ->
            response.headers.map { header ->
              if (header.type.kind == GeneratedTypeRef.Kind.ARRAY) {
                header.type.arguments.firstOrNull() ?: GeneratedTypeRef.scalar("any")
              } else {
                header.type
              }
            }
          }
        val requestTypes =
          operation.requestBody
            ?.requestVariants()
            .orEmpty()
            .filter { variant -> variant.runtimeType() == null }
            .map { variant -> variant.type }
        responseTypes + eventTypes + headerTypes + requestTypes
      }.distinct()

  private fun GeneratedTypeRef.renderAdapterConstant(): PythonCodeBlock {
    val name = adapterName()
    val type = renderClientPythonType(nullable = false)
    return if (kind != GeneratedTypeRef.Kind.UNION || arguments.size <= 1) {
      PythonCodeBlock.of(
        "%L: %T[%C] = %T(%C)",
        name,
        PythonSymbol("pydantic", "TypeAdapter"),
        type,
        PythonSymbol("pydantic", "TypeAdapter"),
        type,
      )
    } else {
      PythonCodeBlock.of(
        "%L: %T[%C] = %T(\n    %C\n)",
        name,
        PythonSymbol("pydantic", "TypeAdapter"),
        type,
        PythonSymbol("pydantic", "TypeAdapter"),
        type,
      )
    }
  }

  private fun GeneratedTypeRef.adapterName(): String =
    "_" +
      when (kind) {
        GeneratedTypeRef.Kind.NAMED -> "named_$name"
        GeneratedTypeRef.Kind.SCALAR -> "scalar_$name"
        GeneratedTypeRef.Kind.ARRAY -> "array_${arguments.firstOrNull()?.adapterNamePart() ?: "object"}"
        GeneratedTypeRef.Kind.MAP -> "map_${arguments.firstOrNull()?.adapterNamePart() ?: "object"}"
        GeneratedTypeRef.Kind.UNION -> "union_${arguments.joinToString("_") { it.adapterNamePart() }}"
      }.pythonIdentifierName +
      "_adapter"

  private fun GeneratedTypeRef.adapterNamePart(): String =
    when (kind) {
      GeneratedTypeRef.Kind.NAMED, GeneratedTypeRef.Kind.SCALAR -> name
      GeneratedTypeRef.Kind.ARRAY -> "array_${arguments.firstOrNull()?.adapterNamePart() ?: "object"}"
      GeneratedTypeRef.Kind.MAP -> "map_${arguments.firstOrNull()?.adapterNamePart() ?: "object"}"
      GeneratedTypeRef.Kind.UNION -> "union_${arguments.joinToString("_") { it.adapterNamePart() }}"
    }

  private fun GeneratedOperation.renderDecoderFunctions(): PythonCodeBlock? {
    if (streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM) {
      val responseType =
        responseVariants().firstNotNullOfOrNull { variant -> variant.type }
          ?: GeneratedTypeRef.scalar("string")
      if (streaming.eventMode == GeneratedStreaming.EventMode.DISCRIMINATED) {
        val mappings = responseType.eventDiscriminatorMappings()
        val cases =
          mappings.map { (event, type) ->
            PythonCodeBlock.of(
              "    if event.event == %S:\n" +
                "        return %L.validate_json(event.data)",
              event,
              type.adapterName(),
            )
          }
        return PythonCodeBlock.of(
          "def %L(event: %T) -> %C | None:\n" +
            "    if event.data is None:\n" +
            "        raise ValueError(%S)\n" +
            "%C\n" +
            "    %T(__name__).warning(%S, event.event)\n" +
            "    return None",
          eventDecoderName(),
          PythonSymbol("sunday", "ServerSentEvent"),
          responseType.renderClientPythonType(),
          "Server-sent events must contain data",
          PythonCodeBlock.join(cases, separator = "\n"),
          PythonSymbol("logging", "getLogger"),
          "Unknown event type, ignoring event: event=%s",
        )
      }
      return PythonCodeBlock.of(
        """
        def %L(event: %T) -> %C:
            if event.data is None:
                raise ValueError("Server-sent events must contain data")
            return %L.validate_json(event.data)
        """.trimIndent(),
        eventDecoderName(),
        PythonSymbol("sunday", "ServerSentEvent"),
        responseType.renderClientPythonType(),
        responseType.adapterName(),
      )
    }

    return null
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
    val runtimeType = runtimeType()
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
      PythonCodeBlock.of(
        "    try:\n" +
          "        validated = %L.validate_python(body)\n" +
          "    except %T:\n" +
          "        pass\n" +
          "    else:\n" +
          "        return %T(body=validated, content_types=%C)",
        this.type.adapterName(),
        PythonSymbol("pydantic", "ValidationError"),
        PythonSymbol("sunday", "RequestPayloadSpec"),
        renderMediaTypes(mediaTypes),
      )
    }
  }

  private fun PythonRequestVariant.runtimeType(): PythonSymbol? =
    when {
      mediaTypes.any { mediaType -> mediaType.startsWith("multipart/", ignoreCase = true) } ->
        PythonSymbol("sunday", "MultipartBody")
      mediaTypes.any { mediaType -> mediaType.equals("application/json-patch+json", ignoreCase = true) } ->
        PythonSymbol("sunday", "PatchDocument")
      else -> null
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
    when {
      method.equals("SUBSCRIBE", ignoreCase = true) -> "GET"
      method.equals("PUBLISH", ignoreCase = true) -> "POST"
      else -> method.uppercase()
    }

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

  private fun GeneratedOperation.requestParameters(): List<GeneratedParameter> {
    val templateVariables = rfc6570Variables()
    return httpParameters().filterNot { parameter -> parameter.wireName() in templateVariables }
  }

  private fun GeneratedOperation.renderPathTemplate(): PythonCodeBlock =
    if (rfc6570Variables().isEmpty()) {
      PythonCodeBlock.of("%S", path)
    } else {
      PythonCodeBlock.of("%T(%S)", PythonSymbol("sunday", "URITemplate"), path)
    }

  private fun GeneratedOperation.renderTemplateParameterArgument(): PythonCodeBlock {
    val variables = rfc6570Variables()
    if (variables.isEmpty()) {
      return PythonCodeBlock.of("")
    }
    val values =
      httpParameters()
        .filter { parameter -> parameter.wireName() in variables }
        .map { parameter ->
          val value =
            parameter.constantValue?.renderPythonValue()
              ?: PythonCodeBlock.of("%L", parameter.name.pythonIdentifierName)
          PythonCodeBlock.of("%S: %C", parameter.wireName(), value)
        }
    return PythonCodeBlock.of(
      "            template_parameters={%C},\n",
      PythonCodeBlock.join(values, separator = ", "),
    )
  }

  private fun GeneratedOperation.rfc6570Variables(): Set<String> =
    RFC6570_EXPRESSION
      .findAll(path)
      .map { match -> match.groupValues[1] }
      .filter { expression -> expression.isRfc6570Expression() }
      .flatMap { expression ->
        expression
          .trimStart('+', '#', '.', '/', ';', '?', '&')
          .splitToSequence(',')
          .map { variable -> variable.substringBefore('*').substringBefore(':') }
      }.toSet()

  private fun String.isRfc6570Expression(): Boolean =
    firstOrNull() in setOf('+', '#', '.', '/', ';', '?', '&') || contains(',') || contains('*') || contains(':')

  private fun GeneratedTypeRef.eventDiscriminatorMappings(): List<Pair<String, GeneratedTypeRef>> {
    if (kind == GeneratedTypeRef.Kind.NAMED) {
      val mappings = modelIndex[name]?.discriminatorMappings.orEmpty()
      if (mappings.isNotEmpty()) {
        return mappings.toList()
      }
    }
    return flattenedUnionTypes()
      .filter { type -> type.kind == GeneratedTypeRef.Kind.NAMED }
      .map { type ->
        (modelIndex[type.name]?.discriminatorValue ?: type.name.pythonTypeName) to type
      }
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

  private companion object {
    val RFC6570_EXPRESSION = Regex("\\{([^}]+)}")
  }
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
