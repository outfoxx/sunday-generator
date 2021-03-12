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

@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.typescript.tools

import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import java.nio.file.Files

fun compileTypes(compiler: TypeScriptCompiler, types: Map<TypeName.Standard, ModuleSpec>): Boolean {
  try {
    types.forEach { (typeName, moduleSpec) ->

      val imported = typeName.base as SymbolSpec.Imported
      val moduleName = imported.source.replaceFirst("!", "")
      val modulePath = compiler.srcDir.resolve(moduleName).normalize()

      Files.createDirectories(modulePath.parent)

      FileSpec.get(moduleSpec, moduleName)
        .writeTo(compiler.srcDir)
    }

    return compiler.compile() == 0
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}
