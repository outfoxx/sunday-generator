@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.typescript.tools

import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import java.nio.file.Files

fun compileTypes(types: Map<TypeName.Standard, ModuleSpec>): Boolean {

  val workDir = Files.createTempDirectory(null)
  try {
    val compiler = TypeScriptCompiler(workDir)

    types.forEach { (typeName, moduleSpec) ->

      val imported = typeName.base as SymbolSpec.Imported
      val moduleName = imported.source.replaceFirst("!", "")
      val modulePath = compiler.srcDir.resolve(moduleName).normalize()

      Files.createDirectories(modulePath.parent)

      FileSpec.get(moduleSpec, moduleName)
        .writeTo(compiler.srcDir)
    }

    val result = compiler.compile()

    return result == 0

  } finally {
    workDir.toFile().deleteRecursively()
  }
}
