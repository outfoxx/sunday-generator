/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI

/** Shared compiler-backed fixtures for the IR feature tests. */
abstract class TypeScriptSundayIrTestSupport {
  protected fun assertIrServiceSnapshot(
    compiler: TypeScriptCompiler,
    testUri: URI,
    snapshotPath: String,
    importStyle: TypeScriptTypeRegistry.ImportStyle = TypeScriptTypeRegistry.ImportStyle.ESM,
    options: TypeScriptSundayOptions = typeScriptSundayTestOptions,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf(), importStyle)
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findTypeMod("API@!api${importStyle.importExtension}", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "api${importStyle.importExtension}")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot(snapshotPath, output)
  }

  protected val aggregateServiceOptions =
    TypeScriptSundayOptions(
      "http://example.com/",
      listOf("application/json"),
      "API",
      true,
      "TurnPostAPI",
    )
}
