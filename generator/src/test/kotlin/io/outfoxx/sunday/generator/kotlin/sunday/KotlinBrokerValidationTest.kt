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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@KotlinTest
@Tag("events")
@Tag("validation")
@OptIn(ExperimentalCompilerApi::class)
class KotlinBrokerValidationTest {
  @ParameterizedTest
  @ValueSource(strings = ["javax", "jakarta", "none"])
  fun `publication validates outbound values while consumption preserves unknown values`(
    namespace: String,
    @ResourceUri("asyncapi/ir/amqp-tolerant-enum.yaml") uri: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          if (namespace != "none") add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(AsyncApiToGeneratedApi().convertFragment(uri).api, registry, kotlinSundayTestOptions)
      .generateServiceTypes()
    val result =
      compileTypesResult(
        registry.buildTypes(),
        listOf(
          SourceFile.kotlin(
            "BrokerValidationCheck.kt",
            Files.readString(Path.of("src/test/resources/kotlin/broker/BrokerValidationCheck.kt")),
          ),
        ),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(if (namespace == "none") "javax" else namespace, result.classLoader) {
      result.classLoader
        .loadClass("io.test.BrokerValidationCheck")
        .getMethod("verify", String::class.java)
        .invoke(null, namespace)
    }
  }
}
