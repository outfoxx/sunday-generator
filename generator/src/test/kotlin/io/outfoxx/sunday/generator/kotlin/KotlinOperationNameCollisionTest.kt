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
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI
import java.nio.file.Path

@KotlinTest
class KotlinOperationNameCollisionTest {
  @ParameterizedTest
  @CsvSource(
    "raml,true",
    "raml,false",
    "openapi,true",
    "openapi,false",
    "asyncapi,true",
    "asyncapi,false",
    "asyncapi3,true",
    "asyncapi3,false",
    "composed,true",
    "composed,false",
  )
  fun `operation names can match imported runtime extensions`(
    frontend: String,
    generateClientConfig: Boolean,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operation =
      source.services.first().operations.first().copy(
        id = "operation",
        method = "GET",
        path = "/operation",
        streaming = null,
        responses = listOf(GeneratedResponse(status = 204)),
      )
    val api =
      source.copy(
        services =
          listOf(
            GeneratedService(
              "CollisionService",
              operations =
                listOf(
                  operation,
                  operation.copy(id = "withSecurity", path = "/with-security"),
                  operation.copy(
                    id = "nullableOperation",
                    path = "/nullable-operation",
                    nullify = GeneratedNullify(statuses = listOf(404)),
                  ),
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
        generateClientConfig = generateClientConfig,
      ),
    ).generateServiceTypes()
    val consumer =
      SourceFile.kotlin(
        "CollisionCheck.kt",
        """
        package example

        import io.outfoxx.sunday.*
        import io.outfoxx.sunday.jdk.JdkTransport
        import io.outfoxx.sunday.problems.SundayHttpProblem
        import kotlinx.io.Source
        import java.net.http.HttpRequest
        import java.net.http.HttpResponse

        fun verifyCollision() {
          JdkTransport(URITemplate("https://example.com"), SundayHttpProblem.Factory).use { transport ->
            val client = CollisionAPI(transport)
            val ordinary: Operation<Unit, Unit, HttpRequest, HttpResponse<Source>> = client.operation()
            val nullable: NullableOperation<Unit, Unit, HttpRequest, HttpResponse<Source>> = client.nullableOperation()
            check(client.withSecurity().spec.pathTemplate == "/with-security")
            check(ordinary.spec.pathTemplate == "/operation")
            check(nullable.spec.pathTemplate == "/nullable-operation")
            check(nullable.nullify.statuses == listOf(404))
          }
        }
        """.trimIndent(),
      )
    val result = compileTypesResult(registry.buildTypes(), listOf(consumer))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    result.classLoader
      .loadClass("example.CollisionCheckKt")
      .getMethod("verifyCollision")
      .invoke(null)
  }

  @Test
  fun `broker operation name can match the delivery decoding extension`(
    @ResourceUri("asyncapi/ir/amqp-broker.yaml") sourceUri: URI,
  ) {
    val source = AsyncApiToGeneratedApi().convertFragment(sourceUri).api
    val api =
      source.copy(
        services =
          source.services.map { service ->
            service.copy(
              operations =
                service.operations.map { operation ->
                  if (operation.method == "SUBSCRIBE") operation.copy(id = "decodeDeliveries") else operation
                },
            )
          },
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
      ),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    result.classLoader.loadClass("example.EventsBroker").getMethod("decodeDeliveries")
  }
}
