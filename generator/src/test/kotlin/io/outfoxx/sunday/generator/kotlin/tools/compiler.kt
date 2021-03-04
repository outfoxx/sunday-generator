package io.outfoxx.sunday.generator.kotlin.tools

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile

fun compileTypes(types: Map<ClassName, TypeSpec>) =
  KotlinCompilation()
    .apply {
      sources = types.entries
        .filter { it.key.topLevelClassName() == it.key }
        .map { FileSpec.get(it.key.packageName, it.value) }
        .map {
          val fileName = "${it.packageName.replace('.', '_')}_${it.name}.kt"
          SourceFile.kotlin(fileName, it.toString())
        }
      kotlincArguments = listOf("-jvm-target", "11")
      inheritClassPath = true
      verbose = false
      allWarningsAsErrors = true
      reportOutputFiles = true
    }
    .compile()
