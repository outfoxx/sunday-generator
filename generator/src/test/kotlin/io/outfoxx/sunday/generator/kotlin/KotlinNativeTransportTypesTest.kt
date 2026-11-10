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

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedExchange
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinNativeTransportTypesTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native types survive service operations exports and event sources`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operation =
      source.services.first().operations.first().copy(
        id = "fetch",
        method = "GET",
        path = "/items",
        streaming = null,
        responses = listOf(GeneratedResponse(status = 204)),
      )
    val api =
      source.copy(
        services =
          listOf(
            GeneratedService(
              "TypedService",
              operations =
                listOf(
                  operation,
                  operation.copy(id = "request", exchange = GeneratedExchange.REQUEST),
                  operation.copy(id = "response", exchange = GeneratedExchange.RESPONSE),
                  operation.copy(id = "optional", nullify = GeneratedNullify(statuses = listOf(404))),
                  operation.copy(id = "events", streaming = GeneratedStreaming(GeneratedStreaming.Kind.EVENT_SOURCE)),
                ),
            ),
          ),
      )
    val registry = KotlinTypeRegistry(null, GenerationMode.Client, emptySet(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions(
        kotlinPlacement("example", "example"),
        "https://example.com/problems/",
        listOf("application/json"),
        "API",
        profile = "external",
      ),
    ).generateServiceTypes()
    val consumer =
      SourceFile.kotlin(
        "NativeTypes.kt",
        """
        package example

        import io.outfoxx.sunday.*
        import io.outfoxx.sunday.http.*
        import io.outfoxx.sunday.jdk.JdkTransport
        import io.outfoxx.sunday.okhttp.OkHttpTransport
        import io.outfoxx.sunday.security.AuthenticationRecoveryBudget
        import kotlinx.coroutines.flow.Flow
        import kotlinx.io.Source
        import java.net.http.HttpRequest
        import java.net.http.HttpResponse

        fun jdk(transport: JdkTransport): TypedAPI<HttpRequest, HttpResponse<Source>> = TypedAPI(transport)
        fun okhttp(transport: OkHttpTransport): TypedAPI<okhttp3.Request, okhttp3.Response> = TypedAPI(transport)

        suspend fun <TReq, TRes> checkTypes(client: TypedAPI<TReq, TRes>) {
          val operation: Operation<Unit, Unit, TReq, TRes> = client.fetch()
          val optional: NullableOperation<Unit, Unit, TReq, TRes> = client.optional()
          val exported: Request<TReq> = client.request()
          val nativeRequest: TReq = exported.transport
          val response: Response<TRes> = client.response()
          val nativeResponse: TRes = response.transport
          val decoded: OperationResponse<Unit, TRes> = operation.response()
          val optionalResponse: OperationResponse<Unit, TRes> = optional.response()
          val events: EventSource<TReq, TRes> = client.events()
          val stream: Flow<ResponseEvent<TRes>> = client.transport.start(exported, AuthenticationRecoveryBudget())
          consume(nativeRequest, nativeResponse, decoded, optionalResponse, events, stream)
        }

        fun consume(vararg values: Any?) { check(values.isNotEmpty()) }
        """.trimIndent(),
      )
    val result = compileTypesResult(registry.buildTypes(), listOf(consumer))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }
}
