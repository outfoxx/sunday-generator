@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.swift.tools

import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.TypeSpec

fun compileTypes(compiler: SwiftCompiler, types: Map<DeclaredTypeName, TypeSpec>): Boolean {
  try {
    types.forEach { (typeName, typeSpec) ->
      FileSpec.get(typeName.moduleName, typeSpec)
        .writeTo(compiler.srcDir)
    }

    return compiler.compile() == 0
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}
