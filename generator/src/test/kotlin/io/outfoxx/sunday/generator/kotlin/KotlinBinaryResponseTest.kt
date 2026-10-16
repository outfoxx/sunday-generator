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
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.binaryResponseApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinBinaryResponseTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml"])
  fun `binary responses and unions preserve download bytes`(frontend: String) {
    val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      binaryResponseApi(frontend),
      registry,
      KotlinSundayOptions("example", "https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val result =
      compileTypesResult(
        registry.buildTypes(),
        listOf(
          SourceFile.kotlin(
            "Check.kt",
            java.nio.file.Files
              .readString(Path.of("src/test/resources/binary-responses/Check.kt")),
          ),
        ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    result.classLoader
      .loadClass("example.BinaryCheck")
      .getMethod("verify")
      .invoke(null)
  }
}
