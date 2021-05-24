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

package io.outfoxx.sunday.generator.typescript.tools

import io.outfoxx.sunday.test.utils.Compilation
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import java.nio.file.Files

fun compileTypes(compiler: TypeScriptCompiler, types: Map<TypeName.Standard, ModuleSpec>): Boolean {
  try {
    val fileSpecs =
      types.map { (typeName, moduleSpec) ->

        val imported = typeName.base as SymbolSpec.Imported
        val modulePath = imported.source.replaceFirst("!", "")

        FileSpec.get(moduleSpec, modulePath)
      }

    fileSpecs.forEach {

      val modulePath = compiler.srcDir.resolve(it.modulePath).normalize()

      Files.createDirectories(modulePath.parent)

      it.writeTo(compiler.srcDir)
    }

    val (result, output) = compiler.compile()

    if (result != 0) {

      val files =
        fileSpecs.associate {
          val builder = StringBuilder()
          it.writeTo(builder)
          "${it.modulePath}.ts" to builder.toString()
        }

      Compilation.printFailure(files, output)
    }

    return result == 0
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}
