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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import com.sun.net.httpserver.HttpServer
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.security.SecurityBinding
import io.outfoxx.sunday.security.TokenConfiguration
import io.outfoxx.sunday.security.TokenManager
import io.outfoxx.sunday.security.TokenProvider
import io.outfoxx.sunday.security.TokenRequest
import io.outfoxx.sunday.security.TokenSet
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinScopedSecurityTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `compiled clients authenticate HTTP operations and event connections`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operations =
      source.services.flatMap { service ->
        service.operations.map { it.copy(auth = source.effectiveAuth(service, it)) }
      }
    val api = source.copy(services = listOf(GeneratedService("SecurityService", operations = operations)))
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        emptySet(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions(
        "io.test",
        "https://api.example/problems/",
        listOf("application/json"),
        "API",
        profile = "external",
      ),
    ).generateServiceTypes()
    val checkName = ClassName("io.test", "SecurityCheck")
    val check =
      TypeSpec
        .classBuilder(checkName)
        .addFunction(
          FunSpec
            .builder("run")
            .addParameter("base", String::class)
            .addParameter("manager", TokenManager::class)
            .addCode("%M {\n", MemberName("kotlinx.coroutines", "runBlocking"))
            .addCode(
              "  %T(%T(base), %T.Factory, tokenManager = manager).use { transport ->\n",
              ClassName("io.outfoxx.sunday.jdk", "JdkTransport"),
              ClassName("io.outfoxx.sunday", "URITemplate"),
              ClassName("io.outfoxx.sunday.problems", "SundayHttpProblem"),
            ).addCode("    val client = %T(transport)\n", ClassName("io.test", "SecurityAPI"))
            .apply {
              operations.forEach { operation ->
                if (operation.streaming != null) {
                  addCode(
                    "    client.%L().%M(1).%M()\n",
                    operation.id,
                    MemberName("kotlinx.coroutines.flow", "take"),
                    MemberName("kotlinx.coroutines.flow", "toList"),
                  )
                } else {
                  addCode("    client.%L().execute()\n", operation.id)
                }
              }
            }.addCode("  }\n}\n")
            .build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (checkName to check))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val acquired = CopyOnWriteArrayList<TokenRequest>()
    val headers = CopyOnWriteArrayList<String>()
    val provider =
      object : TokenProvider {
        override val identity = "application"

        override fun configure(binding: SecurityBinding) = TokenConfiguration("public-client", "fresh-session")

        override suspend fun acquire(request: TokenRequest): TokenSet {
          acquired += request
          return TokenSet("external-token")
        }
      }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      exchange.use {
        headers += exchange.requestHeaders.getFirst("Authorization") ?: ""
        if (exchange.requestURI.path == "/events") {
          exchange.responseHeaders.add("Content-Type", "text/event-stream")
          val body = "data: \"value\"\n\n".toByteArray()
          exchange.sendResponseHeaders(200, body.size.toLong())
          exchange.responseBody.write(body)
        } else {
          exchange.sendResponseHeaders(204, -1)
        }
      }
    }
    server.start()
    try {
      TokenManager(mapOf("application" to provider)).use { manager ->
        val type = result.classLoader.loadClass("io.test.SecurityCheck")
        type
          .getMethod("run", String::class.java, TokenManager::class.java)
          .invoke(type.getConstructor().newInstance(), "http://127.0.0.1:${server.address.port}", manager)
      }
    } finally {
      server.stop(0)
    }
    assertEquals(
      List(operations.size) { "bearer external-token" },
      headers.map {
        it.substringBefore(' ').lowercase() +
          " " +
          it.substringAfter(' ')
      },
    )
    val binding = acquired.single().binding
    assertEquals("application", binding.provider)
    assertEquals("external", binding.profile)
    assertEquals(SecurityBinding.Flow.AuthorizationCode, binding.flow)
    assertEquals(setOf("items:read"), binding.scopes)
    assertEquals("https://identity.example/token", binding.endpoints.tokenUrl)
    assertEquals("https://identity.example/authorize", binding.endpoints.authorizationUrl)
  }
}
