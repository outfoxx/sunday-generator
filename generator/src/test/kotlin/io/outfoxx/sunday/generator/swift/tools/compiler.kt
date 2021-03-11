@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.swift.tools

import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.TypeSpec
import java.nio.file.Files

fun compileTypes(types: Map<DeclaredTypeName, TypeSpec>): Boolean {

  val workDir = Files.createTempDirectory(null)
  try {
    val compiler = SwiftCompiler(workDir)

    types.forEach { (typeName, typeSpec) ->

      FileSpec.get(typeName.moduleName, typeSpec)
        .writeTo(compiler.srcDir)
    }

    val result = compiler.compile()

    return result == 0

  } finally {
    workDir.toFile().deleteRecursively()
  }
}
