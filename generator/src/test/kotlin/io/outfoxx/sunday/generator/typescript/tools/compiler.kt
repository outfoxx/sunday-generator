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
