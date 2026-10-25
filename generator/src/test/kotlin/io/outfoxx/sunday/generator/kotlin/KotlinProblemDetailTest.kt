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

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.problemDetailApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("responses")
class KotlinProblemDetailTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `problem details are optional defaults and can be overridden`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = problemDetailApi(frontend, directory)
    for (target in listOf("sunday", "jaxrs")) {
      for (library in KotlinProblemLibrary.entries) {
        if (target == "sunday" && library == KotlinProblemLibrary.QUARKUS) continue
        val registry =
          KotlinTypeRegistry(null, GenerationMode.Client, setOf(KotlinTypeRegistry.Option.JacksonAnnotations), library)
        if (target == "sunday") {
          KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
        } else {
          KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
        }
        val compiled =
          compileTypesResult(
            registry.buildTypes(),
            listOf(
              SourceFile.kotlin(
                "Check.kt",
                """
                import io.test.WithDefaultProblem
                import io.test.WithoutDefaultProblem
                fun verify() {
                  check(WithDefaultProblem().detail == "Default detail")
                  check(WithoutDefaultProblem().detail == null)
                  check(WithDefaultProblem(detail = "Override").detail == "Override")
                  check(WithoutDefaultProblem(detail = "Override").detail == "Override")
                  check(WithDefaultProblem(detail = "").detail == "")
                  check(WithDefaultProblem(detail = null).detail == null)
                }
                """.trimIndent(),
              ),
            ),
          )
        assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
        compiled.classLoader
          .loadClass("CheckKt")
          .getMethod("verify")
          .invoke(null)
      }
    }
  }
}
