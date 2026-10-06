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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinClientConfigurationTest {
  @ParameterizedTest
  @ValueSource(
    strings = ["raml", "openapi", "asyncapi", "composed", "security", "multi", "alternatives", "server-security"],
  )
  fun `configuration factory compiles with generic transport`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi(frontend, directory)
    val authenticated = frontend in setOf("security", "alternatives", "server-security")
    val configType =
      if (frontend in
        setOf("multi", "server-security")
      ) {
        "ExampleAPIProductionConfig"
      } else {
        "ExampleAPIConfig"
      }
    val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions("example", "https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val consumer =
      TypeSpec
        .objectBuilder("ConstructionCheck")
        .addFunction(
          FunSpec
            .builder("verify")
            .addAnnotation(JvmStatic::class)
            .addCode(
              """
              var calls = 0
              val config = $configType(tenant = "secondary")
              val transport = io.outfoxx.sunday.jdk.JdkTransport(io.outfoxx.sunday.URITemplate(config.baseURL().toString()), io.outfoxx.sunday.problems.SundayHttpProblem.Factory)
              try {
                val client: API<io.outfoxx.sunday.jdk.JdkRequest> = createAPI(config, { settings ->
                  calls++
                  check((settings.tokenManager != null) == ${authenticated
              })
                  check(settings.baseURL.toString() == "https://secondary.example/v1")
                  transport
                }${if (frontend == "alternatives") {
                ", credentials = APICredentials(identity = io.outfoxx.sunday.security.BearerCredentials(\"secret\"), accessKey = io.outfoxx.sunday.security.ApiKeyCredentials(\"key\")), securitySelection = mapOf(\"listItems\" to APISecurityAlternative.AccessKeyAndIdentity)"
              } else if (frontend in setOf("security", "server-security")) {
                ", credentials = APICredentials(identity = io.outfoxx.sunday.security.BearerCredentials(\"secret\"))"
              } else {
                ""
              }})
                check(calls == 1)
                check(client.transport === transport)
                val direct = API(transport)
                check(direct.transport === transport)
              } finally {
                transport.close()
              }
              """.trimIndent(),
            ).build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (ClassName("example", "ConstructionCheck") to consumer))
    assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK)
    result.classLoader
      .loadClass("example.ConstructionCheck")
      .getMethod("verify")
      .invoke(null)
  }
}
