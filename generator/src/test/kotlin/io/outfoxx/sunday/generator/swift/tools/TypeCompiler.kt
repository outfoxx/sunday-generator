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

package io.outfoxx.sunday.generator.swift.tools

import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.utils.Compilation
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.TypeSpec
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path

fun compileTypes(
  compiler: SwiftCompiler,
  types: Map<DeclaredTypeName, TypeSpec>,
): Boolean =
  compiler.synchronizeCompilation {
    compileTypesUnsafe(compiler, types)
  }

private fun compileTypesUnsafe(
  compiler: SwiftCompiler,
  types: Map<DeclaredTypeName, TypeSpec>,
): Boolean {
  try {
    CompiledGeneratedSources.beginCompile()
    val fileSpecs =
      types.map { (typeName, typeSpec) ->
        FileSpec.get(typeName.moduleName, typeSpec)
      }

    fileSpecs.forEach { it.writeTo(compiler.srcDir) }

    val (result, output) = compiler.compile()

    val generatedWarnings = output.containsGeneratedWarnings(compiler.srcDir)

    if (result != 0 || generatedWarnings) {

      val files =
        fileSpecs.associate {
          val builder = StringBuilder()
          it.writeTo(builder)
          "${it.name}.swift" to builder.toString()
        }

      Compilation.printFailure(files, output)
      throw AssertionFailedError("Swift compilation failed:\n$output")
    }

    if (result == 0 && !generatedWarnings) {
      fileSpecs.forEach { fileSpec ->
        val builder = StringBuilder()
        fileSpec.writeTo(builder)
        CompiledGeneratedSources.record(GeneratedCodeLanguage.Swift, "${fileSpec.name}.swift", builder.toString())
      }
    }

    return result == 0 && !generatedWarnings
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}

fun compileGeneratedFiles(compiler: SwiftCompiler): Boolean =
  compiler.synchronizeCompilation {
    compileGeneratedFilesUnsafe(compiler)
  }

private fun compileGeneratedFilesUnsafe(compiler: SwiftCompiler): Boolean {
  try {
    CompiledGeneratedSources.beginCompile()
    val (result, output) = compiler.compile()

    val generatedWarnings = output.containsGeneratedWarnings(compiler.srcDir)

    if (result != 0 || generatedWarnings) {
      val files =
        Files
          .walk(compiler.srcDir)
          .use { paths ->
            paths
              .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".swift") }
              .toList()
          }.associate { path ->
            compiler.srcDir.relativize(path).toString() to Files.readString(path)
          }

      Compilation.printFailure(files, output)
      throw AssertionFailedError("Swift compilation failed:\n$output")
    }

    if (result == 0 && !generatedWarnings) {
      val files =
        Files
          .walk(compiler.srcDir)
          .use { paths ->
            paths
              .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".swift") }
              .toList()
          }.associateWith { path -> Files.readString(path) }
      CompiledGeneratedSources.recordAll(GeneratedCodeLanguage.Swift, compiler.srcDir, files)
    }

    return result == 0 && !generatedWarnings
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}

private fun String.containsGeneratedWarnings(srcDir: Path): Boolean {
  val localSrcDir = Regex.escape(srcDir.toAbsolutePath().normalize().toString())
  val dockerSrcDir = Regex.escape("/work/src")
  return lineSequence()
    .filter { line ->
      Regex("""^(?:$localSrcDir|$dockerSrcDir|.*[/\\]src[/\\]).*:\d+:\d+: warning:""")
        .containsMatchIn(line)
    }.any { line -> !line.isAllowedGeneratedWarning() }
}

private fun String.isAllowedGeneratedWarning(): Boolean =
  // Published Sunday versions used by the compile fixture do not yet declare Problem's unchecked
  // Sendable boundary. Generated problem hierarchies restate the conformance over immutable
  // stored fields; remove this allowlist after the fixture consumes the updated Sunday release.
  contains("warning: non-final class '") &&
    contains("cannot conform to the 'Sendable' protocol")
