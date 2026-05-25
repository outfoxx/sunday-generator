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

/** Renders Litestar server stubs from generated IR. */
class PythonLitestarRenderer(
  private val packageName: String,
) {

  /** Renders a Litestar service protocol and router factory module. */
  fun renderService(service: GeneratedService): PythonModule {
    val moduleName = service.pythonServiceServerModuleName
    val module = PythonModuleBuilder("$packageName/$moduleName.py")
    val serviceName = "${service.pythonServiceBaseName.pythonTypeName}Service"
    val routerFactoryName = service.pythonServiceRouterFactoryName

    module.addExport(serviceName)
    module.addExport(routerFactoryName)
    module.addCode(service.renderServiceProtocol(serviceName))
    module.addCode(service.renderRouterFactory(serviceName, routerFactoryName))
    if (service.operations.any { operation -> operation.streaming != null }) {
      module.addCode(renderServerSentEventsHelper())
    }

    return module.build()
  }

  private fun renderServerSentEventsHelper(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      async def _server_sent_events(events: %T[%T]) -> %T[str]:
          async for event in events:
              yield event.model_dump_json(by_alias=True)
      """.trimIndent(),
      PythonSymbol("collections.abc", "AsyncIterable"),
      PythonSymbol("pydantic", "BaseModel"),
      PythonSymbol("collections.abc", "AsyncIterator"),
    )

  private fun GeneratedService.renderServiceProtocol(serviceName: String): PythonCodeBlock {
    val methods = PythonCodeBlock.join(operations.map { operation -> operation.renderProtocolMethod() }, "\n\n")

    return PythonCodeBlock.of(
      """
      class %L(%T):
          ${"\"\"\"Application implementation contract for the %L service.\"\"\""}

      %C
      """.trimIndent(),
      serviceName,
      PythonSymbol("typing", "Protocol"),
      pythonServiceBaseName,
      methods,
    )
  }

  private fun GeneratedService.renderRouterFactory(
    serviceName: String,
    routerFactoryName: String,
  ): PythonCodeBlock {
    val handlers = PythonCodeBlock.join(operations.map { operation -> operation.renderRouteHandler() }, "\n\n")

    return PythonCodeBlock.of(
      """
      def %L(service: %L) -> %T:
          ${"\"\"\"Create a Litestar router for the %L service.\n\n          Configure Litestar with PydanticPlugin(prefer_alias=True) so responses use source wire names.\n          \"\"\""}

      %C

          return %T(
              path="/",
              route_handlers=[
      %C
              ],
          )
      """.trimIndent(),
      routerFactoryName,
      serviceName,
      PythonSymbol("litestar", "Router"),
      pythonServiceBaseName,
      handlers,
      PythonSymbol("litestar", "Router"),
      renderRouteHandlerList(),
    )
  }

  private fun GeneratedService.renderRouteHandlerList(): PythonCodeBlock =
    PythonCodeBlock.join(
      operations.map { operation ->
        PythonCodeBlock.of("            %L,", operation.id.pythonIdentifierName)
      },
    )

  private fun GeneratedOperation.renderProtocolMethod(): PythonCodeBlock =
    if (streaming == null) {
      if (hasServiceParameters()) {
        PythonCodeBlock.of(
          """
          |    async def %L(
          |        self,
          |%C
          |    ) -> %C: ...
          """.trimMargin(),
          id.pythonIdentifierName,
          renderServiceParameterLines(),
          returnType().renderServerPythonType(),
        )
      } else {
        PythonCodeBlock.of(
          "    async def %L(self) -> %C: ...",
          id.pythonIdentifierName,
          returnType().renderServerPythonType(),
        )
      }
    } else {
      if (hasServiceParameters()) {
        PythonCodeBlock.of(
          """
          |    def %L(
          |        self,
          |%C
          |    ) -> %T[%C]: ...
          """.trimMargin(),
          id.pythonIdentifierName,
          renderServiceParameterLines(),
          PythonSymbol("collections.abc", "AsyncIterator"),
          returnType().renderServerPythonType(),
        )
      } else {
        PythonCodeBlock.of(
          "    def %L(self) -> %T[%C]: ...",
          id.pythonIdentifierName,
          PythonSymbol("collections.abc", "AsyncIterator"),
          returnType().renderServerPythonType(),
        )
      }
    }

  private fun GeneratedOperation.renderRouteHandler(): PythonCodeBlock =
    if (streaming == null) {
      if (hasHandlerParameters()) {
        if (returnType().isNil()) {
          PythonCodeBlock.of(
            """
            |    @%T(%S)
            |    async def %L(
            |%C
            |    ) -> None:
            |        await service.%L(%C)
            """.trimMargin(),
            method.routeDecorator(),
            litestarPath(),
            id.pythonIdentifierName,
            renderHandlerParameters(),
            id.pythonIdentifierName,
            renderServiceArguments(),
          )
        } else {
          PythonCodeBlock.of(
            """
            |    @%T(%S)
            |    async def %L(
            |%C
            |    ) -> %C:
            |        return await service.%L(%C)
            """.trimMargin(),
            method.routeDecorator(),
            litestarPath(),
            id.pythonIdentifierName,
            renderHandlerParameters(),
            returnType().renderServerPythonType(),
            id.pythonIdentifierName,
            renderServiceArguments(),
          )
        }
      } else {
        if (returnType().isNil()) {
          PythonCodeBlock.of(
            """
            |    @%T(%S)
            |    async def %L() -> None:
            |        await service.%L()
            """.trimMargin(),
            method.routeDecorator(),
            litestarPath(),
            id.pythonIdentifierName,
            id.pythonIdentifierName,
          )
        } else {
          PythonCodeBlock.of(
            """
            |    @%T(%S)
            |    async def %L() -> %C:
            |        return await service.%L()
            """.trimMargin(),
            method.routeDecorator(),
            litestarPath(),
            id.pythonIdentifierName,
            returnType().renderServerPythonType(),
            id.pythonIdentifierName,
          )
        }
      }
    } else {
      if (hasHandlerParameters()) {
        PythonCodeBlock.of(
          """
          |    @%T(%S)
          |    async def %L(
          |%C
          |    ) -> %T:
          |        return %T(_server_sent_events(service.%L(%C)))
          """.trimMargin(),
          method.routeDecorator(),
          litestarPath(),
          id.pythonIdentifierName,
          renderHandlerParameters(),
          PythonSymbol("litestar.response", "ServerSentEvent"),
          PythonSymbol("litestar.response", "ServerSentEvent"),
          id.pythonIdentifierName,
          renderServiceArguments(),
        )
      } else {
        PythonCodeBlock.of(
          """
          |    @%T(%S)
          |    async def %L() -> %T:
          |        return %T(_server_sent_events(service.%L()))
          """.trimMargin(),
          method.routeDecorator(),
          litestarPath(),
          id.pythonIdentifierName,
          PythonSymbol("litestar.response", "ServerSentEvent"),
          PythonSymbol("litestar.response", "ServerSentEvent"),
          id.pythonIdentifierName,
        )
      }
    }

  private fun GeneratedOperation.hasServiceParameters(): Boolean =
    routeParameters().isNotEmpty() ||
      requestBody != null ||
      queryParameters().isNotEmpty() ||
      headerParameters().isNotEmpty()

  private fun GeneratedOperation.hasHandlerParameters(): Boolean = hasServiceParameters()

  private fun GeneratedOperation.renderServiceParameterLines(): PythonCodeBlock =
    PythonCodeBlock.join(
      requiredServiceParameters() + optionalServiceParameters(),
      separator = "\n",
    )

  private fun GeneratedOperation.renderHandlerParameters(): PythonCodeBlock =
    PythonCodeBlock.join(
      routeParameters().map { parameter -> parameter.renderPathHandlerParameter() } +
        listOfNotNull(requestBody?.renderBodyHandlerParameter()) +
        queryParameters().map { parameter -> parameter.renderQueryHandlerParameter() } +
        headerParameters().map { parameter -> parameter.renderHeaderHandlerParameter() },
      separator = "\n",
    )

  private fun GeneratedOperation.renderServiceArguments(): PythonCodeBlock {
    val arguments =
      routeParameters().map { parameter -> parameter.name.pythonIdentifierName } +
        listOfNotNull(requestBody?.let { "data" }) +
        queryParameters().map { parameter -> parameter.name.pythonIdentifierName } +
        headerParameters().map { parameter -> parameter.name.pythonIdentifierName }

    return PythonCodeBlock.of("%L", arguments.joinToString(", "))
  }

  private fun GeneratedOperation.requiredServiceParameters(): List<PythonCodeBlock> =
    routeParameters().map { parameter ->
      PythonCodeBlock.of(
        "        %L: %C,",
        parameter.name.pythonIdentifierName,
        parameter.type.renderServerPythonType(nullable = false),
      )
    } + listOfNotNull(requestBody?.renderServiceBodyParameter())

  private fun GeneratedOperation.optionalServiceParameters(): List<PythonCodeBlock> =
    (queryParameters() + headerParameters()).map { parameter ->
      PythonCodeBlock.of(
        "        %L: %C = %C,",
        parameter.name.pythonIdentifierName,
        parameter.type.renderOptionalParameterType(),
        parameter.renderDefaultValue(),
      )
    }

  private fun GeneratedPayload.renderServiceBodyParameter(): PythonCodeBlock =
    PythonCodeBlock.of("        body: %C,", type.renderServerPythonType(nullable = false))

  private fun GeneratedPayload.renderBodyHandlerParameter(): PythonCodeBlock =
    PythonCodeBlock.of("        data: %C,", type.renderServerPythonType(nullable = false))

  private fun GeneratedParameter.renderPathHandlerParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %T[%C],",
      name.pythonIdentifierName,
      PythonSymbol("litestar.params", "FromPath"),
      type.renderServerPythonType(nullable = false),
    )

  private fun GeneratedParameter.renderQueryHandlerParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %T[%C, %T(name=%S)] = %C,",
      name.pythonIdentifierName,
      PythonSymbol("typing", "Annotated"),
      type.renderOptionalParameterType(),
      PythonSymbol("litestar.params", "QueryParameter"),
      wireName(),
      renderDefaultValue(),
    )

  private fun GeneratedParameter.renderHeaderHandlerParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "        %L: %T[%C, %T(name=%S)] = %C,",
      name.pythonIdentifierName,
      PythonSymbol("typing", "Annotated"),
      type.renderOptionalParameterType(),
      PythonSymbol("litestar.params", "HeaderParameter"),
      wireName(),
      renderDefaultValue(),
    )

  private fun GeneratedOperation.routeParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.PATH }

  private fun GeneratedOperation.queryParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.QUERY }

  private fun GeneratedOperation.headerParameters(): List<GeneratedParameter> =
    parameters.filter { parameter -> parameter.location == GeneratedParameter.Location.HEADER }

  private fun GeneratedOperation.returnType(): GeneratedTypeRef =
    successResponse()?.type ?: GeneratedTypeRef.scalar("nil")

  private fun GeneratedOperation.successResponse(): GeneratedResponse? =
    responses.firstOrNull { response -> response.status in 200..299 && response.type != null }
      ?: responses.firstOrNull { response -> streaming != null && response.type != null }

  private fun GeneratedTypeRef.isNil(): Boolean = kind == GeneratedTypeRef.Kind.SCALAR && name == "nil"

  private fun GeneratedOperation.litestarPath(): String {
    var result = path
    routeParameters().forEach { parameter ->
      result =
        result.replace(
          "{${parameter.wireName()}}",
          "{${parameter.name.pythonIdentifierName}:${parameter.type.litestarPathType()}}",
        )
    }
    return result
  }

  private fun GeneratedParameter.wireName(): String = serializationName ?: name

  private fun GeneratedTypeRef.renderOptionalParameterType(): PythonCodeBlock =
    if (nullable) {
      renderServerPythonType()
    } else {
      PythonCodeBlock.of("%C | None", renderServerPythonType(nullable = false))
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

  private fun String.routeDecorator(): PythonSymbol =
    when (uppercase()) {
      "DELETE" -> PythonSymbol("litestar", "delete")
      "PATCH" -> PythonSymbol("litestar", "patch")
      "POST" -> PythonSymbol("litestar", "post")
      "PUT" -> PythonSymbol("litestar", "put")
      else -> PythonSymbol("litestar", "get")
    }

  private fun GeneratedTypeRef.litestarPathType(): String =
    when (kind) {
      GeneratedTypeRef.Kind.SCALAR ->
        when (format?.lowercase()?.ifBlank { null } ?: name.lowercase()) {
          "date" -> "date"
          "date-time", "datetime", "date-time-only", "datetime-only" -> "datetime"
          "number" -> "float"
          "integer" -> "int"
          "time", "time-only" -> "time"
          "uuid" -> "uuid"
          else -> "str"
        }
      else -> "str"
    }

  private fun GeneratedTypeRef.renderServerPythonType(nullable: Boolean = true): PythonCodeBlock {
    val type =
      when (kind) {
        GeneratedTypeRef.Kind.NAMED -> PythonCodeBlock.of("%T", PythonSymbol(".models", name.pythonTypeName))
        GeneratedTypeRef.Kind.ARRAY ->
          PythonCodeBlock.of(
            "list[%C]",
            arguments.firstOrNull()?.renderServerPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
          )
        GeneratedTypeRef.Kind.MAP ->
          PythonCodeBlock.of(
            "dict[str, %C]",
            arguments.firstOrNull()?.renderServerPythonType(nullable = false) ?: PythonCodeBlock.of("object"),
          )
        GeneratedTypeRef.Kind.UNION ->
          if (arguments.isEmpty()) {
            PythonCodeBlock.of("object")
          } else {
            PythonCodeBlock.join(
              arguments.map { type ->
                type.renderServerPythonType(nullable = false)
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
}
